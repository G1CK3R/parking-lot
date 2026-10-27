package mz.uem.eda.parking.ui.dialogs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.JComponent;
import javax.swing.UIManager;

import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O gráfico da ocupação ao longo do dia (RF19): a ocupação média de cada
 * hora, em percentagem dos lugares, das 6h à meia-noite, com a hora mais
 * cheia marcada. Mostra as horas de pico do parque.
 *
 * <p>Só desenha os números que recebe em {@link #setData}. Estende
 * {@code JComponent} porque desenha à mão; o
 * {@code @SuppressWarnings("serial")} cala o aviso de serialização, que não
 * se aplica.
 */
@SuppressWarnings("serial")
final class OccupancyChart extends JComponent {

    private static final Color LINE = Theme.FULL_DAY_STAY;
    private static final Color AREA = new Color(0x3D, 0x8B, 0xD9, 50);
    private static final int MARGIN_LEFT = 44;
    private static final int MARGIN_RIGHT = 16;
    private static final int MARGIN_TOP = 40;
    private static final int MARGIN_BOTTOM = 34;

    private int fromHour = 6;
    private int toHour = 23;
    /** A ocupação média de cada hora do relógio, em percentagem; vazio sem dados. */
    private double[] hourly = new double[0];
    private int busiest = -1;
    private String caption = "";

    OccupancyChart() {
        setPreferredSize(new Dimension(520, 260));
    }

    /**
     * @param occupancy a ocupação média de cada hora do relógio (0 a 23), em percentagem
     * @param first a primeira hora a mostrar
     * @param last a última hora a mostrar
     * @param busiestHour a hora mais cheia, marcada com um ponto
     * @param subtitle o texto por baixo do título (que combinação é)
     */
    void setData(double[] occupancy, int first, int last, int busiestHour, String subtitle) {
        this.hourly = occupancy;
        this.fromHour = first;
        this.toHour = last;
        this.busiest = busiestHour;
        this.caption = subtitle;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(UIManager.getColor("Panel.background"));
            g.fillRect(0, 0, getWidth(), getHeight());

            Font base = getFont();
            g.setFont(base.deriveFont(Font.BOLD, 13f));
            g.setColor(UIManager.getColor("Label.foreground"));
            g.drawString(Texts.get("sim.chart.occupancy"), MARGIN_LEFT - 30, 18);
            g.setFont(base.deriveFont(11f));
            g.setColor(Theme.MUTED);
            FontMetrics cm = g.getFontMetrics();
            g.drawString(caption, getWidth() - MARGIN_RIGHT - cm.stringWidth(caption), 18);

            if (hourly.length == 0) {
                String text = Texts.get("sim.chart.empty");
                g.setFont(base.deriveFont(12f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
                return;
            }
            paintLine(g);
        } finally {
            g.dispose();
        }
    }

    private void paintLine(Graphics2D g) {
        int left = MARGIN_LEFT;
        int right = getWidth() - MARGIN_RIGHT;
        int top = MARGIN_TOP;
        int bottom = getHeight() - MARGIN_BOTTOM;
        Font small = getFont().deriveFont(11f);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        // Grelha de 25 em 25 %.
        for (int level = 0; level <= 100; level += 25) {
            int y = (int) Math.round(yOf(level, top, bottom));
            g.setColor(new Color(0, 0, 0, level == 0 ? 90 : 25));
            g.drawLine(left, y, right, y);
            g.setColor(Theme.MUTED);
            String label = level + " %";
            g.drawString(label, left - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }

        // Área e linha, um ponto a meio de cada hora.
        Path2D.Double line = new Path2D.Double();
        Path2D.Double area = new Path2D.Double();
        area.moveTo(xOf(fromHour, left, right), bottom);
        for (int hour = fromHour; hour <= toHour; hour++) {
            double x = xOf(hour, left, right);
            double y = yOf(hourly[hour], top, bottom);
            if (hour == fromHour) {
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
            area.lineTo(x, y);
        }
        area.lineTo(xOf(toHour, left, right), bottom);
        area.closePath();
        g.setColor(AREA);
        g.fill(area);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        // As horas por baixo, de duas em duas.
        g.setColor(UIManager.getColor("Label.foreground"));
        for (int hour = fromHour; hour <= toHour; hour += 2) {
            String label = Texts.format("sim.chart.hour", String.valueOf(hour));
            int x = (int) xOf(hour, left, right);
            g.drawString(label, x - fm.stringWidth(label) / 2, bottom + fm.getAscent() + 6);
        }

        // A hora mais cheia.
        if (busiest >= fromHour && busiest <= toHour) {
            int x = (int) xOf(busiest, left, right);
            int y = (int) yOf(hourly[busiest], top, bottom);
            g.setColor(Theme.FULL);
            g.fillOval(x - 5, y - 5, 10, 10);
            String label = Texts.format("sim.chart.busiest", String.valueOf(busiest), Texts.number(hourly[busiest], 0));
            g.setFont(small.deriveFont(Font.BOLD));
            FontMetrics bm = g.getFontMetrics();
            int labelX = Math.min(Math.max(left, x - bm.stringWidth(label) / 2), right - bm.stringWidth(label));
            g.drawString(label, labelX, y - 10);
        }
    }

    private double xOf(int hour, int left, int right) {
        return left + (right - left) * (hour - fromHour) / (double) Math.max(1, toHour - fromHour);
    }

    private static double yOf(double percent, int top, int bottom) {
        return bottom - (bottom - top) * percent / 100.0;
    }
}
