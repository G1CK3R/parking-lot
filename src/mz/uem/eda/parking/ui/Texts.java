package mz.uem.eda.parking.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Os textos da interface, lidos de {@code strings_pt.properties}. Todo o
 * texto que o operador vê passa por aqui; o código Java só conhece as
 * chaves.
 *
 * <p>Procura o ficheiro primeiro no classpath e depois em
 * {@code resources/strings_pt.properties} a partir da pasta onde o programa
 * arrancou, tal como o {@code ConnectionFactory} faz com a configuração.
 * Assim a aplicação arranca com o botão Run do VS Code mesmo que a pasta
 * {@code resources} não esteja no classpath.
 *
 * <p>O ficheiro é lido em UTF-8, para os acentos saírem certos.
 */
public final class Texts {

    private static final String FILE = "strings_pt.properties";
    private static final Locale PORTUGUESE = new Locale("pt", "MZ");

    private static ResourceBundle bundle;

    private Texts() {
    }

    /**
     * @param key a chave, por exemplo {@code "login.title"}
     * @return o texto; se a chave faltar, a própria chave entre pontos de
     *         exclamação, para se ver no ecrã o que falta sem o programa
     *         parar
     */
    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /**
     * Preenche um texto com {@code {0}}, {@code {1}}...
     *
     * @param key a chave
     * @param values os valores, pela ordem
     * @return o texto preenchido
     */
    public static String format(String key, Object... values) {
        return new MessageFormat(get(key), PORTUGUESE).format(values);
    }

    /**
     * Escreve um valor em meticais, por exemplo {@code 6000} centavos como
     * {@code "60,00 MZN"}.
     *
     * @param cents o valor em centavos
     * @return o valor formatado
     */
    public static String money(long cents) {
        return String.format(PORTUGUESE, "%,.2f MZN", cents / 100.0);
    }

    /** Carrega o ficheiro na primeira vez que é preciso. */
    private static ResourceBundle bundle() {
        if (bundle == null) {
            try {
                bundle = load();
            } catch (IOException e) {
                throw new IllegalStateException("não consegui ler " + FILE, e);
            }
        }
        return bundle;
    }

    private static ResourceBundle load() throws IOException {
        InputStream in = Texts.class.getClassLoader().getResourceAsStream(FILE);
        if (in == null) {
            Path file = Paths.get("resources", FILE);
            if (!Files.isRegularFile(file)) {
                throw new IOException("falta " + file);
            }
            in = Files.newInputStream(file);
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new PropertyResourceBundle(reader);
        }
    }
}