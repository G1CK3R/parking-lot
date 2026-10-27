package mz.uem.eda.parking.ui.dialogs;

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;

/**
 * O ecrã de entrada: nome de utilizador e palavra-passe. Fica aberto até as
 * credenciais estarem certas ou o operador desistir.
 *
 * <p>Com credenciais erradas, diz só que o utilizador ou a palavra-passe
 * estão errados, sem dizer qual: dizer que o utilizador não existe ajudava
 * quem anda a adivinhar nomes.
 *
 * <p>A palavra-passe é lida como {@code char[]} e apagada logo a seguir,
 * para não ficar numa {@code String} na memória.
 */
public class LoginDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JLabel message = new JLabel();
    private User user;

    private LoginDialog(ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog((Frame) null, Texts.get("login.title"), true);

        JButton submit = new JButton(Texts.get("login.submit"));
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
        form.addField(Texts.get("login.username"), username);
        form.addField(Texts.get("login.password"), password);
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
     * Mostra o ecrã e espera que o operador entre ou desista.
     *
     * @param desk o balcão onde se valida a entrada
     * @return o utilizador autenticado, ou {@code null} se o operador
     *         carregou em Sair ou fechou o ecrã
     */
    public static User ask(ParkingDesk desk) {
        LoginDialog screen = new LoginDialog(desk);
        screen.dialog.setVisible(true);
        return screen.user;
    }

    private void submit() {
        char[] secret = password.getPassword();
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            user = desk.login(username.getText().strip(), secret);
        } finally {
            Arrays.fill(secret, ' ');
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        if (user == null) {
            message.setText(Texts.get("login.failed"));
            password.setText("");
            password.requestFocusInWindow();
            return;
        }
        dialog.dispose();
    }
}