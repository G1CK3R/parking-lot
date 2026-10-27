package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Uma reserva de lugar (RF10): guarda um lugar para uma matrícula até a
 * tolerância acabar. Se o carro chegar a tempo, faz check-in e a reserva dá
 * lugar a uma {@link ParkingSession}; se não chegar, expira.
 *
 * <p>A reserva não escolhe baia: só garante que, quando o carro chegar, há
 * um lugar livre no parque. A baia é escolhida no check-in, pela política de
 * colocação, como para qualquer outra entrada.
 *
 * <p>Guarda a matrícula, e não a {@link Vehicle}, porque quem reserva por
 * telefone ainda não está no parque. No check-in, o serviço compara a
 * matrícula da viatura que chega com esta.
 *
 * <p>Guarda a duração estimada em minutos, que é o que o
 * {@code EntryService} recebe no check-in, e também a classe de permanência
 * correspondente, para a interface a mostrar sem fazer contas.
 *
 * <p>Imutável, excepto o estado, que só avança uma vez (de {@code ACTIVE}
 * para {@code CHECKED_IN}, {@code EXPIRED} ou {@code CANCELLED}), e o
 * identificador da base de dados. A única excepção é desfazer um
 * cancelamento ({@link #reactivate()}), que volta de {@code CANCELLED} para
 * {@code ACTIVE}.
 */
public class Reservation {

    public enum Status {
        /** À espera do carro. */
        ACTIVE,
        /** O carro chegou e a reserva deu lugar a uma sessão. */
        CHECKED_IN,
        /** A tolerância acabou antes de o carro chegar. */
        EXPIRED,
        /** O operador cancelou a reserva antes de o carro chegar. */
        CANCELLED
    }

    private final String plate;
    private final StayClass stayClass;
    private final int estimatedMinutes;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private Status status;

    private long id;

    /**
     * Cria uma reserva activa.
     *
     * @param plate a matrícula, normalizada aqui como em {@link Vehicle}
     * @param stayClass a classe de permanência que corresponde à duração
     * @param estimatedMinutes a duração estimada, em minutos, maior que 0
     * @param createdAt a hora da reserva
     * @param expiresAt a hora a partir da qual a reserva deixa de valer,
     *        depois de {@code createdAt}
     * @throws NullPointerException se algum argumento for {@code null}
     * @throws IllegalArgumentException se a matrícula ficar vazia, a duração
     *         não for positiva ou a expiração não for depois da criação
     */
    public Reservation(String plate, StayClass stayClass, int estimatedMinutes,
            LocalDateTime createdAt, LocalDateTime expiresAt) {
        Objects.requireNonNull(stayClass, "stayClass não pode ser null");
        Objects.requireNonNull(createdAt, "createdAt não pode ser null");
        Objects.requireNonNull(expiresAt, "expiresAt não pode ser null");
        String normalized = Vehicle.normalizePlate(plate);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("plate não pode estar vazia: '" + plate + "'");
        }
        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException("estimatedMinutes deve ser positivo: " + estimatedMinutes);
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt tem de ser depois de createdAt: " + expiresAt);
        }
        this.plate = normalized;
        this.stayClass = stayClass;
        this.estimatedMinutes = estimatedMinutes;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = Status.ACTIVE;
    }

    /** @return a matrícula normalizada, nunca {@code null} nem vazia */
    public String getPlate() {
        return plate;
    }

    /** @return a classe de permanência da estadia reservada */
    public StayClass getStayClass() {
        return stayClass;
    }

    /** @return a duração estimada, em minutos */
    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    /** @return a hora da reserva, nunca {@code null} */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return a hora a partir da qual a reserva deixa de valer */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /** @return o estado, nunca {@code null} */
    public Status getStatus() {
        return status;
    }

    /** @return {@code true} se a reserva ainda estiver à espera do carro */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * Diz se a tolerância já acabou à hora indicada. Não muda o estado: quem
     * marca a reserva como expirada é o serviço, com {@link #expire()}.
     *
     * @param now a hora a verificar
     * @return {@code true} se {@code now} for igual ou posterior a
     *         {@link #getExpiresAt()}
     */
    public boolean isExpiredAt(LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        return !now.isBefore(expiresAt);
    }

    /**
     * Regista que o carro chegou e entrou.
     *
     * @throws IllegalStateException se a reserva já não estiver activa
     */
    public void checkIn() {
        requireActive();
        status = Status.CHECKED_IN;
    }

    /**
     * Regista que a tolerância acabou sem o carro chegar.
     *
     * @throws IllegalStateException se a reserva já não estiver activa
     */
    public void expire() {
        requireActive();
        status = Status.EXPIRED;
    }

    /**
     * Regista que a reserva foi cancelada antes de o carro chegar: o lugar
     * volta a ficar livre para todos.
     *
     * @throws IllegalStateException se a reserva já não estiver activa
     */
    public void cancel() {
        requireActive();
        status = Status.CANCELLED;
    }

    /**
     * Desfaz um cancelamento: a reserva volta a estar activa, com a mesma
     * hora de expiração. É a única vez em que o estado volta atrás.
     *
     * @throws IllegalStateException se a reserva não estiver cancelada
     */
    public void reactivate() {
        if (status != Status.CANCELLED) {
            throw new IllegalStateException("só se reactiva uma reserva cancelada: " + this);
        }
        status = Status.ACTIVE;
    }

    /**
     * @return o identificador na base de dados, ou 0 se a reserva ainda não
     *         foi gravada
     */
    public long getId() {
        return id;
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id tem de ser positivo: " + id);
        }
        if (this.id != 0) {
            throw new IllegalStateException("id já definido: " + this.id);
        }
        this.id = id;
    }

    /** @return a matrícula e o estado, por exemplo {@code "AAA123MC ACTIVE"} */
    @Override
    public String toString() {
        return plate + " " + status;
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("a reserva já não está activa: " + this);
        }
    }
}