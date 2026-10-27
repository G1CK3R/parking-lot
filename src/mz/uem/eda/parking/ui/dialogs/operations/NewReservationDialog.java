package mz.uem.eda.parking.ui.dialogs.operations;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Reserva um lugar (RF10): a matrícula e quanto tempo o condutor diz que
 * vai ficar, como na entrada. A reserva guarda um lugar durante
 * {@value ParkingDesk#RESERVATION_TOLERANCE_MINUTES} minutos; quando o
 * carro chegar, regista-se a entrada normalmente, e a {@code ParkingDesk}
 * reconhece a reserva pela matrícula.
 *
 * <p>Só há reserva se houver um lugar livre que não esteja já reservado
 * (RF11); senão, o ecrã diz que não há lugar.
 */
public final class NewReservationDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField plate = new JTextField(20);
    private final JSpinner hours = new JSpinner(new SpinnerNumberModel(1, 0, 720, 1));
    private final JSpinner minutes = new JSpinner(new SpinnerNumberModel(0, 0, 59, 5));
    private final JLabel message = new JLabel();
    private Reservation created;

    private NewReservationDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("reservation.new.title"), Dialog.ModalityType.APPLICATION_MODAL);

        JPanel duration = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        duration.add(hours);
        duration.add(new JLabel(Texts.get("entry.hours")));
        duration.add(minutes);
        duration.add(new JLabel(Texts.get("entry.minutes")));

        ActionListener reserve = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        };
        JButton submit = new JButton(Texts.get("reservation.new.submit"));
        submit.addActionListener(reserve);
        // Como na entrada: o Enter dentro das horas ou dos minutos também reserva.
        ((JSpinner.DefaultEditor) hours.getEditor()).getTextField().addActionListener(reserve);
        ((JSpinner.DefaultEditor) minutes.getEditor()).getTextField().addActionListener(reserve);
        JButton cancel = new JButton(Texts.get("dialog.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        FormPanel form = new FormPanel();
        form.addText(Texts.format("reservation.new.intro", String.valueOf(ParkingDesk.RESERVATION_TOLERANCE_MINUTES)));
        form.addField(Texts.get("entry.plate"), plate);
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
     * Mostra o ecrã e espera que a reserva seja feita ou que o operador
     * desista.
     *
     * @param owner a janela por cima da qual aparece o ecrã
     * @param desk o balcão
     * @return a reserva feita, ou {@code null} se o operador desistiu ou a
     *         gravação falhou
     */
    public static Reservation ask(Window owner, ParkingDesk desk) {
        NewReservationDialog screen = new NewReservationDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.created;
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

        Reservation reservation;
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            reservation = desk.reserve(text, estimated, LocalDateTime.now());
        } catch (IllegalStateException | IllegalArgumentException e) {
            // O carro já está no parque ou já tem reserva, ou a matrícula não
            // serve. A frase pode ser comprida, por isso vai numa caixa de aviso.
            JOptionPane.showMessageDialog(dialog, Texts.format("reservation.error.refused", e.getMessage()),
                    Texts.get("reservation.new.title"), JOptionPane.WARNING_MESSAGE);
            plate.requestFocusInWindow();
            return;
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        if (reservation == null) {
            showProblem(Texts.get("reservation.error.full"));
            return;
        }
        created = reservation;
        dialog.dispose();
    }

    private void showProblem(String text) {
        message.setText(text);
        plate.requestFocusInWindow();
    }
}
