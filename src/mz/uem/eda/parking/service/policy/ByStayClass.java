package mz.uem.eda.parking.service.policy;

import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;

/**
 * Coloca o carro pela classe de permanência, com a regra «empilhar só sobre
 * quem sai depois» (ADR-005). Se o carro novo só ficar por cima de carros
 * que saem depois dele, nunca bloqueia ninguém: quando chegar a vez de cada
 * um, os de cima já saíram.
 *
 * <p>Procura em três passos, e em cada um fica a primeira baia pela ordem da
 * configuração:
 * <ol>
 *   <li><b>Baia da sua classe</b> com espaço e cujo topo não saia antes do
 *       carro novo (ou vazia).</li>
 *   <li><b>Transbordo</b>: qualquer outra baia com espaço que cumpra a mesma
 *       regra. Um carro de curta duração pode ir para o topo de uma baia de
 *       jornada completa, mas um de jornada completa não vai para cima de
 *       um de curta duração.</li>
 *   <li><b>Último recurso</b>: nenhuma baia cumpre a regra. Para o carro não
 *       ficar à espera com lugares livres, vai para a primeira baia com
 *       espaço da sua classe e, se não houver, para a primeira com espaço.
 *       Aqui a regra é quebrada e haverá manobras, que a simulação conta.</li>
 * </ol>
 *
 * <p>A classe de uma baia diz a que carros se destina; com o transbordo, uma
 * baia pode ter carros de outra classe. A regra olha sempre para a classe do
 * carro do topo, não para a da baia.
 *
 * <p>Só lê o topo de cada baia ({@code peek}), sem mexer nas pilhas.
 */
public class ByStayClass implements PlacementPolicy {

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException se {@code stayClass} for {@code null}
     * @complexity O(B): no pior caso, três percursos das B baias
     */
    @Override
    public Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass) {
        Objects.requireNonNull(stayClass, "stayClass não pode ser null");

        // 1. Baia da sua classe, sem quebrar a regra.
        for (Lane lane : lanes) {
            if (hasSpace(lane) && lane.getStayClass() == stayClass && keepsOrder(lane, stayClass)) {
                return lane;
            }
        }
        // 2. Transbordo para outra classe, sem quebrar a regra.
        for (Lane lane : lanes) {
            if (hasSpace(lane) && keepsOrder(lane, stayClass)) {
                return lane;
            }
        }
        // 3. Último recurso: quebra a regra para não deixar o carro à espera.
        Lane anyLane = null;
        for (Lane lane : lanes) {
            if (hasSpace(lane)) {
                if (lane.getStayClass() == stayClass) {
                    return lane;
                }
                if (anyLane == null) {
                    anyLane = lane;
                }
            }
        }
        return anyLane;
    }

    /** Baia de estacionamento com espaço para mais um carro. */
    private static boolean hasSpace(Lane lane) {
        return !lane.isBuffer() && !lane.isFull();
    }

    /**
     * Diz se o carro novo pode ir para o topo sem bloquear ninguém: a baia
     * está vazia, ou o carro do topo não sai antes dele. Duas classes iguais
     * cumprem a regra.
     *
     * @complexity O(1)
     */
    private static boolean keepsOrder(Lane lane, StayClass incoming) {
        if (lane.isEmpty()) {
            return true;
        }
        ParkingSession top = lane.asStack().peek();
        return !top.getStayClass().leavesBefore(incoming);
    }
}