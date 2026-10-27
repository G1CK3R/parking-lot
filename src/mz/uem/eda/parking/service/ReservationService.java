package mz.uem.eda.parking.service;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Reservas de lugar (RF10, RF11, RF12). Cada reserva guarda um lugar livre
 * durante uma tolerância constante; se o carro chegar a tempo, faz check-in
 * e entra pelo {@link EntryService}; se não, a reserva expira e o lugar
 * volta a ficar livre para todos.
 *
 * <p><b>A expiração é uma fila FIFO.</b> Como a tolerância é a mesma para
 * todas, a ordem de criação é a ordem de expiração: a reserva da frente é
 * sempre a próxima a expirar. Expirar é olhar para a frente e desenfileirar
 * enquanto a hora já passou; nunca é preciso procurar no meio.
 *
 * <p>O check-in e o cancelamento acontecem por qualquer ordem, por isso não
 * se tira a reserva do meio da fila: marca-se como {@code CHECKED_IN} ou
 * {@code CANCELLED} e ela sai quando chegar à frente. Uma cancelada só sai
 * quando passa a sua hora de expiração, para o cancelamento se poder
 * desfazer até lá ({@link #reactivate}); como as de trás expiram depois
 * dela, esperar por ela não atrasa nenhuma expiração. Um contador guarda quantas estão activas, para não percorrer a
 * fila a cada consulta.
 *
 * <p><b>Lugares reservados contam como ocupados</b> para quem chega sem
 * reserva (RF11). Antes de uma entrada sem reserva, a interface pergunta
 * {@link #availableForWalkIns}; o {@link WaitingQueueService} faz o mesmo
 * antes de chamar o próximo da fila de espera.
 */
public class ReservationService {

    private final Iterable<Lane> lanes;
    private final EntryService entryService;
    private final int toleranceMinutes;
    private final Queue<Reservation> reservations;
    private int activeCount;

    /** A última reserva reposta por {@link #load}, para verificar a ordem. */
    private Reservation lastLoaded;

    /**
     * Cria o serviço sem reservas.
     *
     * @param lanes as baias do parque, as mesmas do {@link EntryService}
     * @param entryService o serviço que faz entrar o carro no check-in
     * @param toleranceMinutes quantos minutos vale cada reserva, maior que 0
     * @throws NullPointerException se {@code lanes} ou {@code entryService}
     *         for {@code null}
     * @throws IllegalArgumentException se a tolerância não for positiva
     */
    public ReservationService(Iterable<Lane> lanes, EntryService entryService, int toleranceMinutes) {
        this.lanes = Objects.requireNonNull(lanes, "lanes não pode ser null");
        this.entryService = Objects.requireNonNull(entryService, "entryService não pode ser null");
        if (toleranceMinutes <= 0) {
            throw new IllegalArgumentException("toleranceMinutes deve ser positivo: " + toleranceMinutes);
        }
        this.toleranceMinutes = toleranceMinutes;
        this.reservations = new LinkedQueue<>();
        this.activeCount = 0;
    }

    /**
     * Cria uma reserva para esta matrícula, válida durante a tolerância.
     *
     * @param plate a matrícula
     * @param estimatedMinutes a duração estimada da estadia, em minutos
     * @param now a hora da reserva
     * @return a reserva criada, ou {@code null} se não houver lugar livre
     *         que não esteja já reservado (RF11)
     * @throws IllegalArgumentException se a duração não for positiva ou a
     *         matrícula ficar vazia
     * @throws IllegalStateException se a matrícula já estiver estacionada
     *         ou já tiver uma reserva activa
     * @complexity O(r + n): expira as reservas vencidas, procura a
     *             matrícula nas r reservas e nos n carros estacionados
     */
    public Reservation reserve(String plate, int estimatedMinutes, LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        StayClass stayClass = EntryService.classify(estimatedMinutes);
        String normalized = Vehicle.normalizePlate(plate);
        expire(now);
        if (isParked(normalized)) {
            throw new IllegalStateException("veículo já parqueado: " + normalized);
        }
        if (find(normalized) != null) {
            throw new IllegalStateException("já existe uma reserva activa: " + normalized);
        }
        if (freePlaces() - activeCount <= 0) {
            return null;
        }
        Reservation reservation = new Reservation(normalized, stayClass, estimatedMinutes, now,
                now.plusMinutes(toleranceMinutes));
        reservations.enqueue(reservation);
        activeCount++;
        return reservation;
    }

    /**
     * Faz entrar o carro que tem reserva. A baia é escolhida pela política
     * de colocação do {@link EntryService}, como em qualquer entrada.
     *
     * @param vehicle a viatura que chegou
     * @param now a hora de chegada
     * @return a sessão aberta, ou {@code null} se a matrícula não tiver
     *         reserva activa (ou se ela já tiver expirado)
     * @throws IllegalStateException se não houver lugar, o que só acontece
     *         se alguém entrou sem reserva sem consultar
     *         {@link #availableForWalkIns} (a reserva fica activa)
     * @complexity O(r + n + B)
     */
    public ParkingSession checkIn(Vehicle vehicle, LocalDateTime now) {
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");
        expire(now);
        Reservation reservation = find(vehicle.getPlate());
        if (reservation == null) {
            return null;
        }
        ParkingSession session = entryService.enter(vehicle, reservation.getEstimatedMinutes(), now);
        if (session == null) {
            throw new IllegalStateException("não há lugar para a reserva de " + vehicle.getPlate());
        }
        reservation.checkIn();
        activeCount--;
        return session;
    }

    /**
     * Cancela a reserva activa desta matrícula: o lugar deixa de estar
     * guardado. A reserva fica na fila, marcada como cancelada, e sai quando
     * chegar à frente, como as que fizeram check-in.
     *
     * @param plate a matrícula
     * @param now a hora do cancelamento, para expirar antes as vencidas
     * @return a reserva cancelada, ou {@code null} se a matrícula não tiver
     *         reserva activa (ou se ela já tiver expirado)
     * @complexity O(r)
     */
    public Reservation cancel(String plate, LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        expire(now);
        Reservation reservation = find(plate);
        if (reservation == null) {
            return null;
        }
        reservation.cancel();
        activeCount--;
        return reservation;
    }

    /**
     * Desfaz o cancelamento de uma reserva: volta a guardar o lugar, até à
     * mesma hora de expiração. Só é possível enquanto essa hora não passar e
     * se ainda houver um lugar livre sem reserva.
     *
     * @param reservation a reserva cancelada
     * @param now a hora
     * @return {@code true} se a reserva voltou a estar activa; {@code false}
     *         se já passou a hora, se a reserva já não está na fila, ou se não
     *         há lugar livre
     * @complexity O(r + B)
     */
    public boolean reactivate(Reservation reservation, LocalDateTime now) {
        Objects.requireNonNull(reservation, "reservation não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");
        expire(now);
        if (reservation.getStatus() != Reservation.Status.CANCELLED || reservation.isExpiredAt(now)
                || !contains(reservation) || find(reservation.getPlate()) != null
                || isParked(reservation.getPlate()) || freePlaces() - activeCount <= 0) {
            return false;
        }
        reservation.reactivate();
        activeCount++;
        return true;
    }

    /** Diz se esta reserva (o mesmo objecto) ainda está na fila. O(r). */
    private boolean contains(Reservation reservation) {
        for (Reservation r : reservations) {
            if (r == reservation) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marca como expiradas as reservas cuja tolerância já acabou e tira da
     * frente da fila as que já fizeram check-in (RF12). Pára na primeira
     * reserva activa ainda dentro da tolerância: todas as de trás expiram
     * depois dela.
     *
     * @param now a hora actual
     * @return quantas reservas expiraram nesta chamada
     * @complexity O(1) por reserva retirada; O(r) no pior caso, mas cada
     *             reserva só é retirada uma vez
     */
    public int expire(LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        int expired = 0;
        while (!reservations.isEmpty()) {
            Reservation front = reservations.peek();
            if (front.getStatus() == Reservation.Status.CANCELLED && !front.isExpiredAt(now)) {
                // Fica até à sua hora, para o cancelamento se poder desfazer.
                break;
            }
            if (!front.isActive()) {
                reservations.dequeue();
            } else if (front.isExpiredAt(now)) {
                front.expire();
                reservations.dequeue();
                activeCount--;
                expired++;
            } else {
                break;
            }
        }
        return expired;
    }

    /**
     * Quantos lugares ficam para quem chega sem reserva: os lugares livres
     * menos as reservas activas. Expira primeiro as reservas vencidas.
     *
     * @param now a hora actual
     * @return os lugares disponíveis, 0 ou mais
     * @complexity O(B), mais as reservas que expirarem
     */
    public int availableForWalkIns(LocalDateTime now) {
        expire(now);
        int available = freePlaces() - activeCount;
        return available > 0 ? available : 0;
    }

    /**
     * Procura a reserva activa desta matrícula.
     *
     * @param plate a matrícula
     * @return a reserva activa, ou {@code null} se não houver
     * @complexity O(r)
     */
    public Reservation find(String plate) {
        String wanted = Vehicle.normalizePlate(plate);
        for (Reservation reservation : reservations) {
            if (reservation.isActive() && reservation.getPlate().equals(wanted)) {
                return reservation;
            }
        }
        return null;
    }

    /**
     * Repõe uma reserva activa que veio da base de dados, no fim da fila. Só
     * a camada {@code persistence} chama este método, no arranque, com as
     * reservas pela ordem de criação.
     *
     * @param reservation a reserva gravada, ainda activa
     * @throws IllegalArgumentException se a reserva não estiver activa, ou
     *         se tiver sido criada antes da última reposta (a fila deixaria
     *         de estar pela ordem de expiração)
     * @throws IllegalStateException se a matrícula já tiver uma reserva
     *         activa
     * @complexity O(r)
     */
    public void load(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation não pode ser null");
        if (!reservation.isActive()) {
            throw new IllegalArgumentException("só se repõem reservas activas: " + reservation);
        }
        if (lastLoaded != null && reservation.getCreatedAt().isBefore(lastLoaded.getCreatedAt())) {
            throw new IllegalArgumentException("reservas fora da ordem de criação: " + reservation);
        }
        if (find(reservation.getPlate()) != null) {
            throw new IllegalStateException("já existe uma reserva activa: " + reservation.getPlate());
        }
        reservations.enqueue(reservation);
        activeCount++;
        lastLoaded = reservation;
    }

    /** @return quantas reservas estão activas; O(1) */
    public int activeCount() {
        return activeCount;
    }

    /** @return a tolerância de cada reserva, em minutos */
    public int getToleranceMinutes() {
        return toleranceMinutes;
    }

    /**
     * Percurso só de leitura das reservas na fila, pela ordem de expiração.
     * Pode incluir reservas que já fizeram check-in e ainda não chegaram à
     * frente: a interface mostra só as activas ({@link Reservation#isActive}).
     *
     * @return as reservas na fila
     * @complexity O(1) para obter; O(r) para percorrer
     */
    public Iterable<Reservation> reservations() {
        return reservations;
    }

    /**
     * @return os lugares livres em todas as baias de estacionamento
     * @complexity O(B)
     */
    private int freePlaces() {
        int free = 0;
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                free += lane.freeSpace();
            }
        }
        return free;
    }

    /**
     * Diz se a matrícula já está numa baia. Percorre as baias só para
     * leitura.
     *
     * @complexity O(n)
     */
    private boolean isParked(String plate) {
        for (Lane lane : lanes) {
            for (ParkingSession session : lane.contents()) {
                if (session.getVehicle().getPlate().equals(plate)) {
                    return true;
                }
            }
        }
        return false;
    }
}