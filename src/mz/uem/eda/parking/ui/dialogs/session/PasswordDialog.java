package mz.uem.eda.parking.ui.dialogs.session;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;

/**
 * O ecrã da palavra-passe nova, de duas maneiras:
 * <ul>
 * <li><b>A própria</b> (menu Sessão, qualquer utilizador, RF03): pede a
 * palavra-passe actual, para que ninguém a mude num posto deixado aberto;</li>
 * <li><b>A de outro utilizador</b> (ecrã de utilizadores, só o
 * administrador, RF22): para quem se esqueceu da sua; não pede a antiga.</li>
 * </ul>
 * Em ambos os casos a nova escreve-se duas vezes.
 */
public final class PasswordDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    /** O utilizador a quem se repõe a palavra-passe, ou {@code null} para a própria. */
    private final String username;
    private final JPasswordField current = new JPasswordField(20);
    private final JPasswordField replacement = new JPasswordField(20);
    private final JPasswordField confirm = new JPasswordField(20);
    private final JLabel message = new JLabel();
    private boolean done;

    private PasswordDialog(Window owner, ParkingDesk desk, String username) {
        this.desk = desk;
        this.username = username;
        String title = username == null ? Texts.get("password.own.title")
                : Texts.format("password.reset.title", username);
        this.dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);

        JButton submit = new JButton(Texts.get("password.submit"));
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
        if (username == null) {
            form.addField(Texts.get("password.current"), current);
        } else {
            form.addText(Texts.format("password.reset.intro", username));
        }
        form.addField(Texts.get("password.new"), replacement);
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
     * Muda a palavra-passe de quem está autenticado.
     *
     * @param owner a janela por cima da qual aparece o ecrã
     * @param desk o balcão
     * @return {@code true} se a palavra-passe mudou
     */
    public static boolean askOwn(Window owner, ParkingDesk desk) {
        return show(new PasswordDialog(owner, desk, null));
    }

    /**
     * Dá uma palavra-passe nova a outro utilizador. Só para o administrador.
     *
     * @param owner a janela por cima da qual aparece o ecrã
     * @param desk o balcão
     * @param username o utilizador
     * @return {@code true} se a palavra-passe mudou
     */
    public static boolean askReset(Window owner, ParkingDesk desk, String username) {
        return show(new PasswordDialog(owner, desk, username));
    }

    private static boolean show(PasswordDialog screen) {
        screen.dialog.setVisible(true);
        return screen.done;
    }

    private void submit() {
        char[] old = current.getPassword();
        char[] secret = replacement.getPassword();
        char[] repeated = confirm.getPassword();
        try {
            String problem = Passwords.problem(secret, repeated);
            if (username == null && old.length == 0) {
                showProblem(Texts.get("password.error.current.blank"), current);
            } else if (problem != null) {
                showProblem(problem, replacement);
            } else {
                save(old, secret);
            }
        } finally {
            Arrays.fill(old, ' ');
            Arrays.fill(secret, ' ');
            Arrays.fill(repeated, ' ');
        }
    }

    private void save(char[] old, char[] secret) {
        boolean changed;
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            if (username == null) {
                changed = desk.changeOwnPassword(old, secret);
            } else {
                changed = desk.resetPassword(username, secret);
            }
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            dialog.dispose();
            return;
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }

        if (!changed) {
            if (username == null) {
                showProblem(Texts.get("password.error.current.wrong"), current);
            } else {
                showProblem(Texts.format("users.error.gone", username), replacement);
            }
            return;
        }
        done = true;
        dialog.dispose();
        JOptionPane.showMessageDialog(dialog.getOwner(), Texts.get("password.done"),
                dialog.getTitle(), JOptionPane.INFORMATION_MESSAGE);
    }

    /** Mostra o problema, limpa as palavras-passe e põe o cursor no campo a corrigir. */
    private void showProblem(String text, JPasswordField field) {
        message.setText(text);
        current.setText("");
        replacement.setText("");
        confirm.setText("");
        field.requestFocusInWindow();
    }
}
