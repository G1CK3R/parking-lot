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
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.MonthlyPass;
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
 * <p><b>Avença e talão perdido (RF16, RF17).</b> Se a matrícula tiver uma
 * avença válida, o diálogo diz de quem é e até quando, e a estadia não se
 * paga. A caixa «Talão perdido» soma a multa da tarifa; o valor a pagar
 * muda logo que se marca. Se não houver nada a pagar por causa da avença,
 * os métodos de pagamento ficam desligados.
 *
 * <p>Devolve o resultado da saída: a janela principal anima as manobras e
 * só depois mostra o recibo, com {@link #showReceipt}: o valor, o método,
 * quantas manobras foram precisas e, se houve, o carro que entrou da fila
 * de espera.
 */
public class ExitDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Os métodos que o operador pode escolher: todos menos a avença, que é o programa que atribui. */
    private static final Payment.Method[] CHOICES = { Payment.Method.CASH, Payment.Method.MPESA,
        Payment.Method.EMOLA, Payment.Method.CARD };

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField search = new JTextField(16);
    private final JLabel summary = new JLabel();
    private final JLabel pass = new JLabel();
    private final JCheckBox lostTicket = new JCheckBox();
    private final JLabel amount = new JLabel();
    private final JRadioButton[] methods = new JRadioButton[CHOICES.length];
    private final JButton find = new JButton(Texts.get("exit.find"));
    private final JButton submit = new JButton(Texts.get("exit.submit"));
    private final JLabel message = new JLabel();

    /** O carro encontrado no primeiro passo, ou {@code null}. */
    private ParkingSession found;

    /** Se o carro encontrado tem avença válida. */
    private boolean covered;

    /** A hora a que o valor foi mostrado; é a hora de saída. */
    private LocalDateTime at;

    private ExitResult result;

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
        lostTicket.setText(Texts.format("exit.lost", Texts.money(desk.getTariff().getLostTicketCents())));
        lostTicket.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showAmount();
            }
        });

        // Um botão por método que o operador pode escolher.
        JPanel methodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < CHOICES.length; i++) {
            methods[i] = new JRadioButton(Texts.get("payment." + CHOICES[i].name()));
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
        form.addField(Texts.get("exit.pass"), pass);
        form.addComponent(lostTicket);
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
     * @return o resultado da saída, ou {@code null} se o operador cancelou
     *         ou se a gravação falhou
     */
    public static ExitResult ask(Frame owner, ParkingDesk desk) {
        return ask(owner, desk, null);
    }

    /**
     * Mostra o diálogo já com um carro procurado, por exemplo o que se
     * escolheu na pesquisa de viaturas.
     *
     * @param owner a janela principal
     * @param desk o balcão onde se faz a saída
     * @param plateOrTicket a matrícula ou o talão a procurar logo, ou
     *        {@code null} para começar vazio
     * @return o resultado da saída, ou {@code null} se o operador cancelou
     *         ou se a gravação falhou
     */
    public static ExitResult ask(Frame owner, ParkingDesk desk, String plateOrTicket) {
        ExitDialog screen = new ExitDialog(owner, desk);
        if (plateOrTicket != null) {
            screen.search.setText(plateOrTicket);
            screen.find();
            screen.dialog.setLocationRelativeTo(owner);
        }
        screen.dialog.setVisible(true);
        return screen.result;
    }

    /**
     * Mostra o recibo de uma saída.
     *
     * @param owner a janela principal
     * @param result o resultado da saída
     */
    public static void showReceipt(Frame owner, ExitResult result) {
        JOptionPane.showMessageDialog(owner, receipt(result),
                Texts.get("exit.done.title"), JOptionPane.INFORMATION_MESSAGE);
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

        MonthlyPass valid = desk.validPass(session.getVehicle().getPlate(), at);
        covered = valid != null;
        pass.setText(valid == null ? Texts.get("exit.pass.none")
                : Texts.format("exit.pass.valid", valid.getCustomerName(), valid.getValidUntil().format(DATE)));

        message.setText(" ");
        lostTicket.setEnabled(true);
        showAmount();
        dialog.getRootPane().setDefaultButton(submit);
        dialog.pack();
    }

    /**
     * Mostra o valor a pagar com o estado da caixa do talão perdido. Os
     * métodos ficam desligados só quando a avença cobre tudo; uma estadia
     * dentro da tolerância também dá 0, e aí o método escolhido fica
     * registado como sempre.
     */
    private void showAmount() {
        if (found == null) {
            return;
        }
        long due = desk.quote(found, at, lostTicket.isSelected());
        amount.setText(Texts.money(due));
        setMethodsEnabled(!covered || due > 0);
        submit.setEnabled(true);
    }

    /** Segundo passo: cobra e tira o carro. */
    private void submit() {
        if (found == null) {
            return;
        }
        try {
            result = desk.exit(found, selectedMethod(), at, lostTicket.isSelected());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Por exemplo, a baia de manobra não tem espaço para os carros de cima.
            message.setText(Texts.format("exit.error.refused", e.getMessage()));
            return;
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        }
        dialog.dispose();
    }

    private static String receipt(ExitResult result) {
        Payment payment = result.getPayment();
        ParkingSession session = payment.getSession();
        String plate = session.getVehicle().getPlate();
        String ticket = String.valueOf(session.getTicketNumber());
        String manoeuvres = String.valueOf(result.getPlan().getManoeuvreCount());
        String text;
        if (payment.getMethod() == Payment.Method.PASS) {
            text = Texts.format("exit.done.pass", plate, ticket, manoeuvres);
        } else {
            text = Texts.format("exit.done", plate, ticket, Texts.money(payment.getAmountCents()),
                    Texts.get("payment." + payment.getMethod().name()), manoeuvres);
        }
        if (payment.getLostTicketCents() > 0) {
            text += Texts.format("exit.done.lost", Texts.money(payment.getLostTicketCents()));
        }
        ParkingSession admitted = result.getAdmitted();
        if (admitted != null) {
            text += Texts.format("exit.admitted", admitted.getVehicle().getPlate(), admitted.getLaneCode());
        }
        return text;
    }

    private Payment.Method selectedMethod() {
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].isSelected()) {
                return CHOICES[i];
            }
        }
        return CHOICES[0];
    }

    /** Volta ao primeiro passo: nada encontrado, nada para cobrar. */
    private void showNothingFound() {
        found = null;
        covered = false;
        at = null;
        summary.setText(Texts.get("exit.summary.empty"));
        pass.setText(" ");
        lostTicket.setSelected(false);
        lostTicket.setEnabled(false);
        amount.setText("—");
        setMethodsEnabled(false);
        submit.setEnabled(false);
        dialog.getRootPane().setDefaultButton(find);
    }

    private void setMethodsEnabled(boolean enabled) {
        for (JRadioButton method : methods) {
            method.setEnabled(enabled);
        }
    }
}
