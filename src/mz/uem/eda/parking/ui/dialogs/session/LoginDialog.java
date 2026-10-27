package mz.uem.eda.parking.ui.dialogs.session;

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.access.AuthService;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;

/**
 * O ecrã de entrada: nome de utilizador e palavra-passe. Fica aberto até as
 * credenciais estarem certas ou o operador desistir.
 *
 * <p>Com credenciais erradas, diz só que o utilizador ou a palavra-passe
 * estão errados, sem dizer qual: dizer que o utilizador não existe ajudava
 * quem anda a adivinhar nomes.
 *
 * <p>Cinco palavras-passe erradas seguidas bloqueiam a conta durante 15
 * minutos (RF03). Aí o ecrã diz até quando, para o operador não continuar a
 * tentar; o administrador pode desbloqueá-la antes, no ecrã de
 * utilizadores.
 *
 * <p>A palavra-passe é lida como {@code char[]} e apagada logo a seguir,
 * para não ficar numa {@code String} na memória.
 */
public class LoginDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

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
        String name = username.getText().strip();
        LocalDateTime now = LocalDateTime.now();
        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            user = desk.login(name, secret, now);
        } catch (SQLException e) {
            // O login pode ter dado certo e só o registo ter falhado; mesmo
            // assim não se entra, porque a base não está a gravar.
            user = null;
            Errors.saveFailed(dialog, e);
            return;
        } finally {
            Arrays.fill(secret, ' ');
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        if (user == null) {
            LocalDateTime lockedUntil = desk.lockedUntil(name, now);
            if (lockedUntil != null) {
                message.setText(Texts.format("login.locked", lockedUntil.format(TIME),
                        String.valueOf(AuthService.MAX_FAILED_ATTEMPTS)));
            } else {
                message.setText(Texts.get("login.failed"));
            }
            dialog.pack();
            password.setText("");
            password.requestFocusInWindow();
            return;
        }
        dialog.dispose();
    }
}