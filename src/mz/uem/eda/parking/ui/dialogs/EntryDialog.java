package mz.uem.eda.parking.ui.dialogs;

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O diálogo de entrada: matrícula, tipo de viatura e quanto tempo o condutor
 * diz que vai ficar (ADR-004). A {@link ParkingDesk} decide o resto: se o
 * carro tem reserva, se há lugar e em que baia fica, ou se vai para a fila
 * de espera.
 *
 * <p>Se o parque estiver cheio, avisa logo que o carro ficou à espera. Se o
 * carro entrou, devolve a sessão: a janela principal anima a entrada e só
 * depois mostra o talão, com {@link #showTicket}.
 */
public class EntryDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField plate = new JTextField(20);
    private final JComboBox<String> type = new JComboBox<>();
    private final JSpinner hours = new JSpinner(new SpinnerNumberModel(1, 0, 720, 1));
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(0, 0, 59, 5));
    private final JLabel message = new JLabel();
    private ParkingSession entered;

    private EntryDialog(Frame owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("entry.title"), true);

        // As opções do tipo de viatura vêm do enum, pela mesma ordem.
        for (Vehicle.Type option : Vehicle.Type.values()) {
            type.addItem(Texts.get("vehicle." + option.name()));
        }

        JPanel duration = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        duration.add(hours);
        duration.add(new JLabel(Texts.get("entry.hours")));
        duration.add(minutes);
        duration.add(new JLabel(Texts.get("entry.minutes")));

        ActionListener register = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        };
        JButton submit = new JButton(Texts.get("entry.submit"));
        submit.addActionListener(register);
        // A caixa de texto de um JSpinner fica com o Enter para si; assim o
        // Enter também regista a entrada depois de escrever as horas.
        ((JSpinner.DefaultEditor) hours.getEditor()).getTextField().addActionListener(register);
        ((JSpinner.DefaultEditor) minutes.getEditor()).getTextField().addActionListener(register);
        JButton cancel = new JButton(Texts.get("dialog.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        FormPanel form = new FormPanel();
        form.addField(Texts.get("entry.plate"), plate);
        form.addField(Texts.get("entry.type"), type);
        form.addField(Texts.get("entry.duration"), duration);
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
     * Mostra o diálogo e espera que a entrada seja registada ou cancelada.
     *
     * @param owner a janela principal, para o diálogo ficar centrado nela
     * @param desk o balcão onde se regista a entrada
     * @return a sessão do carro que entrou, ou {@code null} se o operador
     *         cancelou, se o carro ficou na fila de espera ou se a gravação
     *         falhou
     */
    public static ParkingSession ask(Frame owner, ParkingDesk desk) {
        EntryDialog screen = new EntryDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.entered;
    }

    /**
     * Mostra o talão de uma entrada: número, matrícula, baia, classe e hora.
     *
     * @param owner a janela principal
     * @param session a sessão do carro que entrou
     */
    public static void showTicket(Frame owner, ParkingSession session) {
        JOptionPane.showMessageDialog(owner,
                Texts.format("entry.done", String.valueOf(session.getTicketNumber()),
                        session.getVehicle().getPlate(), session.getLaneCode(),
                        Texts.get("stay." + session.getStayClass().name()),
                        session.getEntryTime().format(TIME)),
                Texts.get("entry.done.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    private void submit() {
        String text = plate.getText().strip();
        int estimated = (Integer) hours.getValue() * 60 + (Integer) minutes.getValue();
        if (text.isEmpty()) {
            showProblem(Texts.get("entry.error.plate"));
            return;
        }
        if (estimated <= 0) {
            showProblem(Texts.get("entry.error.duration"));
            return;
        }

        Vehicle.Type chosen = Vehicle.Type.values()[type.getSelectedIndex()];
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session;
        try {
            session = desk.enter(text, chosen, estimated, now);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Por exemplo, o carro já está no parque ou à espera, ou a
            // matrícula só tem espaços e hífenes.
            showProblem(Texts.format("entry.error.refused", e.getMessage()));
            return;
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        }

        dialog.dispose();
        if (session == null) {
            JOptionPane.showMessageDialog(dialog.getOwner(),
                    Texts.format("entry.queued", Vehicle.normalizePlate(text)),
                    Texts.get("entry.queued.title"), JOptionPane.WARNING_MESSAGE);
        }
        entered = session;
    }

    private void showProblem(String text) {
        message.setText(text);
        plate.requestFocusInWindow();
    }
}