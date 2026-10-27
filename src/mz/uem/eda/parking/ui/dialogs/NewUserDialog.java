package mz.uem.eda.parking.ui.dialogs;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;

/**
 * Cria um utilizador novo (RF22): nome, perfil e palavra-passe inicial,
 * escrita duas vezes. O perfil começa em Operador, que é o caso comum; um
 * segundo administrador escolhe-se de propósito.
 *
 * <p>O administrador diz a palavra-passe inicial ao novo utilizador, que a
 * pode mudar depois no menu Sessão.
 */
public final class NewUserDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField username = new JTextField(20);
    private final JComboBox<String> role = new JComboBox<>();
    private final JPasswordField password = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JLabel message = new JLabel();
    private User created;

    private NewUserDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("newuser.title"), Dialog.ModalityType.APPLICATION_MODAL);

        // As opções do perfil vêm do enum, pela mesma ordem: Operador primeiro.
        for (User.Role option : User.Role.values()) {
            role.addItem(Texts.get("role." + option.name()));
        }

        JButton submit = new JButton(Texts.get("newuser.submit"));
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
        form.addField(Texts.get("login.username"), username);
        form.addField(Texts.get("newuser.role"), role);
        form.addField(Texts.get("newuser.password"), password);
        form.addField(Texts.get("password.confirm"), confirm);
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
     * Mostra o ecrã e espera que o utilizador seja criado ou que o
     * administrador desista.
     *
     * @param owner a janela por cima da qual aparece o ecrã
     * @param desk o balcão, com um administrador autenticado
     * @return o utilizador criado, ou {@code null} se o administrador desistiu
     */
    public static User ask(Window owner, ParkingDesk desk) {
        NewUserDialog screen = new NewUserDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.created;
    }

    private void submit() {
        String name = username.getText().strip();
        char[] secret = password.getPassword();
        char[] repeated = confirm.getPassword();
        try {
            String problem = Passwords.problem(secret, repeated);
            if (name.isEmpty()) {
                showProblem(Texts.get("user.error.blank"));
                username.requestFocusInWindow();
            } else if (problem != null) {
                showProblem(problem);
                password.requestFocusInWindow();
            } else {
                create(name, secret);
            }
        } finally {
            Arrays.fill(secret, ' ');
            Arrays.fill(repeated, ' ');
        }
    }

    private void create(String name, char[] secret) {
        User.Role chosen = User.Role.values()[role.getSelectedIndex()];
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            created = desk.registerUser(name, secret, chosen);
            dialog.dispose();
        } catch (IllegalStateException e) {
            // O nome já existe.
            showProblem(Texts.format("newuser.error.exists", name));
            username.requestFocusInWindow();
            username.selectAll();
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
    }

    private void showProblem(String text) {
        message.setText(text);
        password.setText("");
        confirm.setText("");
    }
}
