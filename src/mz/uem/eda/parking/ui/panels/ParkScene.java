package mz.uem.eda.parking.ui.panels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.service.views.LaneView;

/**
 * Uma fotografia do parque, tirada antes de uma operação, para a animação
 * ter de onde partir. A {@link mz.uem.eda.parking.service.ParkingDesk} faz a
 * operação de uma vez; o {@link MoveAnimator} reproduz depois os movimentos
 * nesta fotografia, um a um, enquanto o {@link ParkPanel} a desenha.
 *
 * <p><b>Não é uma estrutura do domínio.</b> Guarda, para cada baia, a lista
 * das sessões do fundo para o topo, em listas do {@code java.util}, que a
 * interface pode usar (ADR-028). Serve só para desenhar: as pilhas
 * verdadeiras continuam na {@code ParkingDesk}, que a interface nunca toca.
 *
 * <p>No fim da animação, {@link #matches} compara a fotografia com o parque
 * real. Se não baterem certo, o plano de movimentos tem um erro, e a
 * animação mostrou-o.
 */
public final class ParkScene {

    /** Por baia (pelo código), as sessões do fundo (índice 0) para o topo. */
    private final Map<String, List<ParkingSession>> lanes = new LinkedHashMap<>();

    private ParkScene() {
    }

    /**
     * Tira a fotografia do parque como está agora.
     *
     * @param views as baias, da {@code ParkingDesk}
     * @return a fotografia
     */
    public static ParkScene capture(Iterable<LaneView> views) {
        ParkScene scene = new ParkScene();
        for (LaneView view : views) {
            List<ParkingSession> sessions = new ArrayList<>();
            // sessions() vai do topo para o fundo; inserir à cabeça deixa a lista do fundo para o topo.
            for (ParkingSession session : view.sessions()) {
                sessions.add(0, session);
            }
            scene.lanes.put(view.getCode(), sessions);
        }
        return scene;
    }

    /**
     * @param code o código da baia
     * @return as sessões da baia, do fundo (índice 0) para o topo
     */
    List<ParkingSession> lane(String code) {
        return lanes.get(code);
    }

    /**
     * Tira o carro do topo da baia, como faria um {@code pop}.
     *
     * @param code o código da baia
     * @return a sessão que estava no topo, ou {@code null} se a baia estava vazia
     */
    ParkingSession takeTop(String code) {
        List<ParkingSession> sessions = lanes.get(code);
        if (sessions == null || sessions.isEmpty()) {
            return null;
        }
        return sessions.remove(sessions.size() - 1);
    }

    /**
     * Põe um carro no topo da baia, como faria um {@code push}.
     *
     * @param code o código da baia
     * @param session a sessão do carro
     */
    void putOnTop(String code, ParkingSession session) {
        lanes.get(code).add(session);
    }

    /**
     * Diz se a fotografia é igual ao parque real: as mesmas sessões, pela
     * mesma ordem, em todas as baias.
     *
     * @param views as baias, da {@code ParkingDesk}
     * @return {@code true} se baterem certo
     */
    boolean matches(Iterable<LaneView> views) {
        for (LaneView view : views) {
            List<ParkingSession> sessions = lanes.get(view.getCode());
            if (sessions == null || sessions.size() != view.size()) {
                return false;
            }
            int index = sessions.size() - 1;
            for (ParkingSession session : view.sessions()) {
                if (!sessions.get(index).equals(session)) {
                    return false;
                }
                index--;
            }
        }
        return true;
    }
}
