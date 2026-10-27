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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.RevenueLine;
import mz.uem.eda.parking.service.RevenueReport;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O ecrã de relatórios, só para o administrador (RF18): a receita de um
 * período, com o total, o número de cobranças (pagamentos de estadias e
 * avenças vendidas) e a média, e cinco separadores: por dia, por operador,
 * por método de pagamento, a lista dos pagamentos e a lista das avenças. A
 * lista dos pagamentos pode exportar-se para CSV (RF20), para abrir no
 * Excel.
 *
 * <p>O ecrã só escolhe o período e mostra. As contas e as ordenações são da
 * {@code ParkingDesk} ({@link ParkingDesk#revenue}), com o radix sort de
 * dez filas; as notas por baixo das tabelas dizem que algoritmo produziu
 * cada ordem, porque é isso que o trabalho quer mostrar.
 */
public final class ReportsDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JComboBox<String> period = ReportParts.periodChoice();
    private final JLabel range = new JLabel();
    private final JLabel total = new JLabel();
    private final JLabel count = new JLabel();
    private final JLabel average = new JLabel();
    private final JComboBox<String> order = new JComboBox<>();
    private final DefaultTableModel days = ReportParts.model("report.column.day", "report.column.count",
            "report.column.revenue", "report.column.share");
    private final DefaultTableModel operators = ReportParts.model("report.column.operator",
            "report.column.count", "report.column.revenue", "report.column.share");
    private final DefaultTableModel methods = ReportParts.model("report.column.method", "report.column.count",
            "report.column.revenue", "report.column.share");
    private final DefaultTableModel payments = ReportParts.model("report.column.paidAt", "report.column.ticket",
            "report.column.plate", "report.column.lane", "report.column.stay", "report.column.amount",
            "report.column.method", "report.column.operator");
    private final JLabel paymentsNote = ReportParts.note();
    private final DefaultTableModel passes = ReportParts.model("report.column.paidAt", "report.column.plate",
            "report.column.customer", "report.column.month", "report.column.amount", "report.column.method",
            "report.column.operator");
    private final JLabel passesNote = ReportParts.note();
    private RevenueReport report;

    private ReportsDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("report.title"), Dialog.ModalityType.APPLICATION_MODAL);

        period.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                load();
            }
        });
        order.addItem(Texts.get("report.order.time"));
        order.addItem(Texts.get("report.order.amount"));
        order.addItem(Texts.get("report.order.ticket"));
        order.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPayments();
            }
        });

        JButton export = new JButton(Texts.get("report.export"));
        export.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportCsv();
            }
        });
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        // Em cima: o período e o botão de exportar.
        JPanel choose = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        choose.add(new JLabel(Texts.get("report.period")));
        choose.add(period);
        choose.add(range);
        JPanel top = new JPanel(new BorderLayout());
        top.add(choose, BorderLayout.WEST);
        top.add(export, BorderLayout.EAST);

        // Os três números grandes.
        JPanel cards = new JPanel(new GridLayout(1, 3, Theme.PADDING, 0));
        cards.add(ReportParts.card(Texts.get("report.total"), total));
        cards.add(ReportParts.card(Texts.get("report.count"), count));
        cards.add(ReportParts.card(Texts.get("report.average"), average));

        JPanel header = new JPanel(new BorderLayout(0, Theme.PADDING));
        header.add(top, BorderLayout.NORTH);
        header.add(cards, BorderLayout.CENTER);

        // Os separadores.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Texts.get("report.tab.days"), tab(ReportParts.table(days, 1, 2, 3),
                Texts.get("report.note.days")));
        tabs.addTab(Texts.get("report.tab.operators"), tab(ReportParts.table(operators, 1, 2, 3),
                Texts.get("report.note.operators")));
        tabs.addTab(Texts.get("report.tab.methods"), tab(ReportParts.table(methods, 1, 2, 3),
                Texts.get("report.note.methods")));
        JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        orderRow.add(new JLabel(Texts.get("report.order")));
        orderRow.add(order);
        JPanel paymentsTab = new JPanel(new BorderLayout(0, Theme.GAP));
        paymentsTab.setBorder(Theme.padding());
        paymentsTab.add(orderRow, BorderLayout.NORTH);
        JScrollPane paymentsTable = ReportParts.table(payments, 1, 4, 5);
        ReportParts.widths(paymentsTable, 140, 60, 100, 50, 100, 110, 90, 90);
        paymentsTab.add(paymentsTable, BorderLayout.CENTER);
        paymentsTab.add(paymentsNote, BorderLayout.SOUTH);
        tabs.addTab(Texts.get("report.tab.payments"), paymentsTab);
        JPanel passesTab = new JPanel(new BorderLayout(0, Theme.GAP));
        passesTab.setBorder(Theme.padding());
        JScrollPane passesTable = ReportParts.table(passes, 4);
        ReportParts.widths(passesTable, 140, 100, 180, 140, 110, 90, 90);
        passesTab.add(passesTable, BorderLayout.CENTER);
        passesTab.add(passesNote, BorderLayout.SOUTH);
        tabs.addTab(Texts.get("report.tab.passes"), passesTab);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottom.add(close);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(header, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(980, 640);
        dialog.setLocationRelativeTo(owner);
        load();
    }

    /**
     * Mostra o ecrã de relatórios até o administrador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     */
    public static void show(Window owner, ParkingDesk desk) {
        new ReportsDialog(owner, desk).dialog.setVisible(true);
    }

    /** Pede à {@code ParkingDesk} o relatório do período escolhido e mostra-o. */
    private void load() {
        LocalDate[] days = ReportParts.period(period.getSelectedIndex(), LocalDate.now());
        report = desk.revenue(days[0].atStartOfDay(), days[1].atStartOfDay());
        range.setText(ReportParts.rangeText(days[0], days[1]));
        total.setText(Texts.money(report.getTotalCents()));
        count.setText(String.valueOf(report.getCount()));
        average.setText(Texts.money(report.getAverageCents()));

        this.days.setRowCount(0);
        for (RevenueLine<LocalDate> line : report.byDay()) {
            this.days.addRow(row(line.getKey().format(ReportParts.DATE), line));
        }
        operators.setRowCount(0);
        for (RevenueLine<String> line : report.byOperator()) {
            operators.addRow(row(line.getKey(), line));
        }
        methods.setRowCount(0);
        for (RevenueLine<Payment.Method> line : report.byMethod()) {
            methods.addRow(row(Texts.get("payment." + line.getKey().name()), line));
        }
        showPayments();

        passes.setRowCount(0);
        for (MonthlyPass pass : report.passes()) {
            passes.addRow(new Object[] { pass.getPaidAt().format(ReportParts.DATE_TIME), pass.getPlate(),
                pass.getCustomerName(), Texts.month(pass.getMonth()), Texts.money(pass.getAmountCents()),
                Texts.get("payment." + pass.getMethod().name()), pass.getOperator().getUsername() });
        }
        passesNote.setText(Texts.format("report.note.passes", String.valueOf(report.getPassCount()),
                Texts.money(report.getPassTotalCents())));
    }

    private Object[] row(String label, RevenueLine<?> line) {
        return new Object[] { label, String.valueOf(line.getCount()), Texts.money(line.getTotalCents()),
            ReportParts.share(line.getTotalCents(), report.getTotalCents()) };
    }

    /** Enche a lista dos pagamentos pela ordem escolhida. */
    private void showPayments() {
        payments.setRowCount(0);
        for (Payment payment : chosenOrder()) {
            ParkingSession session = payment.getSession();
            long stayed = Duration.between(session.getEntryTime(), session.getExitTime()).toMinutes();
            payments.addRow(new Object[] { payment.getPaidAt().format(ReportParts.DATE_TIME),
                String.valueOf(session.getTicketNumber()), session.getVehicle().getPlate(),
                session.getLaneCode(), Texts.duration(stayed), Texts.money(payment.getAmountCents()),
                Texts.get("payment." + payment.getMethod().name()), payment.getOperator().getUsername() });
        }
        String[] notes = { "report.note.time", "report.note.amount", "report.note.ticket" };
        paymentsNote.setText(Texts.format(notes[order.getSelectedIndex()],
                String.valueOf(report.getPaymentCount())));
    }

    private Iterable<Payment> chosenOrder() {
        switch (order.getSelectedIndex()) {
            case 1:
                return report.paymentsByAmount();
            case 2:
                return report.paymentsByTicket();
            default:
                return report.paymentsByTime();
        }
    }

    /** Um separador com uma tabela e uma nota por baixo. */
    private static JPanel tab(JComponent table, String note) {
        JLabel label = ReportParts.note();
        label.setText(note);
        JPanel panel = new JPanel(new BorderLayout(0, Theme.GAP));
        panel.setBorder(Theme.padding());
        panel.add(table, BorderLayout.CENTER);
        panel.add(label, BorderLayout.SOUTH);
        return panel;
    }

    // ------------------------------------------------------------------
    // Exportar CSV
    // ------------------------------------------------------------------

    /**
     * Grava os pagamentos do período, pela ordem escolhida, num ficheiro
     * CSV. Usa ponto e vírgula entre colunas e vírgula nos valores, como o
     * Excel em português espera, e começa com a marca de UTF-8 para os
     * acentos aparecerem certos.
     */
    private void exportCsv() {
        if (report.getPaymentCount() == 0) {
            JOptionPane.showMessageDialog(dialog, Texts.get("report.export.empty"),
                    Texts.get("report.export"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        String last = report.getTo().toLocalDate().minusDays(1).toString();
        String name = report.getFrom().toLocalDate().equals(ReportParts.BEGINNING)
                ? Texts.format("report.export.file.all", last)
                : Texts.format("report.export.file", report.getFrom().toLocalDate().toString(), last);
        chooser.setSelectedFile(new File(name));
        if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try (Writer out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write('﻿');
            out.write(Texts.get("report.csv.header"));
            out.write("\r\n");
            for (Payment payment : chosenOrder()) {
                out.write(csvLine(payment));
                out.write("\r\n");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, Texts.format("report.export.failed", e.getMessage()),
                    Texts.get("report.export"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(dialog,
                Texts.format("report.export.done", String.valueOf(report.getPaymentCount()),
                        file.getAbsolutePath()),
                Texts.get("report.export"), JOptionPane.INFORMATION_MESSAGE);
    }

    private static String csvLine(Payment payment) {
        ParkingSession session = payment.getSession();
        LocalDateTime entry = session.getEntryTime();
        long stayed = Duration.between(entry, session.getExitTime()).toMinutes();
        String[] fields = { payment.getPaidAt().format(ReportParts.DATE_TIME),
            String.valueOf(session.getTicketNumber()), session.getVehicle().getPlate(), session.getLaneCode(),
            entry.format(ReportParts.DATE_TIME), String.valueOf(stayed), Texts.amount(payment.getAmountCents()),
            Texts.get("payment." + payment.getMethod().name()), payment.getOperator().getUsername() };
        return String.join(";", fields);
    }
}
