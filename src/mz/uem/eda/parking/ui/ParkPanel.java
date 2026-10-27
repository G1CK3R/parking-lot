package mz.uem.eda.parking.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.UIManager;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.service.LaneView;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O parque visto de cima. As baias ficam lado a lado, numa só fila, todas
 * com a boca virada para o corredor em baixo, como num parque real. Cada
 * baia é uma fila indiana de carros: o primeiro a entrar foi até ao fundo, e
 * o último ficou junto à boca. Esse é o topo da pilha, o único que sai sem
 * manobras. A baia de manobra é a última da fila.
 *
 * <p>Os carros estão na vertical, de frente para o fundo da baia, como
 * entraram, e cada um tem a sua cor de pintura (sai da matrícula). A classe
 * de permanência vê-se no lugar pintado no chão à volta do carro e na placa
 * da baia. Com o rato por cima de um carro aparecem a matrícula, o talão, a
 * classe, a hora de entrada e quantos carros tem por cima, que é o custo da
 * sua saída.
 *
 * <p><b>Desenha, não decide.</b> Lê as baias da {@link ParkingDesk} como
 * {@link LaneView}, só de leitura, sempre que se redesenha; não guarda cópia
 * do parque e não toca em nenhuma pilha (ADR-032). Para mostrar o estado
 * novo depois de uma operação, basta chamar {@link #repaint()}.
 *
 * <p>As medidas não são fixas: calculam-se do tamanho do painel em cada
 * desenho, por isso o parque ocupa a janela toda, seja qual for o ecrã.
 *
 * <p><b>Durante uma animação</b>, o {@link MoveAnimator} entrega-lhe uma
 * {@link ParkScene} (a fotografia do parque antes da operação) e o carro em
 * movimento. Enquanto houver fotografia, as baias desenham-se a partir dela
 * e não das baias reais; o carro em movimento recua da boca da sua baia
 * para o corredor, vira, segue pelo corredor e entra na baia de destino.
 *
 * <p>As listas do {@code java.util} usadas aqui só arrumam o que se vai
 * desenhar (ADR-028); não pesquisam nem ordenam dados do parque.
 *
 * <p>É a única classe da interface que estende um componente Swing, porque
 * desenhar à mão obriga a reescrever {@code paintComponent}. O
 * {@code @SuppressWarnings("serial")} cala o aviso de serialização, que não
 * se aplica: o painel nunca é gravado em ficheiro.
 */
@SuppressWarnings("serial")
public final class ParkPanel extends JPanel {

    /** Espaço entre o muro e o asfalto, em píxeis. */
    private static final int MARGIN = 18;

    /** Altura da placa com o código de cada baia. */
    private static final int HEADER = 48;

    /** Altura da faixa da legenda, por baixo do corredor. */
    private static final int LEGEND = 30;

    /** Que fracção da coluna a baia ocupa; o resto é o espaço entre baias. */
    private static final double LANE_SHARE = 0.86;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

    private static final Color TEXT = new Color(0xE8EAED);
    private static final Color TEXT_DIM = new Color(0xBDC1C6);
    private static final Color PLATE_PANEL = new Color(0x24272B);
    private static final Color BAR_TRACK = new Color(0x3C4046);
    private static final Color GATE_RED = new Color(0xD93025);
    private static final Color GATE_SIGN = new Color(0x1E7D32);
    private static final Color KERB = new Color(0x8A8F94);
    private static final Color HEADLIGHT = new Color(0xFFF4C2);
    private static final Color TAILLIGHT = new Color(0xC5221F);

    private final ParkingDesk desk;

    /** Os carros desenhados na última pintura, para saber o que está sob o rato. */
    private final List<CarSpot> spots = new ArrayList<>();
    private CarSpot hovered;

    /** A fotografia a desenhar durante uma animação, ou {@code null} fora dela. */
    private ParkScene scene;

    // O carro em movimento, durante uma animação.
    private ParkingSession traveller;
    private String travellerFrom;
    private String travellerTo;
    private double travellerProgress;
    private String caption;

    /**
     * @param desk o balcão de onde se lêem as baias
     */
    public ParkPanel(ParkingDesk desk) {
        this.desk = desk;
        setBackground(Theme.LOT_WALL);
        setPreferredSize(new Dimension(1000, 600));
        setMinimumSize(new Dimension(800, 480));
        // Um texto qualquer liga as dicas; o texto de cada carro vem de getToolTipText(MouseEvent).
        setToolTipText("");

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hover(spotAt(e.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover(null);
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        CarSpot spot = spotAt(event.getPoint());
        return spot == null ? null : tooltip(spot);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            paintLot(g2);
        } finally {
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // O parque inteiro
    // ------------------------------------------------------------------

    private void paintLot(Graphics2D g) {
        List<LaneView> lanes = new ArrayList<>();
        int deepest = 1;
        for (LaneView lane : desk.lanes()) {
            lanes.add(lane);
            deepest = Math.max(deepest, lane.getCapacity());
        }

        Geometry geo = new Geometry(getWidth(), getHeight(), Math.max(1, lanes.size()), deepest);
        spots.clear();

        paintGround(g, geo);
        paintCorridor(g, geo);
        for (int i = 0; i < lanes.size(); i++) {
            paintLane(g, geo, lanes.get(i), i, carsOf(lanes.get(i)));
        }
        paintLegend(g, geo);

        if (hovered != null) {
            paintHighlight(g, hovered.bounds);
        }
        if (traveller != null) {
            paintTraveller(g, geo, lanes);
        }
        if (caption != null) {
            paintCaption(g, geo);
        }
    }

    /**
     * Os carros de uma baia, do topo para o fundo: da fotografia, durante
     * uma animação; da baia real, fora dela.
     */
    private List<ParkingSession> carsOf(LaneView lane) {
        List<ParkingSession> cars = new ArrayList<>();
        if (scene != null) {
            List<ParkingSession> bottomToTop = scene.lane(lane.getCode());
            for (int i = bottomToTop.size() - 1; i >= 0; i--) {
                cars.add(bottomToTop.get(i));
            }
        } else {
            for (ParkingSession session : lane.sessions()) {
                cars.add(session);
            }
        }
        return cars;
    }

    // ------------------------------------------------------------------
    // Animação (chamado pelo MoveAnimator)
    // ------------------------------------------------------------------

    /**
     * Passa a desenhar a fotografia em vez das baias reais.
     *
     * @param snapshot a fotografia do parque antes da operação
     */
    void setScene(ParkScene snapshot) {
        scene = snapshot;
        hovered = null;
        repaint();
    }

    /** Volta a desenhar as baias reais. */
    void clearScene() {
        scene = null;
        hovered = null;
        repaint();
    }

    /**
     * Mostra o carro em movimento.
     *
     * @param session o carro, ou {@code null} para não mostrar nenhum
     * @param from a baia de onde sai, ou {@code null} se vem da entrada
     * @param to a baia para onde vai, ou {@code null} se sai do parque
     * @param progress quanto do caminho já fez, de 0 a 1
     * @param text a legenda do movimento, ou {@code null}
     */
    void setTraveller(ParkingSession session, String from, String to, double progress, String text) {
        traveller = session;
        travellerFrom = from;
        travellerTo = to;
        travellerProgress = progress;
        caption = text;
        repaint();
    }

    /**
     * O carro em movimento. O caminho tem até quatro pontos: o lugar de
     * onde sai (ou a cancela de entrada), o meio do corredor em frente a
     * essa baia, o meio do corredor em frente à baia de destino, e o lugar
     * onde fica (ou a cancela de saída). Na baia o carro está na vertical;
     * no corredor vira para o lado para onde vai.
     */
    private void paintTraveller(Graphics2D g, Geometry geo, List<LaneView> lanes) {
        double middle = geo.mouth + geo.corridorHeight / 2;
        double[] start = travellerFrom == null ? new double[] {MARGIN + 70, middle}
                : slotCentre(geo, lanes, travellerFrom);
        double[] end = travellerTo == null ? new double[] {geo.width - MARGIN - 70, middle}
                : slotCentre(geo, lanes, travellerTo);
        if (start == null || end == null) {
            return;
        }

        List<double[]> path = new ArrayList<>();
        path.add(start);
        if (travellerFrom != null) {
            path.add(new double[] {start[0], middle});
        }
        if (travellerTo != null) {
            path.add(new double[] {end[0], middle});
        }
        path.add(end);

        // Comprimento total do caminho e o ponto onde o carro está agora.
        double length = 0;
        for (int i = 1; i < path.size(); i++) {
            length += distance(path.get(i - 1), path.get(i));
        }
        double target = ease(travellerProgress) * length;
        double x = start[0];
        double y = start[1];
        double angle = 0;
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1);
            double[] b = path.get(i);
            double segment = distance(a, b);
            if (segment == 0) {
                continue;
            }
            double along = Math.min(target, segment);
            x = a[0] + (b[0] - a[0]) * along / segment;
            y = a[1] + (b[1] - a[1]) * along / segment;
            angle = headingOf(a, b);
            target -= segment;
            if (target <= 0) {
                break;
            }
        }

        double carLength = geo.slotHeight * 0.82;
        double carWidth = Math.min(geo.laneWidth * 0.66, carLength * 0.47);
        Graphics2D turned = (Graphics2D) g.create();
        try {
            turned.rotate(angle, x, y);
            Rectangle2D car = new Rectangle2D.Double(x - carWidth / 2, y - carLength / 2, carWidth, carLength);
            paintCar(turned, car, Theme.paintOf(traveller.getVehicle().getPlate()), false);
            paintHighlight(turned, car.getBounds());
        } finally {
            turned.dispose();
        }
    }

    /**
     * O centro do lugar onde o carro em movimento estava ou vai ficar: o
     * lugar a seguir ao topo actual da baia na fotografia.
     *
     * @return {x, y}, ou {@code null} se a baia não existir
     */
    private double[] slotCentre(Geometry geo, List<LaneView> lanes, String code) {
        for (int column = 0; column < lanes.size(); column++) {
            LaneView lane = lanes.get(column);
            if (lane.getCode().equals(code)) {
                int position = scene == null ? lane.size() : scene.lane(code).size();
                double top = slotTop(geo.mouth, lane.getCapacity(), position, geo.slotHeight);
                return new double[] {geo.laneX(column) + geo.laneWidth / 2, top + geo.slotHeight / 2};
            }
        }
        return null;
    }

    /** Na baia o carro fica na vertical; no corredor vira para onde vai. */
    private static double headingOf(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Math.PI / 2 : -Math.PI / 2;
        }
        return 0;
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    /** Arranca e trava suavemente: lento no início e no fim, rápido no meio. */
    private static double ease(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        return clamped * clamped * (3 - 2 * clamped);
    }

    /** A legenda do movimento, no topo do corredor. */
    private void paintCaption(Graphics2D g, Geometry geo) {
        String hint = Texts.get("anim.skip");
        g.setFont(baseFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        Font small = baseFont().deriveFont(Font.PLAIN, 11f);
        FontMetrics sm = g.getFontMetrics(small);
        double width = fm.stringWidth(caption) + 16 + sm.stringWidth(hint) + 16;
        double x = geo.width / 2 - width / 2;
        double y = geo.mouth + 6;
        g.setColor(withAlpha(Color.BLACK, 170));
        g.fill(new RoundRectangle2D.Double(x, y, width, 24, 12, 12));
        g.setColor(Color.WHITE);
        g.drawString(caption, (float) (x + 10), (float) (y + 16));
        g.setFont(small);
        g.setColor(TEXT_DIM);
        g.drawString(hint, (float) (x + 10 + fm.stringWidth(caption) + 16), (float) (y + 16));
    }

    /** O asfalto todo, por baixo das baias e do corredor. */
    private void paintGround(Graphics2D g, Geometry geo) {
        g.setColor(Theme.ASPHALT);
        g.fill(new RoundRectangle2D.Double(MARGIN / 2.0, MARGIN / 2.0,
                geo.width - MARGIN, geo.legendTop - MARGIN / 2.0, 18, 18));
    }

    /** O corredor: linha amarela ao meio, setas no sentido da circulação e as cancelas. */
    private void paintCorridor(Graphics2D g, Geometry geo) {
        double y = geo.mouth;
        double h = geo.corridorHeight;
        double middle = y + h / 2;
        double left = MARGIN;
        double right = geo.width - MARGIN;

        g.setColor(Theme.CENTER_LINE);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[] {18, 14}, 0));
        g.draw(new Line2D.Double(left + 120, middle, right - 120, middle));

        // Setas da entrada (à esquerda) para a saída (à direita), nas duas faixas.
        g.setColor(withAlpha(Theme.PAINT, 140));
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double size = Math.min(13, h / 6);
        for (double x = left + 190; x < right - 160; x += 170) {
            paintArrow(g, x, y + h * 0.27, size);
            paintArrow(g, x + 85, y + h * 0.73, size);
        }

        paintGate(g, left, y, h, Texts.get("park.entrance"), true);
        paintGate(g, right, y, h, Texts.get("park.exit"), false);
    }

    private static void paintArrow(Graphics2D g, double x, double y, double size) {
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x - size, y - size * 0.6);
        arrow.lineTo(x, y);
        arrow.lineTo(x - size, y + size * 0.6);
        g.draw(arrow);
    }

    /** Uma cancela às riscas vermelhas e brancas, com o letreiro verde ao lado. */
    private void paintGate(Graphics2D g, double edge, double y, double h, String label, boolean entrance) {
        double barWidth = 8;
        double barX = entrance ? edge + 6 : edge - 6 - barWidth;
        double stripe = h / 8;
        for (int i = 0; i < 8; i++) {
            g.setColor(i % 2 == 0 ? GATE_RED : Color.WHITE);
            g.fill(new Rectangle2D.Double(barX, y + i * stripe, barWidth, stripe));
        }

        g.setFont(baseFont().deriveFont(Font.BOLD, 12f));
        FontMetrics fm = g.getFontMetrics();
        double boxWidth = fm.stringWidth(label) + 16;
        double boxX = entrance ? barX + barWidth + 8 : barX - 8 - boxWidth;
        double boxY = y + h / 2 - 12;
        g.setColor(GATE_SIGN);
        g.fill(new RoundRectangle2D.Double(boxX, boxY, boxWidth, 24, 6, 6));
        g.setColor(Color.WHITE);
        g.drawString(label, (float) (boxX + 8), (float) (boxY + 12 + fm.getAscent() / 2.0 - 2));
    }

    // ------------------------------------------------------------------
    // Uma baia
    // ------------------------------------------------------------------

    /**
     * Desenha uma baia na coluna indicada. A boca fica em baixo, junto ao
     * corredor. A posição 0 da pilha (o fundo) é a mais afastada da boca, e
     * o topo fica junto à boca. As baias mais curtas que a mais funda têm um
     * canteiro no fundo.
     */
    private void paintLane(Graphics2D g, Geometry geo, LaneView lane, int column, List<ParkingSession> cars) {
        double x = geo.laneX(column);
        double w = geo.laneWidth;
        double floorTop = geo.laneTop;
        double mouth = geo.mouth;
        double slot = geo.slotHeight;
        int capacity = lane.getCapacity();

        // Piso da baia.
        g.setColor(Theme.LANE_FLOOR);
        g.fill(new Rectangle2D.Double(x, floorTop, w, mouth - floorTop));
        if (lane.isBuffer()) {
            paintHazardFloor(g, x, floorTop, w, mouth - floorTop);
        }

        double used = capacity * slot;
        if (used < mouth - floorTop - 1) {
            paintPlanter(g, x, floorTop, w, mouth - floorTop - used, column + 1);
        }

        // Os lugares livres: só o contorno pintado no chão, tracejado.
        int size = cars.size();
        g.setColor(withAlpha(Theme.PAINT, 55));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                new float[] {3, 3}, 0));
        for (int position = size; position < capacity; position++) {
            g.draw(bay(x, w, slotTop(mouth, capacity, position, slot), slot));
        }

        // Linhas laterais e linha de paragem na boca.
        g.setColor(lane.isBuffer() ? Theme.HAZARD : Theme.PAINT);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Line2D.Double(x, floorTop, x, mouth));
        g.draw(new Line2D.Double(x + w, floorTop, x + w, mouth));
        g.setStroke(new BasicStroke(4f));
        g.draw(new Line2D.Double(x, mouth - 2, x + w, mouth - 2));

        // Os carros, do topo para o fundo: o primeiro tem 0 por cima.
        int depth = 0;
        for (ParkingSession session : cars) {
            int position = size - 1 - depth;
            double top = slotTop(mouth, capacity, position, slot);

            // O lugar pintado à volta do carro, na cor da classe do carro.
            g.setColor(withAlpha(Theme.colorOf(session.getStayClass()), 210));
            g.setStroke(new BasicStroke(2f));
            g.draw(bay(x, w, top, slot));

            Rectangle2D car = carBounds(x, w, top, slot);
            paintCar(g, car, Theme.paintOf(session.getVehicle().getPlate()), depth == 0);
            spots.add(new CarSpot(car.getBounds(), session, depth));
            depth++;
        }

        paintHeader(g, lane, x, w, size);
    }

    /** Onde começa o lugar de uma posição da pilha (0 = fundo, o mais afastado da boca). */
    private static double slotTop(double mouth, int capacity, int position, double slot) {
        return mouth - (capacity - position) * slot;
    }

    /** O rectângulo pintado no chão de um lugar. */
    private static Shape bay(double laneX, double laneWidth, double slotTop, double slot) {
        return new RoundRectangle2D.Double(laneX + 4, slotTop + 2, laneWidth - 8, slot - 4, 6, 6);
    }

    /** Riscas amarelas diagonais, muito suaves, no chão da baia de manobra. */
    private static void paintHazardFloor(Graphics2D g, double x, double y, double w, double h) {
        Shape clip = g.getClip();
        g.clip(new Rectangle2D.Double(x, y, w, h));
        g.setColor(withAlpha(Theme.HAZARD, 38));
        g.setStroke(new BasicStroke(8f));
        for (double offset = -w; offset < h + w; offset += 22) {
            g.draw(new Line2D.Double(x, y + offset, x + w, y + offset + w));
        }
        g.setClip(clip);
    }

    /**
     * Um canteiro com arbustos. A semente (o número da coluna) faz cada
     * canteiro diferente dos outros, mas sempre igual a si próprio de um
     * desenho para o outro.
     */
    private static void paintPlanter(Graphics2D g, double x, double y, double w, double h, int seed) {
        g.setColor(KERB);
        g.fill(new Rectangle2D.Double(x, y, w, h));
        g.setColor(Theme.PLANTER);
        g.fill(new RoundRectangle2D.Double(x + 3, y + 3, w - 6, h - 6, 8, 8));

        int state = seed;
        int bushes = (int) Math.max(1, (w * h) / 500);
        for (int i = 0; i < bushes; i++) {
            state = state * 1103515245 + 12345;
            double r = Math.min(w * 0.22, 5 + Math.abs(state % 5));
            double bx = x + 4 + Math.abs(state % 1000) / 1000.0 * Math.max(1, w - 8 - 2 * r);
            state = state * 1103515245 + 12345;
            double by = y + 4 + Math.abs(state % 1000) / 1000.0 * Math.max(1, h - 8 - 2 * r);
            g.setColor(Theme.BUSH);
            g.fill(new Ellipse2D.Double(bx, by, r * 2, r * 2));
            g.setColor(withAlpha(Color.WHITE, 30));
            g.fill(new Ellipse2D.Double(bx + r * 0.4, by + r * 0.3, r, r * 0.8));
        }
    }

    /**
     * A placa no fundo da baia: faixa com a cor da classe, código, ocupação
     * e uma barra que enche. Fica com contorno vermelho quando a baia está
     * cheia.
     */
    private void paintHeader(Graphics2D g, LaneView lane, double x, double w, int size) {
        double y = MARGIN;
        RoundRectangle2D plate = new RoundRectangle2D.Double(x, y, w, HEADER - 6, 7, 7);
        boolean full = size >= lane.getCapacity();
        Color tint = lane.isBuffer() ? Theme.HAZARD : Theme.colorOf(lane.getStayClass());

        g.setColor(PLATE_PANEL);
        g.fill(plate);
        Shape clip = g.getClip();
        g.clip(plate);
        g.setColor(tint);
        g.fill(new Rectangle2D.Double(x, y, w, 4));
        g.setClip(clip);
        if (full) {
            g.setColor(Theme.FULL);
            g.setStroke(new BasicStroke(2f));
            g.draw(plate);
        }

        g.setFont(baseFont().deriveFont(Font.BOLD, (float) Math.min(13, w * 0.3)));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.WHITE);
        String code = lane.getCode();
        g.drawString(code, (float) (x + w / 2 - fm.stringWidth(code) / 2.0), (float) (y + 6 + fm.getAscent()));

        g.setFont(baseFont().deriveFont(Font.PLAIN, (float) Math.min(10, w * 0.24)));
        FontMetrics sm = g.getFontMetrics();
        String count = lane.isBuffer() ? Texts.get("park.buffer") : size + "/" + lane.getCapacity();
        g.setColor(lane.isBuffer() ? Theme.HAZARD : (full ? Theme.FULL : TEXT_DIM));
        g.drawString(count, (float) (x + w / 2 - sm.stringWidth(count) / 2.0),
                (float) (y + 6 + fm.getAscent() + sm.getAscent() + 1));

        if (!lane.isBuffer()) {
            double barX = x + 5;
            double barY = y + HEADER - 14;
            double barW = w - 10;
            g.setColor(BAR_TRACK);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW, 3, 3, 3));
            g.setColor(full ? Theme.FULL : tint);
            g.fill(new RoundRectangle2D.Double(barX, barY, barW * size / lane.getCapacity(), 3, 3, 3));
        }
    }

    // ------------------------------------------------------------------
    // Um carro
    // ------------------------------------------------------------------

    /**
     * O espaço do carro dentro do lugar: comprido na vertical, com a largura
     * de um carro verdadeiro (pouco menos de metade do comprimento).
     */
    private static Rectangle2D carBounds(double laneX, double laneWidth, double slotTop, double slot) {
        double length = slot * 0.82;
        double width = Math.min(laneWidth * 0.66, length * 0.47);
        return new Rectangle2D.Double(laneX + (laneWidth - width) / 2, slotTop + (slot - length) / 2,
                width, length);
    }

    /**
     * Um carro visto de cima, na vertical, de frente para o fundo da baia:
     * sombra, pneus, carroçaria com volume, capô, pára-brisas, tejadilho,
     * vidros laterais, óculo traseiro, espelhos, faróis, farolins e a chapa
     * de trás. Em lugares muito pequenos fica só a carroçaria e os vidros.
     */
    private void paintCar(Graphics2D g, Rectangle2D r, Color paint, boolean onTop) {
        double x = r.getX();
        double y = r.getY();
        double w = r.getWidth();
        double l = r.getHeight();
        double cx = x + w / 2;
        boolean detailed = w >= 12;
        boolean dark = brightness(paint) < 0.35;

        // Sombra suave: várias camadas transparentes ligeiramente deslocadas.
        for (int i = 0; i < 4; i++) {
            g.setColor(withAlpha(Color.BLACK, 26));
            g.fill(new RoundRectangle2D.Double(x + 0.8 + i * 0.6, y + 1.5 + i * 0.7, w, l, w * 0.75, w * 0.85));
        }

        // Pneus, a espreitar dos lados.
        if (detailed) {
            g.setColor(Theme.TYRE);
            double tyreW = w * 0.13;
            double tyreH = l * 0.15;
            double[] axles = {y + l * 0.16, y + l * 0.71};
            for (double ay : axles) {
                g.fill(new RoundRectangle2D.Double(x - tyreW * 0.45, ay, tyreW, tyreH, 2, 2));
                g.fill(new RoundRectangle2D.Double(x + w - tyreW * 0.55, ay, tyreW, tyreH, 2, 2));
            }
        }

        // Carroçaria, com volume: mais escura nos lados, mais clara ao meio.
        RoundRectangle2D body = new RoundRectangle2D.Double(x, y, w, l, w * 0.75, w * 0.85);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(x, y), new Point2D.Double(x + w, y),
                new float[] {0f, 0.3f, 0.6f, 1f},
                new Color[] {darker(paint, 0.32f), lighter(paint, dark ? 0.22f : 0.14f),
                    lighter(paint, dark ? 0.12f : 0.06f), darker(paint, 0.36f)}));
        g.fill(body);
        g.setColor(withAlpha(darker(paint, 0.55f), 160));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(body);

        // Capô: um brilho suave na frente.
        g.setPaint(new GradientPaint((float) x, (float) y, withAlpha(Color.WHITE, dark ? 55 : 70),
                (float) x, (float) (y + l * 0.26), withAlpha(Color.WHITE, 0)));
        g.fill(new RoundRectangle2D.Double(x + w * 0.12, y + 1, w * 0.76, l * 0.24, w * 0.6, w * 0.5));

        // Pára-brisas: curvo em cima, mais largo em baixo.
        Path2D windscreen = new Path2D.Double();
        windscreen.moveTo(x + w * 0.13, y + l * 0.37);
        windscreen.lineTo(x + w * 0.87, y + l * 0.37);
        windscreen.lineTo(x + w * 0.80, y + l * 0.26);
        windscreen.quadTo(cx, y + l * 0.22, x + w * 0.20, y + l * 0.26);
        windscreen.closePath();
        paintGlass(g, windscreen, y + l * 0.22, y + l * 0.37);

        // Tejadilho.
        RoundRectangle2D roof = new RoundRectangle2D.Double(x + w * 0.16, y + l * 0.37, w * 0.68, l * 0.33,
                w * 0.3, w * 0.3);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(x, y), new Point2D.Double(x + w, y),
                new float[] {0f, 0.45f, 1f},
                new Color[] {darker(paint, 0.12f), lighter(paint, dark ? 0.2f : 0.1f), darker(paint, 0.18f)}));
        g.fill(roof);

        // Óculo traseiro: curvo em baixo.
        Path2D rear = new Path2D.Double();
        rear.moveTo(x + w * 0.16, y + l * 0.70);
        rear.lineTo(x + w * 0.84, y + l * 0.70);
        rear.lineTo(x + w * 0.79, y + l * 0.80);
        rear.quadTo(cx, y + l * 0.83, x + w * 0.21, y + l * 0.80);
        rear.closePath();
        paintGlass(g, rear, y + l * 0.70, y + l * 0.83);

        if (detailed) {
            // Vidros laterais, entre o pára-brisas e o óculo.
            g.setColor(Theme.GLASS);
            g.fill(new RoundRectangle2D.Double(x + w * 0.06, y + l * 0.38, w * 0.08, l * 0.31, 3, 3));
            g.fill(new RoundRectangle2D.Double(x + w * 0.86, y + l * 0.38, w * 0.08, l * 0.31, 3, 3));

            // Espelhos retrovisores.
            g.setColor(darker(paint, 0.15f));
            g.fill(new Ellipse2D.Double(x - w * 0.11, y + l * 0.32, w * 0.16, l * 0.055));
            g.fill(new Ellipse2D.Double(x + w * 0.95, y + l * 0.32, w * 0.16, l * 0.055));

            // Faróis à frente, farolins atrás.
            g.setColor(HEADLIGHT);
            g.fill(new RoundRectangle2D.Double(x + w * 0.1, y + 0.8, w * 0.22, l * 0.04, 3, 3));
            g.fill(new RoundRectangle2D.Double(x + w * 0.68, y + 0.8, w * 0.22, l * 0.04, 3, 3));
            g.setColor(TAILLIGHT);
            g.fill(new RoundRectangle2D.Double(x + w * 0.08, y + l - l * 0.04 - 0.8, w * 0.24, l * 0.04, 3, 3));
            g.fill(new RoundRectangle2D.Double(x + w * 0.68, y + l - l * 0.04 - 0.8, w * 0.24, l * 0.04, 3, 3));

            // Chapa de matrícula atrás.
            g.setColor(new Color(0xF8F9FA));
            g.fill(new Rectangle2D.Double(cx - w * 0.17, y + l - l * 0.035, w * 0.34, Math.max(1.5, l * 0.03)));
        }

        if (onTop) {
            // O carro do topo da pilha, o único que sai sem manobras, leva um contorno claro.
            g.setColor(withAlpha(Color.WHITE, 150));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new RoundRectangle2D.Double(x - 2, y - 2, w + 4, l + 4, w * 0.8, w * 0.9));
        }
    }

    /** Um vidro escuro com um reflexo em diagonal. */
    private static void paintGlass(Graphics2D g, Shape glass, double top, double bottom) {
        g.setPaint(new GradientPaint(0f, (float) top, new Color(0x3A4E63),
                0f, (float) bottom, Theme.GLASS));
        g.fill(glass);
        Shape clip = g.getClip();
        g.clip(glass);
        Rectangle2D b = glass.getBounds2D();
        g.setColor(withAlpha(Color.WHITE, 60));
        g.setStroke(new BasicStroke((float) Math.max(1, b.getWidth() * 0.12)));
        g.draw(new Line2D.Double(b.getX() + b.getWidth() * 0.15, b.getMaxY(),
                b.getX() + b.getWidth() * 0.45, b.getY()));
        g.setClip(clip);
    }

    private static void paintHighlight(Graphics2D g, Rectangle r) {
        RoundRectangle2D ring = new RoundRectangle2D.Double(r.x - 4, r.y - 4, r.width + 8, r.height + 8, 12, 12);
        g.setColor(withAlpha(Color.WHITE, 60));
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(ring);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.draw(ring);
    }

    // ------------------------------------------------------------------
    // Legenda
    // ------------------------------------------------------------------

    /** A legenda, numa faixa por baixo do corredor. */
    private void paintLegend(Graphics2D g, Geometry geo) {
        double y = geo.legendTop + 8;
        double x = MARGIN;
        g.setFont(baseFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        float baseline = (float) (y + 11);

        for (StayClass stayClass : StayClass.values()) {
            g.setColor(Theme.colorOf(stayClass));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(x, y, 12, 14, 4, 4));
            String label = Texts.get("stay." + stayClass.name());
            g.setColor(TEXT);
            g.drawString(label, (float) (x + 18), baseline);
            x += 18 + fm.stringWidth(label) + 22;
        }

        g.setColor(Theme.HAZARD);
        g.draw(new RoundRectangle2D.Double(x, y, 12, 14, 4, 4));
        String buffer = Texts.get("park.legend.buffer");
        g.setColor(TEXT);
        g.drawString(buffer, (float) (x + 18), baseline);
        x += 18 + fm.stringWidth(buffer) + 22;

        g.setColor(withAlpha(Color.WHITE, 150));
        g.setStroke(new BasicStroke(1.4f));
        g.draw(new RoundRectangle2D.Double(x, y, 12, 14, 5, 5));
        String top = Texts.get("park.legend.top");
        g.setColor(TEXT);
        g.drawString(top, (float) (x + 18), baseline);
        x += 18 + fm.stringWidth(top) + 22;

        // A dica do rato vai encostada à direita, só se couber sem tapar a legenda.
        String hint = Texts.get("park.legend.hint");
        double hintX = geo.width - MARGIN - fm.stringWidth(hint);
        if (hintX >= x) {
            g.setColor(TEXT_DIM);
            g.drawString(hint, (float) hintX, baseline);
        }
    }

    // ------------------------------------------------------------------
    // Rato e dicas
    // ------------------------------------------------------------------

    /** Muda o carro realçado e redesenha só a zona que mudou. */
    private void hover(CarSpot spot) {
        if (spot == hovered) {
            return;
        }
        Rectangle dirty = union(hovered, spot);
        hovered = spot;
        if (dirty != null) {
            dirty.grow(10, 10);
            repaint(dirty);
        }
    }

    private CarSpot spotAt(Point point) {
        for (CarSpot spot : spots) {
            if (spot.bounds.contains(point)) {
                return spot;
            }
        }
        return null;
    }

    private static String tooltip(CarSpot spot) {
        ParkingSession session = spot.session;
        LocalDateTime entry = session.getEntryTime();
        String entered;
        if (entry.toLocalDate().equals(LocalDate.now())) {
            entered = Texts.format("park.entered.today", entry.format(TIME));
        } else {
            entered = Texts.format("park.entered.before", entry.format(DAY), entry.format(TIME));
        }
        String depth;
        if (spot.depth == 0) {
            depth = Texts.get("park.depth.top");
        } else {
            depth = Texts.format("park.depth", String.valueOf(spot.depth), String.valueOf(2 * spot.depth));
        }
        return Texts.format("park.tooltip", session.getVehicle().getPlate(),
                String.valueOf(session.getTicketNumber()),
                Texts.get("stay." + session.getStayClass().name()), entered, depth,
                Texts.get("vehicle." + session.getVehicle().getType().name()));
    }

    private static Rectangle union(CarSpot a, CarSpot b) {
        if (a == null && b == null) {
            return null;
        }
        if (a == null) {
            return new Rectangle(b.bounds);
        }
        if (b == null) {
            return new Rectangle(a.bounds);
        }
        return a.bounds.union(b.bounds);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private static Font baseFont() {
        Font font = UIManager.getFont("Label.font");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color lighter(Color color, float amount) {
        return new Color(mix(color.getRed(), 255, amount), mix(color.getGreen(), 255, amount),
                mix(color.getBlue(), 255, amount));
    }

    private static Color darker(Color color, float amount) {
        return new Color(mix(color.getRed(), 0, amount), mix(color.getGreen(), 0, amount),
                mix(color.getBlue(), 0, amount));
    }

    private static int mix(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    /** O brilho de uma cor, de 0 (preto) a 1 (branco). */
    private static double brightness(Color color) {
        return (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
    }

    /** Um carro desenhado: onde ficou no ecrã, de quem é e quantos tem por cima. */
    private static final class CarSpot {
        private final Rectangle bounds;
        private final ParkingSession session;
        private final int depth;

        private CarSpot(Rectangle bounds, ParkingSession session, int depth) {
            this.bounds = bounds;
            this.session = session;
            this.depth = depth;
        }
    }

    /**
     * As medidas de um desenho, calculadas do tamanho do painel: largura das
     * colunas e das baias, onde começam as baias, onde fica a boca (o limite
     * com o corredor), a altura do corredor e de cada lugar.
     */
    private static final class Geometry {
        private final double width;
        private final double columnWidth;
        private final double laneWidth;
        private final double laneTop;
        private final double corridorHeight;
        private final double mouth;
        private final double legendTop;
        private final double slotHeight;

        private Geometry(double width, double height, int columns, int deepest) {
            this.width = width;
            this.columnWidth = (width - 2 * MARGIN) / columns;
            this.laneWidth = columnWidth * LANE_SHARE;
            this.laneTop = MARGIN + HEADER;
            this.corridorHeight = Math.max(64, Math.min(96, height * 0.13));
            this.legendTop = height - MARGIN - LEGEND;
            this.mouth = legendTop - corridorHeight;
            this.slotHeight = (mouth - laneTop) / deepest;
        }

        /** Onde começa a baia dentro da sua coluna. */
        private double laneX(int column) {
            return MARGIN + column * columnWidth + (columnWidth - laneWidth) / 2;
        }
    }
}