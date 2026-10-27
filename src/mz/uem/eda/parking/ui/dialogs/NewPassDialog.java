package mz.uem.eda.parking.ui.dialogs;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Vende uma avença mensal (RF16): o nome e o telefone do cliente, a
 * matrícula, o mês e o método de pagamento. O valor é o da tarifa em vigor
 * e aparece no ecrã antes de cobrar.
 *
 * <p>O mês escolhe-se entre o actual e os dois seguintes. A
 * {@code ParkingDesk} recusa uma segunda avença da mesma matrícula para o
 * mesmo mês.
 */
public final class NewPassDialog {

    /** Quantos meses se podem escolher: o actual e os seguintes. */
    private static final int MONTHS = 3;

    /** Os métodos que se podem escolher: todos menos a própria avença. */
    private static final Payment.Method[] CHOICES = { Payment.Method.CASH, Payment.Method.MPESA,
        Payment.Method.EMOLA, Payment.Method.CARD };

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField customer = new JTextField(24);
    private final JTextField phone = new JTextField(24);
    private final JTextField plate = new JTextField(24);
    private final JComboBox<String> month = new JComboBox<>();
    private final YearMonth[] months = new YearMonth[MONTHS];
    private final JRadioButton[] methods = new JRadioButton[CHOICES.length];
    private final JLabel message = new JLabel();
    private MonthlyPass sold;

    private NewPassDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("newpass.title"), Dialog.ModalityType.APPLICATION_MODAL);

        YearMonth current = YearMonth.now();
        for (int i = 0; i < MONTHS; i++) {
            months[i] = current.plusMonths(i);
            month.addItem(Texts.month(months[i]));
        }

        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < CHOICES.length; i++) {
            methods[i] = new JRadioButton(Texts.get("payment." + CHOICES[i].name()));
            group.add(methods[i]);
            methodRow.add(methods[i]);
        }
        methods[0].setSelected(true);

        JLabel amount = new JLabel(Texts.money(desk.getTariff().getMonthlyPassCents()));
        amount.setFont(amount.getFont().deriveFont(Font.BOLD, 18f));

        JButton submit = new JButton(Texts.get("newpass.submit"));
        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        JButton cancel = new JButton(Texts.get("dialog.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        FormPanel form = new FormPanel();
        form.addField(Texts.get("newpass.customer"), customer);
        form.addField(Texts.get("newpass.phone"), phone);
        form.addField(Texts.get("entry.plate"), plate);
        form.addField(Texts.get("newpass.month"), month);
        form.addField(Texts.get("newpass.amount"), amount);
        form.addField(Texts.get("exit.method"), methodRow);
        form.addMessage(message);
        form.addButtons(cancel, submit);

        dialog.setContentPane(form.getPanel());
        dialog.getRootPane().setDefaultButton(submit);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Mostra o ecrã e espera que a avença seja vendida ou que o operador
     * desista.
     *
     * @param owner a janela por cima da qual aparece o ecrã
     * @param desk o balcão
     * @return a avença vendida, ou {@code null} se o operador desistiu ou a
     *         gravação falhou
     */
    public static MonthlyPass ask(Window owner, ParkingDesk desk) {
        NewPassDialog screen = new NewPassDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.sold;
    }

    private void submit() {
        String name = customer.getText().strip();
        String plateText = plate.getText().strip();
        String phoneText = phone.getText().strip();
        if (name.isEmpty()) {
            message.setText(Texts.get("newpass.error.customer"));
            customer.requestFocusInWindow();
            return;
        }
        if (plateText.isEmpty()) {
            message.setText(Texts.get("entry.error.plate"));
            plate.requestFocusInWindow();
            return;
        }

        MonthlyPass pass;
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            pass = desk.sellPass(name, phoneText.isEmpty() ? null : phoneText, plateText,
                    months[month.getSelectedIndex()], selectedMethod(), LocalDateTime.now());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // A matrícula já tem avença para esse mês, ou um campo não serve.
            JOptionPane.showMessageDialog(dialog, Texts.format("newpass.error.refused", e.getMessage()),
                    Texts.get("newpass.title"), JOptionPane.WARNING_MESSAGE);
            return;
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        sold = pass;
        dialog.dispose();
    }

    private Payment.Method selectedMethod() {
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isSelected()) {
                return CHOICES[i];
            }
        }
        return CHOICES[0];
    }
}
