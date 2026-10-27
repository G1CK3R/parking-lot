package mz.uem.eda.parking.ui.dialogs.admin;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.reports.HistoryEntry;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.ReportParts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O histórico das estadias, só para o administrador (RF20): cada estadia
 * com o tipo de viatura, a entrada, a saída, o valor, a multa, o método e quem cobrou, com
 * filtros por período de entrada, por matrícula, por operador e por estado
 * (ainda no parque ou já saiu).
 *
 * <p>O histórico pode exportar-se para CSV, para abrir no Excel, e
 * imprimir-se com o diálogo de impressão do sistema, que também grava em
 * PDF («Microsoft Print to PDF» no Windows, «Guardar como PDF» no macOS,
 * «Imprimir para ficheiro» no Linux). A impressão é a da própria tabela do
 * Swing ({@link JTable#print}), ajustada à largura da página.
 *
 * <p>A filtragem e a ordenação são da {@code ParkingDesk}
 * ({@link ParkingDesk#history}): uma busca sequencial pelos pagamentos e
 * pelos carros estacionados, e o radix sort pela hora de entrada.
 */
public final class HistoryDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JComboBox<String> period = ReportParts.periodChoice();
    private final JLabel range = new JLabel();
    private final JTextField plate = new JTextField(10);
    private final JComboBox<String> operator = new JComboBox<>();
    private final JCheckBox inPark = new JCheckBox(Texts.get("history.inPark"), true);
    private final JCheckBox left = new JCheckBox(Texts.get("history.left"), true);
    private final DefaultTableModel table = ReportParts.model("report.column.ticket", "report.column.plate",
            "report.column.type", "report.column.lane", "report.column.entry", "report.column.exit", "report.column.stay",
            "report.column.amount", "report.column.fee", "report.column.method", "report.column.operator",
            "report.column.state");
    private final JScrollPane tableScroll = ReportParts.table(table, 0, 6, 7, 8);
    private final JLabel note = ReportParts.note();
    private Iterable<HistoryEntry> entries;
    private LocalDate[] days;

    private HistoryDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("history.title"), Dialog.ModalityType.APPLICATION_MODAL);

        operator.addItem(Texts.get("history.all"));
        for (User user : desk.users()) {
            operator.addItem(user.getUsername());
        }

        ActionListener reload = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                load();
            }
        };
        period.addActionListener(reload);
        operator.addActionListener(reload);
        inPark.addActionListener(reload);
        left.addActionListener(reload);
        plate.addActionListener(reload);
        JButton filter = new JButton(Texts.get("exit.find"));
        filter.addActionListener(reload);

        JButton export = new JButton(Texts.get("history.export"));
        export.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportCsv();
            }
        });
        JButton print = new JButton(Texts.get("history.print"));
        print.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                print();
            }
        });
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        ReportParts.widths(tableScroll, 50, 95, 75, 45, 115, 115, 85, 100, 80, 85, 80, 75);

        JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        first.add(new JLabel(Texts.get("history.period")));
        first.add(period);
        first.add(range);
        JPanel second = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        second.add(new JLabel(Texts.get("history.plate")));
        second.add(plate);
        second.add(filter);
        second.add(new JLabel(Texts.get("history.operator")));
        second.add(operator);
        second.add(inPark);
        second.add(left);
        JPanel filters = new JPanel(new BorderLayout(0, Theme.GAP));
        filters.add(first, BorderLayout.NORTH);
        filters.add(second, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.add(export);
        buttons.add(print);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(filters, BorderLayout.NORTH);
        content.add(tableScroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(1220, 600);
        dialog.setLocationRelativeTo(owner);
        load();
    }

    /**
     * Mostra o histórico até o administrador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     */
    public static void show(Window owner, ParkingDesk desk) {
        new HistoryDialog(owner, desk).dialog.setVisible(true);
    }

    /** Pede à {@code ParkingDesk} as estadias com os filtros escolhidos e mostra-as. */
    private void load() {
        days = ReportParts.period(period.getSelectedIndex(), LocalDate.now());
        range.setText(ReportParts.rangeText(days[0], days[1]));
        String chosenOperator = operator.getSelectedIndex() <= 0 ? null : (String) operator.getSelectedItem();
        entries = desk.history(days[0].atStartOfDay(), days[1].atStartOfDay(), plate.getText(), chosenOperator,
                inPark.isSelected(), left.isSelected());

        LocalDateTime now = LocalDateTime.now();
        table.setRowCount(0);
        for (HistoryEntry entry : entries) {
            table.addRow(row(entry, now));
        }
        note.setText(Texts.format("history.count", String.valueOf(table.getRowCount())));
    }

    /** As colunas de uma estadia, já escritas para o ecrã. */
    private static Object[] row(HistoryEntry entry, LocalDateTime now) {
        ParkingSession session = entry.getSession();
        Payment payment = entry.getPayment();
        LocalDateTime end = entry.isInPark() ? now : session.getExitTime();
        long stayed = Math.max(0, Duration.between(session.getEntryTime(), end).toMinutes());
        return new Object[] { String.valueOf(session.getTicketNumber()), session.getVehicle().getPlate(),
            Texts.get("vehicle." + session.getVehicle().getType().name()), session.getLaneCode(), session.getEntryTime().format(ReportParts.DATE_TIME),
            entry.isInPark() ? "" : session.getExitTime().format(ReportParts.DATE_TIME), Texts.duration(stayed),
            payment == null ? "" : Texts.money(payment.getAmountCents()),
            payment == null || payment.getLostTicketCents() == 0 ? ""
                    : Texts.money(payment.getLostTicketCents()),
            payment == null ? "" : Texts.get("payment." + payment.getMethod().name()),
            payment == null ? "" : payment.getOperator().getUsername(),
            Texts.get(entry.isInPark() ? "history.state.inPark" : "history.state.left") };
    }

    // ------------------------------------------------------------------
    // Exportar e imprimir
    // ------------------------------------------------------------------

    /**
     * Grava as estadias mostradas num ficheiro CSV, com ponto e vírgula
     * entre colunas e a marca de UTF-8 no início, como o
     * {@link ReportsDialog}.
     */
    private void exportCsv() {
        if (table.getRowCount() == 0) {
            JOptionPane.showMessageDialog(dialog, Texts.get("history.export.empty"),
                    Texts.get("history.export"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(Texts.format("history.export.file", days[0].toString(),
                days[1].minusDays(1).toString())));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        int count = 0;
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write('﻿');
            out.write(Texts.get("history.csv.header"));
            out.write("\r\n");
            for (HistoryEntry entry : entries) {
                out.write(csvLine(entry));
                out.write("\r\n");
                count++;
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, Texts.format("report.export.failed", e.getMessage()),
                    Texts.get("history.export"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(dialog,
                Texts.format("history.export.done", String.valueOf(count), file.getAbsolutePath()),
                Texts.get("history.export"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static String csvLine(HistoryEntry entry) {
        ParkingSession session = entry.getSession();
        Payment payment = entry.getPayment();
        String exit = "";
        String minutes = "";
        if (!entry.isInPark()) {
            exit = session.getExitTime().format(ReportParts.DATE_TIME);
            minutes = String.valueOf(Duration.between(session.getEntryTime(), session.getExitTime()).toMinutes());
        }
        String[] fields = { String.valueOf(session.getTicketNumber()), session.getVehicle().getPlate(),
            Texts.get("vehicle." + session.getVehicle().getType().name()), session.getLaneCode(), session.getEntryTime().format(ReportParts.DATE_TIME), exit, minutes,
            payment == null ? "" : Texts.amount(payment.getAmountCents()),
            payment == null ? "" : Texts.amount(payment.getLostTicketCents()),
            payment == null ? "" : Texts.get("payment." + payment.getMethod().name()),
            payment == null ? "" : payment.getOperator().getUsername() };
        return String.join(";", fields);
    }

    /**
     * Imprime a tabela, ajustada à largura da página, com o período em cima
     * e o número da página em baixo. O diálogo de impressão é o do sistema;
     * para ter um PDF escolhe-se a impressora de PDF.
     */
    private void print() {
        MessageFormat header = new MessageFormat(Texts.format("history.print.header",
                ReportParts.rangeText(days[0], days[1])));
        MessageFormat footer = new MessageFormat(Texts.get("history.print.footer"));
        try {
            tableOf().print(JTable.PrintMode.FIT_WIDTH, header, footer);
        } catch (PrinterException e) {
            JOptionPane.showMessageDialog(dialog, Texts.format("history.print.failed", e.getMessage()),
                    Texts.get("history.print"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private JTable tableOf() {
        return (JTable) tableScroll.getViewport().getView();
    }
}
