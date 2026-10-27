package mz.uem.eda.parking.service.policy;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;

/**
 * Coloca o carro pela hora a que diz que sai, com a mesma regra da
 * {@link ByStayClass}, «empilhar só sobre quem sai depois» (ADR-005), mas
 * com horas em vez de classes.
 *
 * <p><b>Porque existe.</b> A {@code ByStayClass} só distingue três classes,
 * e 70% dos carros são de jornada completa: entre eles, a regra deixa
 * empilhar qualquer um sobre qualquer um, e um carro que sai às 16h fica
 * muitas vezes por baixo de um que sai às 18h. Na simulação, isso fez a
 * {@code ByStayClass} perder para a {@link LeastOccupied}. Com a hora
 * declarada (ADR-004), a regra distingue também os carros da mesma classe.
 *
 * <p>Procura em três passos, só a ler o topo de cada baia:
 * <ol>
 *   <li><b>Encaixe mais justo</b>: das baias com espaço cujo topo sai à
 *       mesma hora ou depois do carro novo, a do topo que sai mais cedo.
 *       Assim o carro não bloqueia ninguém, e as baias com topos que saem
 *       muito tarde ficam para carros que também saem tarde.</li>
 *   <li><b>Baia vazia</b>: a primeira, pela ordem da configuração.</li>
 *   <li><b>Último recurso</b>: todas as baias com espaço têm por cima um
 *       carro que sai antes do novo, por isso alguém vai ficar bloqueado. Fica
 *       a baia cujo topo sai mais tarde, que é a que o bloqueia durante menos
 *       tempo.</li>
 * </ol>
 * Os empates ficam com a primeira baia pela ordem da configuração. A classe
 * da baia não conta: um carro vai para onde a hora mandar.
 *
 * <p>A hora de saída de um carro já estacionado é a sua entrada mais a
 * duração que declarou. Como as durações declaradas não são exactas, a regra
 * ainda pode falhar, e as manobras que isso custa aparecem na simulação.
 */
public class ByDeparture implements PlacementPolicy {

    /**
     * Sem a hora de saída não há como aplicar a regra: escolhe como a
     * {@link LeastOccupied}. O {@code EntryService} usa sempre o outro
     * método.
     */
    @Override
    public Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass) {
        return new LeastOccupied().chooseLane(lanes, stayClass);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException se {@code expectedExit} for {@code null}
     * @complexity O(B): um só percurso das B baias
     */
    @Override
    public Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass, LocalDateTime expectedExit) {
        Objects.requireNonNull(expectedExit, "expectedExit não pode ser null");
        Lane tightest = null;
        LocalDateTime tightestTop = null;
        Lane empty = null;
        Lane latest = null;
        LocalDateTime latestTop = null;

        for (Lane lane : lanes) {
            if (lane.isBuffer() || lane.isFull()) {
                continue;
            }
            if (lane.isEmpty()) {
                if (empty == null) {
                    empty = lane;
                }
                continue;
            }
            LocalDateTime top = expectedExitOf(lane.asStack().peek());
            if (!top.isBefore(expectedExit)) {
                // O topo sai depois (ou ao mesmo tempo): o carro novo não o bloqueia.
                if (tightest == null || top.isBefore(tightestTop)) {
                    tightest = lane;
                    tightestTop = top;
                }
            } else if (latest == null || top.isAfter(latestTop)) {
                latest = lane;
                latestTop = top;
            }
        }

        if (tightest != null) {
            return tightest;
        }
        if (empty != null) {
            return empty;
        }
        return latest;
    }

    /** A hora a que um carro estacionado disse que saía. */
    private static LocalDateTime expectedExitOf(ParkingSession session) {
        return session.getEntryTime().plusMinutes(session.getEstimatedMinutes());
    }
}
