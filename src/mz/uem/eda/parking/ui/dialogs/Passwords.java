package mz.uem.eda.parking.ui.dialogs;

import java.util.Arrays;

import mz.uem.eda.parking.service.AuthService;
import mz.uem.eda.parking.ui.Texts;

/**
 * A verificação comum aos ecrãs que pedem uma palavra-passe nova duas
 * vezes: o primeiro administrador, o utilizador novo, a palavra-passe
 * reposta pelo administrador e a mudança da própria palavra-passe.
 *
 * <p>O {@link AuthService} volta a verificar o comprimento ao gravar; esta
 * verificação existe para o ecrã dizer o problema numa frase do
 * {@code strings_pt.properties} antes de chamar a {@code ParkingDesk}.
 */
final class Passwords {

    private Passwords() {
    }

    /**
     * @param secret a palavra-passe nova
     * @param repeated a mesma palavra-passe, escrita outra vez
     * @return a frase a mostrar ao operador, ou {@code null} se estiver tudo
     *         certo
     */
    static String problem(char[] secret, char[] repeated) {
        if (secret.length < AuthService.MIN_PASSWORD_LENGTH) {
            return Texts.format("password.error.short", String.valueOf(AuthService.MIN_PASSWORD_LENGTH));
        }
        if (!Arrays.equals(secret, repeated)) {
            return Texts.get("password.error.mismatch");
        }
        return null;
    }
}
