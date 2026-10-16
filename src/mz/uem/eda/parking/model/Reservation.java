package mz.uem.eda.parking.model;

import java.time.LocalDateTime;

/**
 * Uma reserva de lugar por matrícula (RF10). Fica fora do MVP; o contrato
 * existe para a camada {@code service} programar contra ele.
 *
 * <p>Todas as reservas têm a mesma tolerância, por isso <b>a ordem de
 * criação é a ordem de expiração</b>: uma fila simples basta para as
 * expirar, sem ordenar nada (RF12). É essa propriedade que justifica a fila;
 * quem garante que {@code expiresAt} é sempre {@code createdAt} mais a mesma
 * tolerância é {@code service.ReservationService}.
 *
 * <p>Estados: {@link Status#ACTIVE} ao criar; passa a
 * {@link Status#CHECKED_IN} quando o carro chega a tempo, ou a
 * {@link Status#EXPIRED} quando a tolerância acaba. As duas passagens são
 * finais.
 */
public class Reservation {

    /** Estado da reserva. */
    public enum Status {
        /** À espera do carro. */
        ACTIVE,
        /** O carro chegou e a reserva deu lugar a uma sessão. */
        CHECKED_IN,
        /** A tolerância acabou antes de o carro chegar. */
        EXPIRED
    }

    /**
     * Cria uma reserva activa.
     *
     * @param plate a matrícula, em qualquer formato; é normalizada com
     *        {@link Vehicle#normalizePlate(String)}
     * @param stayClass a classe de permanência para que se reserva, nunca
     *        {@code null}; é a classe que conta para o espaço livre (RF11)
     * @param createdAt a hora de criação, nunca {@code null}
     * @param expiresAt a hora a partir da qual a reserva expira, nunca
     *        {@code null}, posterior a {@code createdAt}
     * @throws NullPointerException se algum argumento for {@code null}
     * @throws IllegalArgumentException se a matrícula ficar vazia depois de
     *         normalizada ou {@code expiresAt} não for posterior a
     *         {@code createdAt}
     */
    public Reservation(String plate, StayClass stayClass, LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Indica se a reserva já devia ter expirado no instante dado. Não muda o
     * estado: quem expira é o serviço, com {@link #expire()}.
     *
     * @param now o instante de referência, nunca {@code null}
     * @return {@code true} se {@code now} for igual ou posterior a
     *         {@code expiresAt}
     * @throws NullPointerException se {@code now} for {@code null}
     * @complexity O(1)
     */
    public boolean isExpiredAt(LocalDateTime now) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Marca a chegada do carro: passa a {@link Status#CHECKED_IN}.
     *
     * @throws IllegalStateException se a reserva não estiver
     *         {@link Status#ACTIVE}
     * @complexity O(1)
     */
    public void checkIn() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Marca a expiração: passa a {@link Status#EXPIRED}.
     *
     * @throws IllegalStateException se a reserva não estiver
     *         {@link Status#ACTIVE}
     * @complexity O(1)
     */
    public void expire() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a matrícula normalizada */
    public String getPlate() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a classe de permanência reservada */
    public StayClass getStayClass() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a hora de criação */
    public LocalDateTime getCreatedAt() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a hora de expiração */
    public LocalDateTime getExpiresAt() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o estado, nunca {@code null} */
    public Status getStatus() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se a reserva ainda não
     *         foi gravada
     */
    public long getId() {
        throw new UnsupportedOperationException("Not implemented yet");
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
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
