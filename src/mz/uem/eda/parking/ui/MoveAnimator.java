package mz.uem.eda.parking.ui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JOptionPane;
import javax.swing.Timer;

import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.service.ExitResult;
import mz.uem.eda.parking.service.ParkingDesk;

/**
 * Reproduz no ecrã os movimentos de uma operação, um de cada vez.
 *
 * <p><b>A animação não decide nada.</b> Quando ela começa, a
 * {@link ParkingDesk} já fez a operação inteira: as pilhas já estão no
 * estado final e tudo já foi gravado. O animador parte de uma
 * {@link ParkScene}, uma fotografia do parque tirada antes, e vai
 * consumindo a fila de {@link Move} do {@link RetrievalPlan} com
 * {@link RetrievalPlan#nextMove()}: cada movimento tira o carro do topo de
 * uma baia da fotografia, leva-o pelo corredor e põe-no no topo de outra
 * (ou fora do parque). O {@link ParkPanel} desenha a fotografia e o carro
 * em movimento.
 *
 * <p><b>É também uma verificação.</b> No fim, a fotografia tem de ter ficado
 * igual ao parque real. Se não ficar, o plano tinha um erro, e o operador é
 * avisado. Durante a reprodução, se o carro do topo de uma baia não for o
 * que o movimento diz, a animação pára logo.
 *
 * <p>Corre na EDT, com um {@link javax.swing.Timer} (não o
 * {@code java.util.Timer}): cada tique avança o carro e pede um novo
 * desenho. Não há threads novas.
 *
 * <p>A duração adapta-se ao número de movimentos: uma saída inteira demora
 * no máximo uns sete segundos, e cada movimento entre 0,3 e 0,9 segundos.
 * O operador pode saltar a animação com {@link #finish()}.
 */
public final class MoveAnimator {

    /** Intervalo entre tiques, cerca de 60 imagens por segundo. */
    private static final int FRAME_MS = 16;

    /** Quanto deve durar, no máximo, a reprodução de uma saída inteira. */
    private static final int EXIT_BUDGET_MS = 7000;

    private static final int MIN_MOVE_MS = 300;
    private static final int MAX_MOVE_MS = 900;

    /** Uma entrada atravessa o corredor inteiro, por isso demora mais. */
    private static final int ENTRY_MS = 1400;

    private final ParkPanel panel;
    private final ParkingDesk desk;
    private final Timer timer;

    // A operação em reprodução.
    private ParkScene scene;
    private RetrievalPlan plan;
    private ParkingSession pendingEntry;
    private Runnable whenDone;
    private int total;
    private int number;
    private int moveMs;

    // O movimento em curso.
    private ParkingSession traveller;
    private String from;
    private String to;
    private long startedAt;
    private int duration;

