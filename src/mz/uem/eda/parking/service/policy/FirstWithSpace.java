package mz.uem.eda.parking.service.policy;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.StayClass;

/**
 * Coloca o carro na primeira baia com espaço, pela ordem da configuração,
 * sem olhar para a classe de permanência. É a política ingénua: serve de
 * linha de base nas medições, para mostrar quanto poupam as outras.
 */
public class FirstWithSpace implements PlacementPolicy {

    /**
     * {@inheritDoc}
     *
     * <p>Escolhe a primeira baia com espaço, pela ordem da configuração.
     *
     * @complexity O(B): uma passagem pelas baias, que pára na primeira com espaço
     */
    @Override
    public Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass) {
        for (Lane lane : lanes) {
            if (!lane.isBuffer() && !lane.isFull()) {
                return lane;
            }
        }
        return null;
    }
}