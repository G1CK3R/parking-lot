package mz.uem.eda.parking.persistence.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Calcula e verifica o hash das palavras-passe com PBKDF2WithHmacSHA256, do
 * próprio JDK, sem bibliotecas externas.
 *
 * <p>Cada utilizador tem um sal próprio, aleatório, e o hash guarda-se com o
 * sal e o número de iterações em colunas separadas. Assim:
 * <ul>
 *   <li>duas pessoas com a mesma palavra-passe têm hashes diferentes;</li>
 *   <li>as iterações podem subir no futuro sem invalidar os hashes antigos,
 *       porque cada um é verificado com as iterações com que foi feito.</li>
 * </ul>
 *
 * <p>O sal e o hash guardam-se em Base64. A palavra-passe chega como
 * {@code char[]}, que é o que o {@code JPasswordField} do Swing devolve, para
 * quem chama poder apagá-la da memória depois de usar. Esta classe nunca a
 * guarda.
 */
public class PasswordHasher {

    /** Iterações para hashes novos: o valor recomendado pela OWASP para PBKDF2-HMAC-SHA256. */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random;

    /** Cria o hasher com as iterações recomendadas. */
    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * Cria o hasher com outro número de iterações. Só os testes usam valores
     * baixos, para correrem depressa.
     *
     * @param iterations as iterações para hashes novos, maior que 0
     * @throws IllegalArgumentException se {@code iterations} não for
     *         positivo
     */
    public PasswordHasher(int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations deve ser positivo: " + iterations);
        }
        this.iterations = iterations;
        this.random = new SecureRandom();
    }

    /** @return as iterações usadas nos hashes novos */
    public int getIterations() {
        return iterations;
    }

    /**
     * Gera um sal novo, aleatório.
     *
     * @return 16 bytes aleatórios, em Base64
     */
    public String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Calcula o hash da palavra-passe com este sal e estas iterações.
     *
     * @param password a palavra-passe
     * @param salt o sal, em Base64
     * @param iterations as iterações, maior que 0
     * @return o hash de 256 bits, em Base64
     * @throws IllegalArgumentException se o sal não for Base64 válido ou as
     *         iterações não forem positivas
     */
    public String hash(char[] password, String salt, int iterations) {
        Objects.requireNonNull(password, "password não pode ser null");
        Objects.requireNonNull(salt, "salt não pode ser null");
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations deve ser positivo: " + iterations);
        }
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        PBEKeySpec spec = new PBEKeySpec(password, saltBytes, iterations, KEY_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // Faz parte de qualquer JDK 8+: se faltar, o ambiente está avariado.
            throw new IllegalStateException(ALGORITHM + " não está disponível", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Diz se a palavra-passe corresponde ao hash guardado. A comparação leva
     * sempre o mesmo tempo, para não revelar quantos bytes coincidem.
     *
     * @param password a palavra-passe escrita
     * @param expectedHash o hash guardado, em Base64
     * @param salt o sal guardado, em Base64
     * @param iterations as iterações guardadas
     * @return {@code true} se corresponder
     */
    public boolean matches(char[] password, String expectedHash, String salt, int iterations) {
        Objects.requireNonNull(expectedHash, "expectedHash não pode ser null");
        byte[] actual = Base64.getDecoder().decode(hash(password, salt, iterations));
        byte[] expected = Base64.getDecoder().decode(expectedHash);
        return MessageDigest.isEqual(actual, expected);
    }
}