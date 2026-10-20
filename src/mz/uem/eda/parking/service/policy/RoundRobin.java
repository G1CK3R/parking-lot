package mz.uem.eda.parking.service.policy;

/**
 * Rotação k:1: atende até k carros seguidos da fila preferencial e depois
 * um da normal, e recomeça. Se uma das filas estiver vazia, atende a outra
 * sem esperar pela vez.
 *
 * <p>A fila preferencial continua a andar mais depressa, mas a normal nunca
 * fica parada: em cada k + 1 atendimentos, pelo menos um é dela. É a
 * resposta à inanição da {@link StrictPriority}.
 *
 * <p>Guarda estado (quantos preferenciais seguidos já atendeu), por isso
 * {@link #servePreferential} só se chama quando um carro vai mesmo ser
 * atendido.
 */
public class RoundRobin implements ServicePolicy {

    private final int preferentialTurns;
    private int servedInARow;

    /**
     * Cria a rotação.
     *
     * @param preferentialTurns k, quantos carros preferenciais seguidos se
     *        atendem antes de um normal, pelo menos 1
     * @throws IllegalArgumentException se {@code preferentialTurns} for
     *         menor que 1
     */
    public RoundRobin(int preferentialTurns) {
        if (preferentialTurns < 1) {
            throw new IllegalArgumentException("preferentialTurns tem de ser pelo menos 1: "
                    + preferentialTurns);
        }
        this.preferentialTurns = preferentialTurns;
        this.servedInARow = 0;
    }

    @Override
    public boolean servePreferential(int preferentialWaiting, int normalWaiting) {
        if (preferentialWaiting <= 0 && normalWaiting <= 0) {
            throw new IllegalArgumentException("as duas filas estão vazias");
        }
        if (preferentialWaiting <= 0) {
            servedInARow = 0;
            return false;
        }
        if (normalWaiting <= 0) {
            return true;
        }
        if (servedInARow < preferentialTurns) {
            servedInARow++;
            return true;
        }
        servedInARow = 0;
        return false;
    }

    /** @return k, os preferenciais seguidos antes de um normal */
    public int getPreferentialTurns() {
        return preferentialTurns;
    }
}