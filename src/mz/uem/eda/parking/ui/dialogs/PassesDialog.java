package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O ecrã das avenças mensais (RF16), para qualquer utilizador: as avenças
 * vendidas, a mais recente em cima, com o estado de cada uma (válida, ainda
 * não começou, terminou), e o botão «Nova avença…».
 *
 * <p>Em cima, quantas avenças valem hoje e o valor de uma avença na tarifa
 * em vigor. Por omissão a lista mostra só as válidas e as dos meses
 * seguintes; desmarcando a caixa, aparecem também as que já terminaram.
 */
public final class PassesDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JLabel valid = new JLabel();
    private final JLabel price = new JLabel();
    private final JCheckBox currentOnly = new JCheckBox(Texts.get("pass.current"), true);
    private final DefaultTableModel table = ReportParts.model("report.column.plate", "report.column.customer",
            "report.column.phone", "report.column.month", "report.column.state", "report.column.amount",
            "report.column.method", "report.column.paidAt", "report.column.operator");

    private PassesDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("pass.title"), Dialog.ModalityType.APPLICATION_MODAL);

        JButton add = new JButton(Texts.get("pass.add"));
        add.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addPass();
            }
        });
        currentOnly.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
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
        cards.add(ReportParts.card(Texts.get("pass.valid"), valid));
        cards.add(ReportParts.card(Texts.get("pass.price"), price));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        actions.add(add);
        actions.add(currentOnly);
        JScrollPane scroll = ReportParts.table(table, 5);
        ReportParts.widths(scroll, 90, 140, 100, 150, 150, 115, 85, 125, 75);
        JPanel tableArea = new JPanel(new BorderLayout(0, Theme.GAP));
        tableArea.add(actions, BorderLayout.NORTH);
        tableArea.add(scroll, BorderLayout.CENTER);

        JLabel note = ReportParts.note();
        note.setText(Texts.get("pass.note"));
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(cards, BorderLayout.NORTH);
        content.add(tableArea, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(1180, 520);
        dialog.setLocationRelativeTo(owner);
        refresh();
    }

    /**
     * Mostra o ecrã das avenças até o operador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão
     */
    public static void show(Window owner, ParkingDesk desk) {
        new PassesDialog(owner, desk).dialog.setVisible(true);
    }

    /**
     * Volta a ler as avenças. A {@code ParkingDesk} dá-as pela ordem de
     * venda; o ecrã mostra a mais recente em cima, inserindo cada linha no
     * topo da tabela.
     */
    private void refresh() {
        LocalDate today = LocalDate.now();
        table.setRowCount(0);
        int validToday = 0;
        for (MonthlyPass pass : desk.passes()) {
            String state;
            if (pass.coversDay(today)) {
                state = Texts.get("pass.state.valid");
                validToday++;
            } else if (pass.getValidFrom().isAfter(today)) {
                state = Texts.get("pass.state.future");
            } else {
                state = Texts.get("pass.state.expired");
                if (currentOnly.isSelected()) {
                    continue;
                }
            }
            table.insertRow(0, new Object[] { pass.getPlate(), pass.getCustomerName(),
                pass.getPhone() == null ? "" : pass.getPhone(), Texts.month(pass.getMonth()), state,
                Texts.money(pass.getAmountCents()), Texts.get("payment." + pass.getMethod().name()),
                pass.getPaidAt().format(ReportParts.DATE_TIME), pass.getOperator().getUsername() });
        }
        valid.setText(String.valueOf(validToday));
        price.setText(Texts.money(desk.getTariff().getMonthlyPassCents()));
    }

    private void addPass() {
        MonthlyPass sold = NewPassDialog.ask(dialog, desk);
        refresh();
        if (sold != null) {
            JOptionPane.showMessageDialog(dialog,
                    Texts.format("newpass.done", sold.getCustomerName(), sold.getPlate(),
                            Texts.month(sold.getMonth()), Texts.money(sold.getAmountCents()),
                            Texts.get("payment." + sold.getMethod().name())),
                    Texts.get("pass.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
