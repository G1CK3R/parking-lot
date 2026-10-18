package mz.uem.eda.parking.service.policy;

/**
 * Regra de atendimento: quando uma saída liberta espaço, decide de qual das
 * duas filas de espera, a preferencial ou a normal, sai o próximo carro.
 * É assim que se tem prioridade sem fila de prioridade (ADR-007).
 *
 * <p>A política só decide; quem retira o carro da fila é o
 * {@code WaitingQueueService}. Uma política pode guardar estado (a rotação
 * conta quantos atendeu de cada fila), por isso este método só se chama
 * quando um carro vai mesmo ser atendido.
 */
public interface ServicePolicy {

    /**
     * Diz se o próximo carro sai da fila preferencial.
     *
     * @param preferentialWaiting quantos carros esperam na fila preferencial
     * @param normalWaiting quantos carros esperam na fila normal
     * @return {@code true} para atender a fila preferencial,
     *         {@code false} para atender a normal
     * @throws IllegalArgumentException se as duas filas estiverem vazias
     * @complexity O(1)
     */
    boolean servePreferential(int preferentialWaiting, int normalWaiting);
}