package mz.uem.eda.parking.persistence;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prepara uma base de dados de teste, {@code parking_lot_test}, a partir dos
 * mesmos {@code sql/01-schema.sql} e {@code sql/02-seed.sql} que a equipa
 * usa. Cada teste começa com a base acabada de criar.
 *
 * <p>Usa as credenciais de {@code resources/config.properties}. Se o
 * ficheiro não existir ou a base não responder, o teste é <b>saltado</b>
 * (não falha): quem não tem o MySQL ligado continua a ver os outros testes
 * verdes.
 *
 * <p>É pública porque os testes da {@code ParkingDesk}, no pacote
 * {@code service}, também precisam de uma base verdadeira.
 */
public final class TestDatabase {

    private static final String DB = "parking_lot";
    private static final String TEST_DB = "parking_lot_test";

    private TestDatabase() {
    }

    /**
     * Recria a base de teste e devolve uma fábrica de ligações para ela.
     * Salta o teste se não houver base de dados disponível.
     */
    public static ConnectionFactory fresh() {
        ConnectionFactory configured;
        try {
            configured = ConnectionFactory.fromConfigFile();
        } catch (IOException | IllegalArgumentException e) {
            assumeTrue(false, "sem config.properties: testes de base de dados saltados");
            return null;
        }
        String url = configured.getUrl();
        assumeTrue(url.contains("/" + DB), "a URL não aponta para a base " + DB);
        String serverUrl = url.replace("/" + DB, "/");
        String testUrl = url.replace("/" + DB, "/" + TEST_DB);

        ConnectionFactory server = configured.withUrl(serverUrl);
        try (Connection c = server.open(); Statement st = c.createStatement()) {
            runScript(st, Paths.get("sql", "01-schema.sql"));
            runScript(st, Paths.get("sql", "02-seed.sql"));
        } catch (SQLException e) {
            assumeTrue(false, "base de dados indisponível: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalStateException("não consegui ler os scripts de sql/", e);
        }
        return configured.withUrl(testUrl);
    }

    /** Uma contagem simples, só para os testes verificarem o que ficou gravado. */
    public static int count(ConnectionFactory connections, String sql) throws SQLException {
        try (Connection c = connections.open();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Corre um script, trocando o nome da base pelo da base de teste. */
    private static void runScript(Statement st, Path file) throws IOException, SQLException {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.strip().startsWith("--")) {
                sql.append(line).append('\n');
            }
        }
        for (String statement : sql.toString().replace(DB, TEST_DB).split(";")) {
            if (!statement.isBlank()) {
                st.execute(statement);
            }
        }
    }
}
