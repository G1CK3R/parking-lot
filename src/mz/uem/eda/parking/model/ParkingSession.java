package mz.uem.eda.parking.model;

import java.time.LocalDateTime;

/**
 * Uma estadia de uma viatura no parque, da entrada ao pagamento. É isto que
 * as baias guardam: cada lugar de uma pilha ou fila é uma
 * {@code ParkingSession}, não uma {@link Vehicle}. Assim uma única busca
 * sequencial pelas baias encontra um carro pela matrícula
 * ({@code getVehicle().getPlate()}) ou pelo talão ({@link #getTicketNumber()}),
 * como a tabela {@code lane_slots} já faz com {@code session_id}.
 *
 * <p><b>O talão não é uma classe à parte.</b> O que se imprime no talão é o
 * número, a hora de entrada e a baia, e os três já estão aqui; a tabela
 * {@code parking_sessions} também guarda o número numa coluna sua. Quem
 * atribui o número sequencial é a camada {@code service}.
 *
 * <p><b>A profundidade não é guardada.</b> Muda sempre que entra ou sai um
 * carro por cima, e um valor guardado ficaria desactualizado sem ninguém
 * dar por isso. Calcula-se quando é precisa, com
 * {@code SequentialSearch.indexOf} sobre a baia.
 *
 * <p>A baia é guardada pelo código e não como {@link Lane}, para que quem
 * recebe uma sessão (um diálogo da interface, por exemplo) não consiga
 * chegar à pilha ou fila da baia.
 *
 * <p>Estados: {@link Status#ACTIVE} desde a entrada; {@link Status#CLOSED}
 * depois de {@link #close(LocalDateTime, int)}, que só se chama com o
 * pagamento feito (RF08). Uma sessão fechada não volta a abrir.
 */
public class ParkingSession {

    /** Estado da estadia. */
    public enum Status {
        /** O carro está no parque. */
        ACTIVE,
        /** O carro saiu e a estadia foi paga. */
        CLOSED
    }

    /**
     * Abre uma sessão no momento da entrada. Fica {@link Status#ACTIVE}, sem
     * hora de saída e com zero manobras.
     *
     * @param ticketNumber o número do talão, maior que 0, único no parque
     * @param vehicle a viatura, nunca {@code null}
     * @param laneCode o código da baia onde o carro foi colocado, nunca
     *        {@code null} nem vazio; não pode ser a baia de manobra
     * @param stayClass a classe de permanência declarada à entrada, nunca
     *        {@code null}; pode não coincidir com a classe da baia, quando
     *        houve transbordo
     * @param estimatedMinutes a duração estimada em minutos, ou 0 se não foi
     *        indicada (a forma de a obter ainda está em aberto, ADR-004)
     * @param entryTime a hora de entrada, nunca {@code null}
     * @throws NullPointerException se algum argumento de objecto for
     *         {@code null}
     * @throws IllegalArgumentException se {@code ticketNumber} não for
     *         positivo, {@code laneCode} estiver vazio ou
     *         {@code estimatedMinutes} for negativo
     */
    public ParkingSession(int ticketNumber, Vehicle vehicle, String laneCode,
            StayClass stayClass, int estimatedMinutes, LocalDateTime entryTime) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Fecha a sessão: regista a hora de saída e as manobras que a saída
     * custou, e passa a {@link Status#CLOSED}. Chama-se depois de o
     * pagamento estar registado.
     *
     * @param exitTime a hora de saída, nunca {@code null}, igual ou posterior
     *        à hora de entrada
     * @param manoeuvres os movimentos de outros carros que foi preciso fazer
     *        para tirar este (2k, com k a profundidade), 0 ou mais
     * @throws NullPointerException se {@code exitTime} for {@code null}
     * @throws IllegalArgumentException se {@code exitTime} for anterior à
     *         entrada ou {@code manoeuvres} for negativo
     * @throws IllegalStateException se a sessão já estiver fechada
     * @complexity O(1)
     */
    public void close(LocalDateTime exitTime, int manoeuvres) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o número do talão. É um {@code int}, e não um {@code long},
     *         para servir directamente de chave ao radix sort
     *         ({@code SortKey.of} devolve {@code int}); dois mil milhões de
     *         talões chegam para a vida do parque.
     */
    public int getTicketNumber() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a viatura, nunca {@code null} */
    public Vehicle getVehicle() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o código da baia onde o carro está ou esteve */
    public String getLaneCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a classe de permanência declarada à entrada */
    public StayClass getStayClass() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a duração estimada em minutos, ou 0 se não foi indicada */
    public int getEstimatedMinutes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a hora de entrada, nunca {@code null} */
    public LocalDateTime getEntryTime() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a hora de saída, ou {@code null} enquanto a sessão estiver activa */
    public LocalDateTime getExitTime() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return as manobras registadas ao fechar, ou 0 enquanto estiver activa */
    public int getManoeuvres() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o estado, nunca {@code null} */
    public Status getStatus() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return {@code true} se o carro ainda estiver no parque */
    public boolean isActive() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se a sessão ainda não
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

    /** Duas sessões são iguais se tiverem o mesmo número de talão. */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Coerente com {@link #equals(Object)}: calculado só a partir do número de talão. */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o talão e a matrícula, por exemplo {@code "#42 AAA123MC"} */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
