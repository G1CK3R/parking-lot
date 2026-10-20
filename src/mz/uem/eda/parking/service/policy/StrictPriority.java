package mz.uem.eda.parking.service.policy;

/**
 * Prioridade estrita: enquanto houver alguém na fila preferencial, é
 * atendido primeiro; a fila normal só anda quando a preferencial está
 * vazia.
 *
 * <p>É a regra mais simples e dá o menor tempo de espera à fila
 * preferencial, mas pode deixar a normal à espera para sempre se chegarem
 * preferenciais sem parar (inanição). A simulação mede esse efeito e
 * compara-o com a {@link RoundRobin}.
 *
 * <p>Sem estado: a decisão depende só dos tamanhos das filas.
 */
public class StrictPriority implements ServicePolicy {

    @Override
    public boolean servePreferential(int preferentialWaiting, int normalWaiting) {
        if (preferentialWaiting <= 0 && normalWaiting <= 0) {
            throw new IllegalArgumentException("as duas filas estão vazias");
        }
        return preferentialWaiting > 0;
    }
}