    /**
     * @param panel o painel onde se desenha
     * @param desk o balcão, para no fim comparar a fotografia com o parque real
     */
    public MoveAnimator(ParkPanel panel, ParkingDesk desk) {
        this.panel = panel;
        this.desk = desk;
        this.timer = new Timer(FRAME_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                tick();
            }
        });
    }

    /** @return {@code true} enquanto houver uma animação a decorrer */
    public boolean isRunning() {
        return scene != null;
    }

    /**
     * Reproduz uma saída: os movimentos do plano e, se a saída libertou
     * lugar para alguém da fila de espera, a entrada desse carro.
     *
     * @param before a fotografia do parque tirada antes da saída
     * @param result o que a {@code ParkingDesk} devolveu
     * @param done o que fazer no fim (por exemplo, mostrar o recibo)
     */
    public void playExit(ParkScene before, ExitResult result, Runnable done) {
        RetrievalPlan exitPlan = result.getPlan();
        int moves = exitPlan.remainingMoves() + (result.getAdmitted() == null ? 0 : 1);
        int perMove = Math.max(MIN_MOVE_MS, Math.min(MAX_MOVE_MS, EXIT_BUDGET_MS / Math.max(1, moves)));
        start(before, exitPlan, result.getAdmitted(), moves, perMove, done);
    }

    /**
     * Reproduz uma entrada: o carro vem da cancela de entrada até à sua baia.
     *
     * @param before a fotografia do parque tirada antes da entrada
     * @param session a sessão do carro que entrou
     * @param done o que fazer no fim (por exemplo, mostrar o talão)
     */
    public void playEntry(ParkScene before, ParkingSession session, Runnable done) {
        start(before, null, session, 1, ENTRY_MS, done);
    }

    /**
     * Salta o resto da animação: o painel passa logo a mostrar o parque
     * real, que já está no estado final.
     */
    public void finish() {
        if (isRunning()) {
            stop();
        }
    }

    private void start(ParkScene before, RetrievalPlan movesToPlay, ParkingSession entry, int count,
            int perMove, Runnable done) {
        if (isRunning()) {
            stop();
        }
        scene = before;
        plan = movesToPlay;
        pendingEntry = entry;
        total = count;
        number = 0;
        moveMs = perMove;
        whenDone = done;
        traveller = null;
        panel.setScene(scene);
        timer.start();
    }

    /** Um tique: começa o movimento seguinte, ou avança o que está em curso. */
    private void tick() {
        if (traveller == null && !startNext()) {
            end();
            return;
        }
        if (!isRunning()) {
            return;
        }
        double progress = (System.currentTimeMillis() - startedAt) / (double) duration;
        if (progress >= 1) {
            if (to != null) {
                scene.putOnTop(to, traveller);
            }
            traveller = null;
            panel.setTraveller(null, null, null, 0, null);
            return;
        }
        panel.setTraveller(traveller, from, to, progress, caption());
    }

    /**
     * Começa o próximo movimento: primeiro os do plano, pela ordem da fila;
     * depois a entrada pendente, se houver.
     *
     * @return {@code false} se já não houver nada para reproduzir
     */
    private boolean startNext() {
        if (plan != null && plan.hasMove()) {
            Move move = plan.nextMove();
            ParkingSession top = scene.takeTop(move.getFromLaneCode());
            if (top == null || !top.getVehicle().getPlate().equals(move.getPlate())) {
                fail();
                return true;
            }
            begin(top, move.getFromLaneCode(), move.getToLaneCode(), moveMs);
            return true;
        }
        if (pendingEntry != null) {
            begin(pendingEntry, null, pendingEntry.getLaneCode(), Math.max(moveMs, ENTRY_MS / 2));
            pendingEntry = null;
            return true;
        }
        return false;
    }

    private void begin(ParkingSession session, String fromLane, String toLane, int ms) {
        traveller = session;
        from = fromLane;
        to = toLane;
        duration = ms;
        startedAt = System.currentTimeMillis();
        number++;
    }

    private String caption() {
        String origin = from == null ? Texts.get("anim.entrance") : from;
        String destination = to == null ? Texts.get("anim.exit") : to;
        return Texts.format("anim.step", String.valueOf(number), String.valueOf(total),
                traveller.getVehicle().getPlate(), origin, destination);
    }

    /** Acabou: confere a fotografia com o parque real e devolve o painel ao normal. */
    private void end() {
        if (scene.matches(desk.lanes())) {
            stop();
        } else {
            fail();
        }
    }

    /**
     * A fotografia não bate certo com o plano ou com o parque real: pára,
     * avisa com o painel ainda congelado no ponto do erro, e só depois
     * volta a mostrar o parque real.
     */
    private void fail() {
        timer.stop();
        JOptionPane.showMessageDialog(panel, Texts.get("anim.mismatch"),
                Texts.get("anim.mismatch.title"), JOptionPane.WARNING_MESSAGE);
        stop();
    }

    /** Pára o relógio, volta a desenhar o parque real e corre o "no fim". */
    private void stop() {
        timer.stop();
        scene = null;
        plan = null;
        pendingEntry = null;
        traveller = null;
        panel.setTraveller(null, null, null, 0, null);
        panel.clearScene();
        Runnable done = whenDone;
        whenDone = null;
        if (done != null) {
            done.run();
        }
    }
}
