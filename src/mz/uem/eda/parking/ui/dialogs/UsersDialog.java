package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O ecrã de utilizadores, só para o administrador (RF22): a lista de quem
 * pode entrar no programa, pela ordem de criação, e três acções sobre ela.
 * <ul>
 * <li><b>Novo utilizador</b>, com o {@link NewUserDialog};</li>
 * <li><b>Nova palavra-passe</b>, para quem se esqueceu da sua, com o
 * {@link PasswordDialog};</li>
 * <li><b>Retirar</b>, depois de confirmar. Na base a conta fica
 * desactivada, não apagada, porque os pagamentos que cobrou continuam a
 * apontar para ela.</li>
 * </ul>
 *
 * <p>As regras ficam na {@code ParkingDesk}: o administrador não se pode
 * retirar a si próprio (aqui o botão nem fica activo) nem retirar o último
 * administrador. A tabela é só uma cópia para mostrar, num modelo do Swing
 * (ADR-028), e volta a ser preenchida depois de cada acção.
 */
public final class UsersDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] { Texts.get("users.column.name"), Texts.get("users.column.role") }, 0);
    private final JTable table = new JTable(model);
    private final JLabel count = new JLabel();
    private final JButton reset = new JButton(Texts.get("users.reset"));
    private final JButton remove = new JButton(Texts.get("users.remove"));

    private UsersDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("users.title"), Dialog.ModalityType.APPLICATION_MODAL);

        // Só leitura: sem editor, um duplo clique não abre a célula para escrever.
        table.setDefaultEditor(Object.class, null);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                updateButtons();
            }
        });

        JButton add = new JButton(Texts.get("users.add"));
        add.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addUser();
            }
        });
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetPassword();
            }
        });
        remove.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeUser();
            }
        });
        JButton close = new JButton(Texts.get("users.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, Theme.GAP));
        actions.add(add);
        actions.add(reset);
        actions.add(remove);
        JPanel side = new JPanel(new BorderLayout());
        side.add(actions, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout(Theme.GAP, 0));
        JLabel note = new JLabel(Texts.get("users.note"));
        note.setForeground(Theme.MUTED);
        bottom.add(note, BorderLayout.CENTER);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeRow.add(close);
        bottom.add(closeRow, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(Theme.PADDING, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(count, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        content.add(side, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(new Dimension(640, 420));
        dialog.setLocationRelativeTo(owner);
        fill(null);
    }

    /**
     * Mostra o ecrã de utilizadores até o administrador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     */
    public static void show(Window owner, ParkingDesk desk) {
        new UsersDialog(owner, desk).dialog.setVisible(true);
    }

    /**
     * Volta a preencher a tabela a partir da {@code ParkingDesk}.
     *
     * @param select o utilizador a deixar seleccionado, ou {@code null}
     */
    private void fill(String select) {
        model.setRowCount(0);
        User me = desk.getCurrentUser();
        int total = 0;
        int selectedRow = -1;
        for (User user : desk.users()) {
            String role = Texts.get("role." + user.getRole().name());
            if (user.equals(me)) {
                role = Texts.format("users.you", role);
            }
            model.addRow(new Object[] { user.getUsername(), role });
            if (user.getUsername().equals(select)) {
                selectedRow = total;
            }
            total++;
        }
        count.setText(Texts.format("users.count", String.valueOf(total)));
        if (selectedRow >= 0) {
            table.setRowSelectionInterval(selectedRow, selectedRow);
        }
        updateButtons();
    }

    /** Nova palavra-passe e Retirar só com alguém seleccionado; Retirar nunca para o próprio. */
    private void updateButtons() {
        String selected = selectedUsername();
        boolean me = selected != null && selected.equals(desk.getCurrentUser().getUsername());
        reset.setEnabled(selected != null);
        remove.setEnabled(selected != null && !me);
        remove.setToolTipText(me ? Texts.get("users.remove.self") : null);
    }

    /** @return o nome na linha seleccionada, ou {@code null} se não houver */
    private String selectedUsername() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return (String) model.getValueAt(row, 0);
    }

    private void addUser() {
        User created = NewUserDialog.ask(dialog, desk);
        if (created != null) {
            fill(created.getUsername());
            JOptionPane.showMessageDialog(dialog,
                    Texts.format("users.added", created.getUsername(),
                            Texts.get("role." + created.getRole().name())),
                    Texts.get("users.title"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void resetPassword() {
        String selected = selectedUsername();
        if (selected != null) {
            PasswordDialog.askReset(dialog, desk, selected);
        }
    }

    private void removeUser() {
        String selected = selectedUsername();
        if (selected == null) {
            return;
        }
        Object[] options = { Texts.get("users.remove.confirm"), Texts.get("dialog.cancel") };
        int answer = JOptionPane.showOptionDialog(dialog, Texts.format("users.remove.question", selected),
                Texts.get("users.remove"), JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[1]);
        if (answer != 0) {
            return;
        }
        try {
            if (!desk.removeUser(selected)) {
                JOptionPane.showMessageDialog(dialog, Texts.format("users.error.gone", selected),
                        Texts.get("users.remove"), JOptionPane.WARNING_MESSAGE);
            }
        } catch (IllegalStateException e) {
            // O próprio ou o último administrador. Com o botão desligado para o próprio, não
            // devia acontecer (o último administrador é quem está autenticado), mas a regra é
            // da ParkingDesk e o ecrã respeita a resposta dela.
            JOptionPane.showMessageDialog(dialog, Texts.get("users.error.lastadmin"),
                    Texts.get("users.remove"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
        }
        fill(null);
    }
}
