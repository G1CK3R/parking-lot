package mz.uem.eda.parking.service.views;

import mz.uem.eda.parking.model.ParkingSession;

/**
 * Um carro estacionado, para a pesquisa de viaturas (RF09): a sessão e a
 * profundidade a que está na baia. Só para ler.
 */
public final class ParkedCar {

    private final ParkingSession session;
    private final int depth;

    public ParkedCar(ParkingSession session, int depth) {
        this.session = session;
        this.depth = depth;
    }

    /** @return a sessão: talão, matrícula, baia, entrada e duração declarada */
    public ParkingSession getSession() {
        return session;
    }

    /** @return quantos carros tem por cima (0 se está no topo) */
    public int getDepth() {
        return depth;
    }

    /** @return quantas manobras custa tirá-lo: 2k, com k carros por cima */
    public int getManoeuvres() {
        return 2 * depth;
    }
}
