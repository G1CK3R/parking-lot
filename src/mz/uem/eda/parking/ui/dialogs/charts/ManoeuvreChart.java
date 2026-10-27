package mz.uem.eda.parking.ui.dialogs.charts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.UIManager;

import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O gráfico de barras das manobras por saída: um grupo por política de
 * colocação e, em cada grupo, uma barra por configuração das baias (a real,
 * heterogénea, e a de comparação, homogénea). É o gráfico que mostra a
 * diferença entre as políticas; a mais baixa de cada configuração leva o
 * valor a negrito.
 *
 * <p>Só desenha os números que recebe em {@link #setData}; não calcula nada.
 * Estende {@code JComponent} porque desenha à mão, como o
 * {@code ParkPanel}; o {@code @SuppressWarnings("serial")} cala o aviso de
 * serialização, que não se aplica (o gráfico nunca é gravado).
 */
@SuppressWarnings("serial")
public final class ManoeuvreChart extends JComponent {

    private static final Color[] SERIES = { Theme.FULL_DAY_STAY, new Color(0x9AA0A6) };
    private static final int MARGIN_LEFT = 44;
    private static final int MARGIN_RIGHT = 16;
    private static final int MARGIN_TOP = 40;
    private static final int MARGIN_BOTTOM = 34;

    private String[] groups = new String[0];
    private String[] seriesNames = new String[0];
    /** values[grupo][série]: manobras por saída. */
    private double[][] values = new double[0][0];

    public ManoeuvreChart() {
        setPreferredSize(new Dimension(520, 260));
    }

    /**
     * @param groupLabels o nome de cada política de colocação
     * @param seriesLabels o nome de cada configuração
     * @param data as manobras por saída, {@code data[política][configuração]}
     */
    public void setData(String[] groupLabels, String[] seriesLabels, double[][] data) {
        this.groups = groupLabels;
        this.seriesNames = seriesLabels;
        this.values = data;
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
            paintTitle(g);
            if (values.length == 0) {
                paintEmpty(g);
                return;
            }
            paintBars(g);
        } finally {
            g.dispose();
        }
    }

    private void paintTitle(Graphics2D g) {
        Font base = getFont();
        g.setFont(base.deriveFont(Font.BOLD, 13f));
        g.setColor(UIManager.getColor("Label.foreground"));
        g.drawString(Texts.get("sim.chart.manoeuvres"), MARGIN_LEFT - 30, 18);

        // Legenda, à direita do título.
        g.setFont(base.deriveFont(11f));
        FontMetrics fm = g.getFontMetrics();
        int x = getWidth() - MARGIN_RIGHT;
        for (int s = seriesNames.length - 1; s >= 0; s--) {
            x -= fm.stringWidth(seriesNames[s]);
            g.setColor(UIManager.getColor("Label.foreground"));
            g.drawString(seriesNames[s], x, 18);
            x -= 14;
            g.setColor(SERIES[s]);
            g.fillRoundRect(x, 9, 10, 10, 3, 3);
            x -= 12;
        }
    }

    private void paintEmpty(Graphics2D g) {
        g.setFont(getFont().deriveFont(12f));
        g.setColor(Theme.MUTED);
        String text = Texts.get("sim.chart.empty");
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
    }

    private void paintBars(Graphics2D g) {
        int left = MARGIN_LEFT;
        int right = getWidth() - MARGIN_RIGHT;
        int top = MARGIN_TOP;
        int bottom = getHeight() - MARGIN_BOTTOM;
        int plotHeight = bottom - top;

        // Escala: do zero ao primeiro inteiro acima do maior valor.
        double max = 0;
        for (double[] group : values) {
            for (double v : group) {
                max = Math.max(max, v);
            }
        }
        int ceiling = Math.max(1, (int) Math.ceil(max));

        // Linhas de grelha, uma por manobra.
        Font small = getFont().deriveFont(11f);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();
        int step = ceiling > 8 ? 2 : 1;
        for (int level = 0; level <= ceiling; level += step) {
            int y = bottom - (int) Math.round(plotHeight * level / (double) ceiling);
            g.setColor(new Color(0, 0, 0, level == 0 ? 90 : 25));
            g.drawLine(left, y, right, y);
            g.setColor(Theme.MUTED);
            String label = String.valueOf(level);
            g.drawString(label, left - 8 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
        }

        // As barras.
        int groupWidth = (right - left) / groups.length;
        int barWidth = Math.min(46, (groupWidth - 24) / Math.max(1, seriesNames.length));
        for (int gi = 0; gi < groups.length; gi++) {
            int groupX = left + gi * groupWidth;
            int barsWidth = barWidth * values[gi].length + 6 * (values[gi].length - 1);
            int x = groupX + (groupWidth - barsWidth) / 2;
            for (int s = 0; s < values[gi].length; s++) {
                double v = values[gi][s];
                int h = (int) Math.round(plotHeight * v / ceiling);
                g.setColor(SERIES[s]);
                g.fillRoundRect(x, bottom - h, barWidth, h, 6, 6);
                g.fillRect(x, bottom - Math.min(h, 4), barWidth, Math.min(h, 4));

                String label = Texts.number(v, 2);
                g.setFont(isLowest(gi, s) ? small.deriveFont(Font.BOLD) : small);
                FontMetrics lm = g.getFontMetrics();
                g.setColor(UIManager.getColor("Label.foreground"));
                g.drawString(label, x + (barWidth - lm.stringWidth(label)) / 2, bottom - h - 4);
                x += barWidth + 6;
            }
            g.setFont(small);
            g.setColor(UIManager.getColor("Label.foreground"));
            String name = groups[gi];
            g.drawString(name, groupX + (groupWidth - fm.stringWidth(name)) / 2, bottom + fm.getAscent() + 6);
        }
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(left, bottom, right, bottom);
    }

    /** Diz se a barra é a mais baixa da sua configuração (a melhor política). */
    private boolean isLowest(int group, int series) {
        for (double[] other : values) {
            if (other[series] < values[group][series]) {
                return false;
            }
        }
        return true;
    }
}
