package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O ecrã das reservas (RF10, RF11, RF12), para qualquer utilizador: as
 * reservas activas, da que expira primeiro para a que expira depois, com
 * quanto tempo falta a cada uma, e dois botões, «Nova reserva…» e
 * «Cancelar reserva».
 *
 * <p>Não há check-in neste ecrã: quando o carro chega, regista-se a entrada
 * como sempre, e a {@code ParkingDesk} reconhece a reserva pela matrícula.
 *
 * <p>Em cima, três números: quantas reservas estão activas, quantos lugares
 * ficam para quem chega sem reserva (os livres menos os reservados) e a
 * tolerância. Um relógio de 30 segundos volta a ler tudo, para o tempo que
 * falta ir descendo e as reservas vencidas desaparecerem; pára quando o
 * ecrã fecha.
 */
public final class ReservationsDialog {

    private static final int REFRESH_MS = 30_000;

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JLabel active = new JLabel();
    private final JLabel free = new JLabel();
    private final DefaultTableModel table = ReportParts.model("entry.plate", "reservation.column.class",
            "reservation.column.duration", "reservation.column.created", "reservation.column.expires",
            "reservation.column.left");
    private final JScrollPane tableScroll = ReportParts.table(table, 2, 5);
    private final JButton cancelReservation = new JButton(Texts.get("reservation.cancel"));
    private final Timer clock;

    private ReservationsDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("reservation.title"), Dialog.ModalityType.APPLICATION_MODAL);

        JButton add = new JButton(Texts.get("reservation.add"));
        add.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addReservation();
            }
        });
        cancelReservation.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelSelected();
            }
        });
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        tableOf().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                cancelReservation.setEnabled(tableOf().getSelectedRow() >= 0);
            }
        });
        ReportParts.widths(tableScroll, 110, 130, 90, 90, 90, 90);

        JPanel cards = new JPanel(new GridLayout(1, 3, Theme.PADDING, 0));
        cards.add(ReportParts.card(Texts.get("reservation.active"), active));
        cards.add(ReportParts.card(Texts.get("reservation.free"), free));
        JLabel tolerance = new JLabel(Texts.format("time.minutes",
                String.valueOf(ParkingDesk.RESERVATION_TOLERANCE_MINUTES)));
        cards.add(ReportParts.card(Texts.get("reservation.tolerance"), tolerance));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        actions.add(add);
        actions.add(cancelReservation);
        JPanel tableArea = new JPanel(new BorderLayout(0, Theme.GAP));
        tableArea.add(actions, BorderLayout.NORTH);
        tableArea.add(tableScroll, BorderLayout.CENTER);

        JLabel note = ReportParts.note();
        note.setText(Texts.format("reservation.note", String.valueOf(ParkingDesk.RESERVATION_TOLERANCE_MINUTES)));
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

        clock = new Timer(REFRESH_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                clock.stop();
            }
        });

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(760, 500);
        dialog.setLocationRelativeTo(owner);
        refresh();
        clock.start();
    }

    /**
     * Mostra o ecrã das reservas até o operador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão
     */
    public static void show(Window owner, ParkingDesk desk) {
        new ReservationsDialog(owner, desk).dialog.setVisible(true);
    }

    /** Volta a ler as reservas: expira as vencidas e mostra as activas. */
    private void refresh() {
        LocalDateTime now = LocalDateTime.now();
        String selected = selectedPlate();
        table.setRowCount(0);
        int row = 0;
        int keep = -1;
        for (Reservation reservation : desk.reservations(now)) {
            if (!reservation.isActive()) {
                continue;
            }
            long left = Math.max(0, Duration.between(now, reservation.getExpiresAt()).toMinutes());
            table.addRow(new Object[] { reservation.getPlate(),
                Texts.get("stay." + reservation.getStayClass().name()),
                Texts.duration(reservation.getEstimatedMinutes()),
                reservation.getCreatedAt().format(ReportParts.TIME),
                reservation.getExpiresAt().format(ReportParts.TIME), Texts.duration(left) });
            if (reservation.getPlate().equals(selected)) {
                keep = row;
            }
            row++;
        }
        if (keep >= 0) {
            tableOf().setRowSelectionInterval(keep, keep);
        }
        cancelReservation.setEnabled(keep >= 0);
        active.setText(String.valueOf(desk.activeReservationCount()));
        free.setText(String.valueOf(desk.freeForWalkIns(now)));
    }

    private void addReservation() {
        Reservation created = NewReservationDialog.ask(dialog, desk);
        if (created == null) {
            refresh();
            return;
        }
        refresh();
        JOptionPane.showMessageDialog(dialog, Texts.format("reservation.done", created.getPlate(),
                created.getExpiresAt().format(ReportParts.TIME)), Texts.get("reservation.title"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void cancelSelected() {
        String plate = selectedPlate();
        if (plate == null) {
            return;
        }
        Object[] options = { Texts.get("reservation.cancel"), Texts.get("reservation.keep") };
        int answer = JOptionPane.showOptionDialog(dialog, Texts.format("reservation.cancel.question", plate),
                Texts.get("reservation.cancel"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[1]);
        if (answer != 0) {
            return;
        }
        try {
            if (!desk.cancelReservation(plate, LocalDateTime.now())) {
                JOptionPane.showMessageDialog(dialog, Texts.format("reservation.error.gone", plate),
                        Texts.get("reservation.cancel"), JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
        }
        refresh();
    }

    /** @return a matrícula da linha seleccionada, ou {@code null} se não houver */
    private String selectedPlate() {
        int row = tableOf().getSelectedRow();
        return row < 0 ? null : (String) table.getValueAt(row, 0);
    }

    private JTable tableOf() {
        return (JTable) tableScroll.getViewport().getView();
    }
}
