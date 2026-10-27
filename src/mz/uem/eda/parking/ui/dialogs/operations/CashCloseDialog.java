package mz.uem.eda.parking.ui.dialogs.operations;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.Charge;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.reports.CashClose;
import mz.uem.eda.parking.service.reports.RevenueLine;
import mz.uem.eda.parking.service.reports.RevenueReport;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.ReportParts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O fecho de caixa do turno (RF21), para qualquer utilizador: o que ele
 * próprio cobrou desde que entrou no programa.
 *
 * <p>Em cima, o total e o número de cobranças: pagamentos de estadias e
 * avenças vendidas. À esquerda, o total por método de pagamento, com todos
 * os métodos sempre presentes (também os que ficaram a zero), porque é com
 * esta tabela que se confere a gaveta, o M-Pesa, o e-Mola e o terminal do
 * cartão; a linha da avença conta as saídas cobertas por avença, que não
 * pagaram nada. À direita, os valores do turno do maior para o menor,
 * ordenados pela {@code ParkingDesk} com duas pilhas; a nota por baixo diz
 * quantas comparações isso custou.
 */
public final class CashCloseDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final LocalDateTime shiftStart;
    private final JLabel shift = new JLabel();
    private final JLabel total = new JLabel();
    private final JLabel count = new JLabel();
    private final DefaultTableModel methods = ReportParts.model("report.column.method", "report.column.count",
            "report.column.revenue");
    private final DefaultTableModel payments = ReportParts.model("report.column.amount", "report.column.ticket",
            "report.column.plate", "report.column.time", "report.column.method");
    private final JLabel note = ReportParts.note();

    private CashCloseDialog(Window owner, ParkingDesk desk, LocalDateTime shiftStart) {
        this.desk = desk;
        this.shiftStart = shiftStart;
        this.dialog = new JDialog(owner, Texts.get("cash.title"), Dialog.ModalityType.APPLICATION_MODAL);

        JButton refresh = new JButton(Texts.get("cash.refresh"));
        refresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                load();
            }
        });
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        JPanel cards = new JPanel(new GridLayout(1, 2, Theme.PADDING, 0));
        cards.add(ReportParts.card(Texts.get("cash.total"), total));
        cards.add(ReportParts.card(Texts.get("report.count"), count));
        JPanel header = new JPanel(new BorderLayout(0, Theme.PADDING));
        header.add(shift, BorderLayout.NORTH);
        header.add(cards, BorderLayout.CENTER);

        JPanel left = new JPanel(new BorderLayout(0, Theme.GAP));
        left.add(new JLabel(Texts.get("cash.byMethod")), BorderLayout.NORTH);
        left.add(ReportParts.table(methods, 1, 2), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(0, Theme.GAP));
        right.add(new JLabel(Texts.get("cash.payments")), BorderLayout.NORTH);
        JScrollPane paymentsTable = ReportParts.table(payments, 0, 1);
        ReportParts.widths(paymentsTable, 110, 60, 100, 60, 90);
        right.add(paymentsTable, BorderLayout.CENTER);
        JPanel tables = new JPanel(new GridLayout(1, 2, Theme.PADDING, 0));
        tables.add(left);
        tables.add(right);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.add(refresh);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(header, BorderLayout.NORTH);
        content.add(tables, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(900, 520);
        dialog.setLocationRelativeTo(owner);
        load();
    }

    /**
     * Mostra o fecho de caixa de quem está autenticado.
     *
     * @param owner a janela principal
     * @param desk o balcão
     * @param shiftStart a hora a que o utilizador entrou no programa
     */
    public static void show(Window owner, ParkingDesk desk, LocalDateTime shiftStart) {
        new CashCloseDialog(owner, desk, shiftStart).dialog.setVisible(true);
    }

    /** Pede o fecho de caixa à {@code ParkingDesk} e mostra-o. */
    private void load() {
        // Um minuto de folga no fim, para entrar também um pagamento feito neste mesmo minuto.
        LocalDateTime now = LocalDateTime.now().plusMinutes(1);
        CashClose cash = desk.cashClose(shiftStart, now);
        RevenueReport report = cash.getReport();

        long minutes = Duration.between(shiftStart, LocalDateTime.now()).toMinutes();
        shift.setText(Texts.format("cash.shift", report.getOperator(), shiftStart.format(ReportParts.TIME),
                shiftStart.format(ReportParts.DATE), Texts.duration(Math.max(0, minutes))));
        total.setText(Texts.money(report.getTotalCents()));
        count.setText(String.valueOf(report.getCount()));

        methods.setRowCount(0);
        for (Payment.Method method : Payment.Method.values()) {
            RevenueLine<Payment.Method> line = lineOf(report, method);
            int paid = line == null ? 0 : line.getCount();
            long cents = line == null ? 0 : line.getTotalCents();
            methods.addRow(new Object[] { Texts.get("payment." + method.name()), String.valueOf(paid),
                Texts.money(cents) });
        }

        payments.setRowCount(0);
        for (Charge charge : cash.chargesByAmount()) {
            // Um pagamento tem talão; uma avença não, e a coluna diz que é uma avença.
            String ticket = charge instanceof Payment
                    ? String.valueOf(((Payment) charge).getSession().getTicketNumber())
                    : Texts.get("cash.pass");
            payments.addRow(new Object[] { Texts.money(charge.getAmountCents()), ticket, charge.getPlate(),
                charge.getPaidAt().format(ReportParts.TIME),
                Texts.get("payment." + charge.getMethod().name()) });
        }
        note.setText(report.getCount() == 0 ? Texts.get("cash.note.empty")
                : Texts.format("cash.note", String.valueOf(report.getCount()),
                        String.valueOf(cash.getComparisons())));
    }

    /** A linha do método no relatório, ou {@code null} se não houve pagamentos com ele. */
    private static RevenueLine<Payment.Method> lineOf(RevenueReport report, Payment.Method method) {
        for (RevenueLine<Payment.Method> line : report.byMethod()) {
            if (line.getKey() == method) {
                return line;
            }
        }
        return null;
    }
}
