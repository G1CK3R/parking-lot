package mz.uem.eda.parking.ui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;

import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.parking.WaitingCar;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O painel à direita do parque: as duas filas de espera e os últimos
 * movimentos.
 *
 * <p><b>Filas de espera.</b> Quando o parque está cheio, os carros esperam
 * numa de duas filas, a preferencial e a normal (ADR-007). O painel mostra
 * cada fila pela ordem de chegada, com a posição de cada carro, há quanto
 * tempo espera e quanto tempo disse que vai ficar. Quem entra a seguir é
 * decidido pela política de atendimento, não pelo painel.
 *
 * <p><b>Últimos movimentos.</b> Os 20 movimentos mais recentes que a
 * {@link ParkingDesk} guarda, do mais antigo (em cima) para o mais recente
 * (em baixo), com uma cor para cada tipo: entrada, manobra e saída.
 *
 * <p>Só mostra: lê tudo da {@code ParkingDesk} em {@link #refresh()} e
 * copia para modelos de lista do Swing (ADR-028); as contagens vêm já
 * feitas da {@code ParkingDesk}. Um relógio de 30 segundos volta a
 * escrever os tempos de espera.
 */
public final class SidePanel {

    /** Largura do painel, em píxeis. */
    private static final int WIDTH = 300;

    /** De quanto em quanto tempo se actualizam os tempos de espera. */
    private static final int REFRESH_MS = 30_000;

    private static final Color BACKGROUND = Theme.LOT_WALL;
    private static final Color LIST_BACKGROUND = new Color(0x34383D);
    private static final Color TEXT = new Color(0xE8EAED);
    private static final Color TEXT_DIM = new Color(0xBDC1C6);

    private final ParkingDesk desk;
    private final JPanel panel = new JPanel(new BorderLayout(0, Theme.GAP));
    private final JLabel preferentialTitle = new JLabel();
    private final JLabel normalTitle = new JLabel();
    private final DefaultListModel<String> preferential = new DefaultListModel<>();
    private final DefaultListModel<String> normal = new DefaultListModel<>();
    private final DefaultListModel<String> moves = new DefaultListModel<>();
    private final JList<String> movesList = new JList<>(moves);
    private final Timer clock;

    /**
     * @param desk o balcão de onde se lêem as filas e os movimentos
     */
    public SidePanel(ParkingDesk desk) {
        this.desk = desk;

        JPanel queues = new JPanel();
        queues.setLayout(new BoxLayout(queues, BoxLayout.Y_AXIS));
        queues.setOpaque(false);
        queues.add(title(Texts.get("side.queues")));
        queues.add(Box.createVerticalStrut(Theme.GAP));
        queues.add(subtitle(preferentialTitle));
        queues.add(listOf(new JList<>(preferential), 2, 40));
        queues.add(Box.createVerticalStrut(Theme.GAP));
        queues.add(subtitle(normalTitle));
        queues.add(listOf(new JList<>(normal), 4, 40));

        JPanel log = new JPanel(new BorderLayout(0, Theme.GAP / 2));
        log.setOpaque(false);
        JPanel logTitle = new JPanel();
        logTitle.setLayout(new BoxLayout(logTitle, BoxLayout.Y_AXIS));
        logTitle.setOpaque(false);
        logTitle.add(title(Texts.get("side.moves")));
        logTitle.add(subtitle(new JLabel(Texts.format("side.moves.legend",
                hex(Theme.CENTER_LINE), hex(MoveColors.ENTRY), hex(MoveColors.EXIT)))));
        log.add(logTitle, BorderLayout.NORTH);
        log.add(listOf(movesList, 0, 24), BorderLayout.CENTER);

        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.PADDING, Theme.PADDING, Theme.PADDING, Theme.PADDING));
        panel.setPreferredSize(new Dimension(WIDTH, 400));
        panel.add(queues, BorderLayout.NORTH);
        panel.add(log, BorderLayout.CENTER);

        clock = new Timer(REFRESH_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        clock.start();
        refresh();
    }

    /** @return o componente a pôr na janela */
    public JComponent getComponent() {
        return panel;
    }

    /** Volta a ler as filas e os movimentos e a escrevê-los no painel. */
    public void refresh() {
        LocalDateTime now = LocalDateTime.now();
        preferentialTitle.setText(Texts.format("side.preferential", String.valueOf(desk.preferentialWaitingCount())));
        normalTitle.setText(Texts.format("side.normal", String.valueOf(desk.normalWaitingCount())));
        fill(preferential, desk.preferentialQueue(), now);
        fill(normal, desk.normalQueue(), now);

        moves.clear();
        for (Move move : desk.recentMoves()) {
            moves.addElement(row(move));
        }
        if (moves.isEmpty()) {
            moves.addElement(Texts.get("side.moves.empty"));
        }
        movesList.ensureIndexIsVisible(moves.size() - 1);
    }

    /** Pára o relógio; chama-se quando a janela fecha. */
    public void stop() {
        clock.stop();
    }

    private static void fill(DefaultListModel<String> model, Iterable<WaitingCar> queue, LocalDateTime now) {
        model.clear();
        int position = 1;
        for (WaitingCar car : queue) {
            long waited = Math.max(0, Duration.between(car.getArrivedAt(), now).toMinutes());
            model.addElement(Texts.format("side.waiting.row", String.valueOf(position),
                    car.getVehicle().getPlate(), Texts.duration(waited),
                    Texts.duration(car.getEstimatedMinutes())));
            position++;
        }
        if (model.isEmpty()) {
            model.addElement(Texts.get("side.waiting.empty"));
        }
    }

    /** Uma linha da lista de movimentos: a cor diz o tipo, depois a matrícula e o percurso. */
    private static String row(Move move) {
        Color color;
        if (move.isEntry()) {
            color = MoveColors.ENTRY;
        } else if (move.isExit()) {
            color = MoveColors.EXIT;
        } else {
            color = Theme.CENTER_LINE;
        }
        String from = move.getFromLaneCode() == null ? Texts.get("anim.entrance") : move.getFromLaneCode();
        String to = move.getToLaneCode() == null ? Texts.get("anim.exit") : move.getToLaneCode();
        return Texts.format("side.move.row", hex(color), move.getPlate(), from, to);
    }

    // ------------------------------------------------------------------
    // Aspecto
    // ------------------------------------------------------------------

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setAlignmentX(0f);
        return label;
    }

    private static JLabel subtitle(JLabel label) {
        label.setForeground(TEXT_DIM);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.GAP / 2, 0));
        label.setAlignmentX(0f);
        return label;
    }

    /**
     * Uma lista escura, sem selecção, dentro de uma caixa com barra de
     * deslocamento.
     *
     * @param rows quantas linhas mostrar sem deslocar, ou 0 para ocupar o
     *        espaço que houver
     * @param rowHeight a altura de cada linha
     */
    private static JScrollPane listOf(JList<String> list, int rows, int rowHeight) {
        list.setBackground(LIST_BACKGROUND);
        list.setForeground(TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionBackground(LIST_BACKGROUND);
        list.setSelectionForeground(TEXT);
        list.setFocusable(false);
        list.setFixedCellHeight(rowHeight);
        if (rows > 0) {
            list.setVisibleRowCount(rows);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x45494F)));
        scroll.setAlignmentX(0f);
        if (rows > 0) {
            Dimension size = new Dimension(WIDTH, rows * rowHeight + 4);
            scroll.setPreferredSize(size);
            scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        }
        return scroll;
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** As cores dos tipos de movimento; a manobra usa o amarelo do corredor. */
    private static final class MoveColors {
        private static final Color ENTRY = new Color(0x34A853);
        private static final Color EXIT = new Color(0xEA4335);

        private MoveColors() {
        }
    }
}
