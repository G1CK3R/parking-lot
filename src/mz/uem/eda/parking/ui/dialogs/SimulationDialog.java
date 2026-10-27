package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.simulation.ArrivalGenerator;
import mz.uem.eda.parking.simulation.Comparison;
import mz.uem.eda.parking.simulation.Metrics;
import mz.uem.eda.parking.simulation.Simulator;
import mz.uem.eda.parking.simulation.Summary;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O ecrã da simulação, só para o administrador: compara as políticas de
 * colocação e de atendimento em dias simulados, com os números que o
 * relatório do trabalho usa.
 *
 * <p>Em cima escolhem-se os parâmetros do dia (carros por dia, percentagem
 * de preferenciais, erro da duração declarada) e quantos dias simular. A
 * {@link Comparison} corre as 16 combinações com os mesmos dias, e a tabela
 * enche-se à medida que cada uma acaba. Por baixo, dois gráficos: as
 * manobras por saída de cada política de colocação e a ocupação ao longo do
 * dia (RF19). O CSV exportado tem uma linha por dia simulado, no mesmo
 * formato do {@code docs/measurements/simulation.csv}.
 *
 * <p><b>Corre fora da EDT.</b> A comparação demora um ou dois segundos (mais,
 * com muitos dias), por isso corre num {@link SwingWorker}, com uma barra de
 * progresso, e a janela continua a responder. A simulação cria as suas
 * próprias baias, serviços e políticas para cada dia: não toca no parque
 * real, na {@code ParkingDesk} nem na base de dados, e nada é partilhado
 * entre a thread da simulação e a EDT, a não ser os resumos já prontos.
 *
 * <p><b>Excepção ao ADR-032.</b> Este é o único ecrã que não fala com a
 * {@code ParkingDesk}: fala com a camada {@code simulation}, que é uma
 * ferramenta de medição à parte, tal como o {@link Simulator#main}.
 */
public final class SimulationDialog {

    private final JDialog dialog;
    private final JSpinner carsPerDay = new JSpinner(
            new SpinnerNumberModel(Simulator.CARS_PER_DAY, 20, 1000, 10));
    private final JSpinner preferential = new JSpinner(
            new SpinnerNumberModel(Simulator.PREFERENTIAL_PERCENT, 0, 100, 5));
    private final JSpinner estimateError = new JSpinner(
            new SpinnerNumberModel(Simulator.ESTIMATE_ERROR_PERCENT, 0, 100, 5));
    private final JSpinner days = new JSpinner(new SpinnerNumberModel(Simulator.DAYS, 1, 100, 1));
    private final JButton run = new JButton(Texts.get("sim.run"));
    private final JButton export = new JButton(Texts.get("sim.export"));
    private final JProgressBar progress = new JProgressBar();
    private final DefaultTableModel table = ReportParts.model("sim.column.layout", "sim.column.placement",
            "sim.column.service", "sim.column.manoeuvres", "sim.column.withManoeuvres", "sim.column.peak",
            "sim.column.preferentialWait", "sim.column.normalWait", "sim.column.notServed",
            "sim.column.occupancy", "sim.column.busiest");
    private final ManoeuvreChart manoeuvreChart = new ManoeuvreChart();
    private final OccupancyChart occupancyChart = new OccupancyChart();
    private final JLabel conclusion = new JLabel(" ");
    private final List<Summary> results = new ArrayList<>();

    private SimulationDialog(Window owner) {
        this.dialog = new JDialog(owner, Texts.get("sim.title"), Dialog.ModalityType.APPLICATION_MODAL);

        run.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                start();
            }
        });
        export.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportCsv();
            }
        });
        export.setEnabled(false);
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        progress.setStringPainted(true);
        progress.setString(Texts.get("sim.progress.idle"));

        // Em cima: os parâmetros e os botões.
        JPanel parameters = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        parameters.add(new JLabel(Texts.get("sim.cars")));
        parameters.add(carsPerDay);
        parameters.add(new JLabel(Texts.get("sim.preferential")));
        parameters.add(preferential);
        parameters.add(new JLabel(Texts.get("sim.error")));
        parameters.add(estimateError);
        parameters.add(new JLabel(Texts.get("sim.days")));
        parameters.add(days);
        parameters.add(run);
        JPanel top = new JPanel(new BorderLayout(Theme.PADDING, Theme.GAP));
        top.add(parameters, BorderLayout.CENTER);
        top.add(export, BorderLayout.EAST);
        top.add(progress, BorderLayout.SOUTH);

        // Ao centro: a tabela; por baixo, a conclusão e os dois gráficos.
        JScrollPane tableScroll = ReportParts.table(table, 3, 4, 5, 6, 7, 8, 9, 10);
        ReportParts.widths(tableScroll, 130, 140, 120, 115, 100, 80, 110, 110, 75, 75, 85);
        JPanel charts = new JPanel(new GridLayout(1, 2, Theme.PADDING, 0));
        charts.add(manoeuvreChart);
        charts.add(occupancyChart);
        JPanel lower = new JPanel(new BorderLayout(0, Theme.GAP));
        lower.add(conclusion, BorderLayout.NORTH);
        lower.add(charts, BorderLayout.CENTER);
        JPanel middle = new JPanel(new BorderLayout(0, Theme.PADDING));
        middle.add(tableScroll, BorderLayout.CENTER);
        middle.add(lower, BorderLayout.SOUTH);

        JLabel note = ReportParts.note();
        note.setText(Texts.get("sim.note"));
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(top, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(run);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(1240, 740);
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Mostra o ecrã da simulação até o administrador o fechar.
     *
     * @param owner a janela principal
     */
    public static void show(Window owner) {
        new SimulationDialog(owner).dialog.setVisible(true);
    }

    /** Lê os parâmetros e corre a comparação num {@link SwingWorker}. */
    private void start() {
        final Comparison comparison = new Comparison(new ArrivalGenerator((Integer) carsPerDay.getValue(),
                (Integer) preferential.getValue(), (Integer) estimateError.getValue()), (Integer) days.getValue());
        results.clear();
        table.setRowCount(0);
        manoeuvreChart.setData(new String[0], new String[0], new double[0][0]);
        occupancyChart.setData(new double[0], 6, 23, -1, "");
        conclusion.setText(" ");
        setRunning(true);
        progress.setMaximum(comparison.size());
        progress.setValue(0);
        progress.setString(Texts.format("sim.progress", "0", String.valueOf(comparison.size())));

        new SwingWorker<Void, Summary>() {
            @Override
            protected Void doInBackground() {
                // Fora da EDT: cada combinação cria as suas baias e serviços.
                for (int i = 0; i < comparison.size(); i++) {
                    publish(comparison.run(i));
                }
                return null;
            }

            @Override
            protected void process(List<Summary> chunk) {
                // De volta à EDT: junta as combinações que já acabaram.
                for (Summary summary : chunk) {
                    results.add(summary);
                    table.addRow(row(summary));
                }
                progress.setValue(results.size());
                progress.setString(Texts.format("sim.progress", String.valueOf(results.size()),
                        String.valueOf(comparison.size())));
            }

            @Override
            protected void done() {
                setRunning(false);
                try {
                    get();
                } catch (InterruptedException | ExecutionException e) {
                    progress.setString(Texts.get("sim.progress.failed"));
                    JOptionPane.showMessageDialog(dialog, Texts.format("sim.failed", e.getMessage()),
                            Texts.get("sim.title"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                progress.setString(Texts.get("sim.progress.done"));
                export.setEnabled(true);
                showCharts();
            }
        }.execute();
    }

    private void setRunning(boolean running) {
        run.setEnabled(!running);
        export.setEnabled(false);
        carsPerDay.setEnabled(!running);
        preferential.setEnabled(!running);
        estimateError.setEnabled(!running);
        days.setEnabled(!running);
    }

    private static Object[] row(Summary s) {
        return new Object[] { layoutName(s.getLayout()), policyName(s.getPlacement()), policyName(s.getService()),
            Texts.number(s.getAverageManoeuvres(), 2), Texts.number(s.getPercentExitsWithManoeuvres(), 0) + " %",
            Texts.number(s.getPeakManoeuvreMinutes(), 0),
            Texts.format("sim.wait", Texts.number(s.getPreferentialAverageWait(), 0),
                    String.valueOf(s.getPreferentialMaxWait())),
            Texts.format("sim.wait", Texts.number(s.getNormalAverageWait(), 0), String.valueOf(s.getNormalMaxWait())),
            String.valueOf(s.getNotServed()), Texts.number(s.getAverageOccupancyPercent(), 0) + " %",
            Texts.format("sim.chart.hour", String.valueOf(s.getBusiestHour())) };
    }

    /**
     * Os gráficos e a frase de conclusão, quando a comparação acaba. As
     * manobras de cada política são a média das duas políticas de
     * atendimento, que quase não mudam as manobras.
     */
    private void showCharts() {
        String[] groups = new String[Comparison.PLACEMENTS];
        double[][] values = new double[Comparison.PLACEMENTS][Comparison.LAYOUTS];
        for (int i = 0; i < results.size(); i++) {
            Summary s = results.get(i);
            int layout = i / (Comparison.PLACEMENTS * Comparison.SERVICES);
            int placement = i / Comparison.SERVICES % Comparison.PLACEMENTS;
            groups[placement] = policyName(s.getPlacement());
            values[placement][layout] += s.getAverageManoeuvres() / Comparison.SERVICES;
        }
        manoeuvreChart.setData(groups,
                new String[] { layoutName(Comparison.HETEROGENEOUS), layoutName(Comparison.HOMOGENEOUS) }, values);

        // A ocupação não depende da política de colocação: mostra a primeira combinação.
        Summary first = results.get(0);
        double[] hourly = new double[24];
        for (int hour = 0; hour < 24; hour++) {
            hourly[hour] = first.getHourlyOccupancy(hour);
        }
        occupancyChart.setData(hourly, Simulator.DAY_START / 60, Simulator.DAY_END / 60 - 1,
                first.getBusiestHour(), Texts.format("sim.chart.occupancy.caption",
                        Texts.number(first.getAverageOccupancyPercent(), 0), String.valueOf(first.getPeakOccupied()),
                        String.valueOf(first.getCapacity())));

        // A melhor política na configuração real, contra a linha de base (a primeira).
        int best = 0;
        for (int p = 1; p < Comparison.PLACEMENTS; p++) {
            if (values[p][0] < values[best][0]) {
                best = p;
            }
        }
        double saving = 100.0 * (values[0][0] - values[best][0]) / values[0][0];
        conclusion.setText(Texts.format("sim.conclusion", groups[best], Texts.number(values[best][0], 2),
                Texts.number(saving, 0), groups[0]));
    }

    private static String layoutName(String layout) {
        return Texts.get(Comparison.HETEROGENEOUS.equals(layout) ? "sim.layout.heterogeneous"
                : "sim.layout.homogeneous");
    }

    /** O nome da política em português; a rotação leva o k. */
    private static String policyName(String name) {
        if (name.startsWith("RoundRobin")) {
            return Texts.format("policy.RoundRobin", String.valueOf(Comparison.PREFERENTIAL_TURNS));
        }
        return Texts.get("policy." + name);
    }

    /**
     * Grava uma linha por dia simulado, no formato do
     * {@code docs/measurements/simulation.csv}: vírgulas entre colunas e
     * ponto decimal, para as ferramentas de análise.
     */
    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(Texts.get("sim.export.file")));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        int lines = 0;
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write(Metrics.csvHeader());
            out.write("\n");
            for (Summary summary : results) {
                for (Metrics day : summary.days()) {
                    out.write(day.toCsvRow());
                    out.write("\n");
                    lines++;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, Texts.format("report.export.failed", e.getMessage()),
                    Texts.get("sim.export"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(dialog, Texts.format("sim.export.done", String.valueOf(lines),
                file.getAbsolutePath()), Texts.get("sim.export"), JOptionPane.INFORMATION_MESSAGE);
    }
}
