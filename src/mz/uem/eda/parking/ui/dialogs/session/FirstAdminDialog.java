package mz.uem.eda.parking.ui.dialogs.session;

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;

/**
 * O ecrã que aparece em vez do login quando a base ainda não tem
 * utilizadores: cria a conta do primeiro administrador e entra com ela. O
 * {@code 02-seed.sql} não traz nenhum administrador com palavra-passe
 * conhecida, de propósito.
 *
 * <p>Pede a palavra-passe duas vezes, porque um engano aqui deixava o
 * parque sem ninguém que consiga entrar.
 */
public class FirstAdminDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JLabel message = new JLabel();
    private User user;

    private FirstAdminDialog(ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog((Frame) null, Texts.get("firstadmin.title"), true);

        JButton submit = new JButton(Texts.get("firstadmin.submit"));
        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        JButton cancel = new JButton(Texts.get("login.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        FormPanel form = new FormPanel();
        form.addText(Texts.get("firstadmin.intro"));
        form.addField(Texts.get("login.username"), username);
        form.addField(Texts.get("login.password"), password);
        form.addField(Texts.get("password.confirm"), confirm);
        form.addMessage(message);
        form.addButtons(cancel, submit);

        dialog.setContentPane(form.getPanel());
        dialog.getRootPane().setDefaultButton(submit);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
    }

    /**
     * Mostra o ecrã e espera que o administrador seja criado ou que o
     * operador desista.
     *
     * @param desk o balcão onde se cria a conta
     * @return o administrador criado, já autenticado, ou {@code null} se o
     *         operador desistiu
     */
    public static User ask(ParkingDesk desk) {
        FirstAdminDialog screen = new FirstAdminDialog(desk);
        screen.dialog.setVisible(true);
        return screen.user;
    }

    private void submit() {
        String name = username.getText().strip();
        char[] secret = password.getPassword();
        char[] repeated = confirm.getPassword();
        try {
            String problem = Passwords.problem(secret, repeated);
            if (name.isEmpty()) {
                showProblem(Texts.get("user.error.blank"));
            } else if (problem != null) {
                showProblem(problem);
            } else {
                create(name, secret);
            }
        } finally {
            Arrays.fill(secret, ' ');
            Arrays.fill(repeated, ' ');
        }
    }

    private void create(String name, char[] secret) {
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            user = desk.createFirstAdmin(name, secret);
            dialog.dispose();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(dialog, Texts.format("error.save", e.getMessage()),
                    Texts.get("error.save.title"), JOptionPane.ERROR_MESSAGE);
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
    }

    private void showProblem(String text) {
        message.setText(text);
        password.setText("");
        confirm.setText("");
        password.requestFocusInWindow();
    }
}