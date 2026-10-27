package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.service.ExitResult;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O diálogo de saída, em dois passos. Primeiro procura o carro pela
 * matrícula ou pelo número do talão e mostra quanto paga. Depois o operador
 * escolhe o método de pagamento e confirma: a {@link ParkingDesk} cobra,
 * tira o carro com as manobras, grava e deixa entrar o primeiro da fila de
 * espera.
 *
 * <p><b>A hora de saída é a hora a que o valor foi mostrado.</b> Fica
 * guardada no primeiro passo e é a mesma que se passa à saída, para o
 * condutor pagar exactamente o que viu, mesmo que entretanto mude a fracção
 * de 15 minutos.
 *
 * <p>No fim mostra o recibo: o valor, o método, quantas manobras foram
 * precisas e, se houve, o carro que entrou da fila de espera.
 */
public class ExitDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField search = new JTextField(16);
    private final JLabel summary = new JLabel();
    private final JLabel amount = new JLabel();
    private final JRadioButton[] methods = new JRadioButton[Payment.Method.values().length];
    private final JButton find = new JButton(Texts.get("exit.find"));
    private final JButton submit = new JButton(Texts.get("exit.submit"));
    private final JLabel message = new JLabel();

    /** O carro encontrado no primeiro passo, ou {@code null}. */
    private ParkingSession found;

    /** A hora a que o valor foi mostrado; é a hora de saída. */
    private LocalDateTime at;

    private boolean done;

    private ExitDialog(Frame owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("exit.title"), true);

        JPanel searchRow = new JPanel(new BorderLayout(Theme.GAP, 0));
        searchRow.add(search, BorderLayout.CENTER);
        searchRow.add(find, BorderLayout.EAST);
        find.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                find();
            }
        });

        amount.setFont(amount.getFont().deriveFont(Font.BOLD, 22f));

        // Um botão por método de pagamento, pela ordem do enum.
        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        ButtonGroup group = new ButtonGroup();
        Payment.Method[] options = Payment.Method.values();
        for (int i = 0; i < options.length; i++) {
            methods[i] = new JRadioButton(Texts.get("payment." + options[i].name()));
            group.add(methods[i]);
            methodRow.add(methods[i]);
        }
        methods[0].setSelected(true);

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
        form.addField(Texts.get("exit.search"), searchRow);
        form.addField(Texts.get("exit.car"), summary);
        form.addField(Texts.get("exit.amount"), amount);
        form.addField(Texts.get("exit.method"), methodRow);
        form.addMessage(message);
        form.addButtons(cancel, submit);
        showNothingFound();

        dialog.setContentPane(form.getPanel());
        dialog.getRootPane().setDefaultButton(find);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Mostra o diálogo e espera que a saída seja feita ou cancelada.
     *
     * @param owner a janela principal, para o diálogo ficar centrado nela
     * @param desk o balcão onde se faz a saída
     * @return {@code true} se o parque mudou, para a janela se redesenhar
     */
    public static boolean ask(Frame owner, ParkingDesk desk) {
        ExitDialog screen = new ExitDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.done;
    }

    /** Primeiro passo: procura o carro e mostra quanto paga se sair agora. */
    private void find() {
        String text = search.getText().strip();
        if (text.isEmpty()) {
            showNothingFound();
            message.setText(Texts.get("exit.error.blank"));
            return;
        }
        ParkingSession session = desk.findForExit(text);
        if (session == null) {
            showNothingFound();
            message.setText(Texts.get("exit.notfound"));
            search.selectAll();
            search.requestFocusInWindow();
            return;
        }

        found = session;
        at = LocalDateTime.now();
        LocalDateTime entry = session.getEntryTime();
        String entered = entry.toLocalDate().equals(LocalDate.now()) ? entry.format(TIME) : entry.format(DAY_TIME);
        long stayed = Duration.between(entry, at).toMinutes();
        summary.setText(Texts.format("exit.summary", session.getVehicle().getPlate(),
                String.valueOf(session.getTicketNumber()), session.getLaneCode(),
                Texts.get("stay." + session.getStayClass().name()), entered,
                Texts.format("exit.stayed", String.valueOf(stayed / 60), String.valueOf(stayed % 60))));
        amount.setText(Texts.money(desk.quote(session, at)));
        message.setText(" ");
        setPaymentEnabled(true);
        dialog.getRootPane().setDefaultButton(submit);
        dialog.pack();
    }

    /** Segundo passo: cobra e tira o carro. */
    private void submit() {
        if (found == null) {
            return;
        }
        Payment.Method method = selectedMethod();
        ExitResult result;
        try {
            result = desk.exit(found, method, at);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Por exemplo, a baia de manobra não tem espaço para os carros de cima.
            message.setText(Texts.format("exit.error.refused", e.getMessage()));
            return;
        } catch (SQLException e) {
            done = true;
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        }

        done = true;
        dialog.dispose();
        JOptionPane.showMessageDialog(dialog.getOwner(), receipt(result),
                Texts.get("exit.done.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private String receipt(ExitResult result) {
        Payment payment = result.getPayment();
        ParkingSession session = payment.getSession();
        String text = Texts.format("exit.done", session.getVehicle().getPlate(),
                String.valueOf(session.getTicketNumber()), Texts.money(payment.getAmountCents()),
                Texts.get("payment." + payment.getMethod().name()),
                String.valueOf(result.getPlan().getManoeuvreCount()));
        ParkingSession admitted = result.getAdmitted();
        if (admitted != null) {
            text += Texts.format("exit.admitted", admitted.getVehicle().getPlate(), admitted.getLaneCode());
        }
        return text;
    }

    private Payment.Method selectedMethod() {
        Payment.Method[] options = Payment.Method.values();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isSelected()) {
                return options[i];
            }
        }
        return options[0];
    }

    /** Volta ao primeiro passo: nada encontrado, nada para cobrar. */
    private void showNothingFound() {
        found = null;
        at = null;
        summary.setText(Texts.get("exit.summary.empty"));
        amount.setText("—");
        setPaymentEnabled(false);
        dialog.getRootPane().setDefaultButton(find);
    }

    private void setPaymentEnabled(boolean enabled) {
        for (JRadioButton method : methods) {
            method.setEnabled(enabled);
        }
        submit.setEnabled(enabled);
    }
}