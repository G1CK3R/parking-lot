package mz.uem.eda.parking.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Abre ligações JDBC à base de dados. É o único sítio do projecto que conhece
 * a URL, o utilizador e a palavra-passe da base.
 *
 * <p>Os dados vêm de {@code config.properties}, com três chaves:
 * {@code db.url}, {@code db.user} e {@code db.password}. Cada pessoa tem o
 * seu ficheiro, fora do repositório; o repositório só tem o
 * {@code config.properties.example}.
 *
 * <p>Não guarda ligações abertas: cada operação abre a sua com
 * {@link #open()} e fecha-a num {@code try}-with-resources. Para um posto de
 * atendimento, abrir uma ligação por operação é rápido o suficiente, e evita
 * ligações esquecidas abertas.
 */
public class ConnectionFactory {

    /** Nome do ficheiro de configuração, procurado no classpath e em {@code resources/}. */
    public static final String CONFIG_FILE = "config.properties";

    private final String url;
    private final String user;
    private final String password;

    /**
     * Cria a fábrica com os dados da ligação.
     *
     * @param url a URL JDBC, por exemplo
     *        {@code jdbc:mysql://localhost:3306/parking_lot}
     * @param user o utilizador da base
     * @param password a palavra-passe, que pode ser vazia
     * @throws NullPointerException se algum argumento for {@code null}
     * @throws IllegalArgumentException se a URL ou o utilizador estiverem em
     *         branco
     */
    public ConnectionFactory(String url, String user, String password) {
        Objects.requireNonNull(url, "url não pode ser null");
        Objects.requireNonNull(user, "user não pode ser null");
        Objects.requireNonNull(password, "password não pode ser null");
        if (url.isBlank() || user.isBlank()) {
            throw new IllegalArgumentException("db.url e db.user não podem estar em branco");
        }
        this.url = url.strip();
        this.user = user.strip();
        this.password = password;
    }

    /**
     * Cria a fábrica a partir das três chaves {@code db.url},
     * {@code db.user} e {@code db.password}.
     *
     * @param properties as propriedades lidas do ficheiro
     * @return a fábrica
     * @throws IllegalArgumentException se faltar alguma das chaves
     */
    public static ConnectionFactory fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties não pode ser null");
        return new ConnectionFactory(required(properties, "db.url"), required(properties, "db.user"),
                required(properties, "db.password"));
    }

    /**
     * Lê {@code config.properties}: primeiro no classpath (o
     * {@code launch.json} põe {@code resources/} no classpath), depois em
     * {@code resources/config.properties} a partir da pasta onde o programa
     * arrancou.
     *
     * @return a fábrica configurada
     * @throws IOException se o ficheiro não existir ou não se conseguir ler
     * @throws IllegalArgumentException se faltar alguma das chaves
     */
    public static ConnectionFactory fromConfigFile() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ConnectionFactory.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                properties.load(in);
                return fromProperties(properties);
            }
        }
        Path file = Paths.get("resources", CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            throw new IOException("falta " + file + ": copiar config.properties.example e preencher");
        }
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        }
        return fromProperties(properties);
    }

    /**
     * Abre uma ligação nova. Quem a pede fecha-a, de preferência num
     * {@code try}-with-resources.
     *
     * @return a ligação aberta, em modo {@code autoCommit}
     * @throws SQLException se a base não estiver acessível ou as credenciais
     *         estiverem erradas
     */
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Uma fábrica igual a esta, com as mesmas credenciais, mas para outra
     * URL. Só os testes a usam, para apontar para a base de teste.
     *
     * @param otherUrl a URL JDBC
     * @return a fábrica nova
     */
    ConnectionFactory withUrl(String otherUrl) {
        return new ConnectionFactory(otherUrl, user, password);
    }

    /** @return a URL JDBC, para mensagens de erro; nunca a palavra-passe */
    public String getUrl() {
        return url;
    }

    /** @return a URL e o utilizador; <b>nunca</b> a palavra-passe */
    @Override
    public String toString() {
        return user + " @ " + url;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("falta a chave " + key + " em " + CONFIG_FILE);
        }
        return value;
    }
}