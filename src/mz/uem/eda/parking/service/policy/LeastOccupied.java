package mz.uem.eda.parking.service.policy;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.StayClass;

/**
 * Coloca o carro na baia com menos carros, sem olhar para a classe de
 * permanência. Em caso de empate, fica a primeira pela ordem da
 * configuração.
 *
 * <p>A ideia é espalhar os carros: numa baia com poucos carros, o que entra
 * fica com poucos por baixo, e quem sair depois tem menos carros por cima.
 * Conta-se o número de carros, e não a percentagem de ocupação, porque é o
 * número de carros por cima que custa manobras (2k).
 *
 * <p>É a segunda política da comparação: melhor que a
 * {@link FirstWithSpace}, que enche uma baia de cada vez, mas ainda sem
 * saber quem sai primeiro, que é o que a {@link ByStayClass} acrescenta.
 */
public class LeastOccupied implements PlacementPolicy {

    @Override
    public Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass) {
        Lane best = null;
        for (Lane lane : lanes) {
            if (!lane.isBuffer() && !lane.isFull()) {
                if (best == null || lane.size() < best.size()) {
                    best = lane;
                }
            }
        }
        return best;
    }
}