package mz.uem.eda.parking.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Cópia de segurança e restauro da base de dados (RF24), num ficheiro de
 * texto próprio do programa.
 *
 * <p><b>O formato.</b> A primeira linha identifica o ficheiro
 * ({@value #HEADER}). Depois, para cada tabela, uma linha {@code TABLE nome},
 * uma linha {@code COLUMNS} com os nomes das colunas e uma linha por registo,
 * com os valores separados por tabulações; {@code \N} quer dizer
 * {@code NULL}, e as tabulações, mudanças de linha e barras dentro dos
 * valores escrevem-se {@code \t}, {@code \n}, {@code \r} e {@code \\}. A
 * última linha é {@code END}.
 *
 * <p><b>Restaurar substitui tudo</b>, numa só transacção: apaga as tabelas
 * pela ordem inversa das chaves estrangeiras e insere as linhas do ficheiro
 * pela ordem certa, com {@code PreparedStatement}, como o resto da camada.
 * Se alguma coisa falhar, nada muda. Os nomes das tabelas e das colunas do
 * ficheiro são conferidos com os da base antes de se usarem.
 *
 * <p>Depois de restaurar, o estado em memória do programa já não
 * corresponde à base: a interface pede para fechar e voltar a abrir.
 */
public class BackupRepository {

    /** A primeira linha de um ficheiro de cópia de segurança. */
    public static final String HEADER = "PARKING-LOT-BACKUP 1";

    /** As tabelas, pela ordem das chaves estrangeiras: cada uma só aponta para as anteriores. */
    private static final String[] TABLES = { "users", "customers", "vehicles", "lanes", "parking_sessions",
        "lane_slots", "tariffs", "payments", "reservations", "park_state", "monthly_passes", "audit_log" };

    private static final String NULL = "\\N";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     */
    public BackupRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Grava todas as tabelas num ficheiro.
     *
     * @param file o ficheiro a criar (substitui se existir)
     * @return quantas linhas foram gravadas, somando todas as tabelas
     * @throws SQLException se a leitura da base falhar
     * @throws IOException se a escrita do ficheiro falhar
     */
    public int export(Path file) throws SQLException, IOException {
        Objects.requireNonNull(file, "file não pode ser null");
        int rows = 0;
        try (Connection c = connections.open();
                Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(HEADER);
            out.write("\n");
            for (String table : TABLES) {
                try (PreparedStatement st = c.prepareStatement("SELECT * FROM " + table + " ORDER BY 1");
                        ResultSet rs = st.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columns = meta.getColumnCount();
                    out.write("TABLE " + table + "\n");
                    StringBuilder names = new StringBuilder("COLUMNS");
                    for (int i = 1; i <= columns; i++) {
                        names.append('\t').append(meta.getColumnName(i));
                    }
                    out.write(names.append('\n').toString());
                    while (rs.next()) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 1; i <= columns; i++) {
                            if (i > 1) {
                                line.append('\t');
                            }
                            line.append(valueOf(rs, i, meta.getColumnType(i)));
                        }
                        out.write(line.append('\n').toString());
                        rows++;
                    }
                }
            }
            out.write("END\n");
        }
        return rows;
    }

    /**
     * Substitui o conteúdo da base pelo de um ficheiro de cópia de segurança.
     *
     * @param file o ficheiro, feito por {@link #export}
     * @return quantas linhas foram repostas
     * @throws IOException se o ficheiro não se ler ou não tiver o formato
     *         certo (a base fica como estava)
     * @throws SQLException se a gravação falhar (a base fica como estava)
     */
    public int restore(Path file) throws IOException, SQLException {
        Objects.requireNonNull(file, "file não pode ser null");
        Queue<TableData> tables = read(file);
        int rows = 0;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                for (TableData table : tables) {
                    checkColumns(c, table);
                }
                for (int i = TABLES.length - 1; i >= 0; i--) {
                    try (PreparedStatement st = c.prepareStatement("DELETE FROM " + TABLES[i])) {
                        st.executeUpdate();
                    }
                }
                for (TableData table : tables) {
                    rows += insertAll(c, table);
                }
                c.commit();
            } catch (SQLException e) {
                Sql.rollbackQuietly(c, e);
                throw e;
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // Ler o ficheiro
    // ------------------------------------------------------------------

    /** O conteúdo de uma tabela no ficheiro: o nome, as colunas e as linhas. */
    private static final class TableData {
        private final String name;
        private String[] columns;
        private final Queue<String[]> rows = new LinkedQueue<>();

        TableData(String name) {
            this.name = name;
        }
    }

    /** Lê o ficheiro inteiro e confere o formato, antes de mexer na base. */
    private static Queue<TableData> read(Path file) throws IOException {
        Queue<TableData> tables = new LinkedQueue<>();
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            if (!HEADER.equals(line)) {
                throw new IOException("não é uma cópia de segurança do parque: " + file.getFileName());
            }
            TableData current = null;
            boolean ended = false;
            int expected = 0;
            while ((line = in.readLine()) != null) {
                if (line.equals("END")) {
                    ended = true;
                    break;
                }
                if (line.startsWith("TABLE ")) {
                    String name = line.substring(6).strip();
                    if (expected >= TABLES.length || !TABLES[expected].equals(name)) {
                        throw new IOException("tabela fora do sítio no ficheiro: " + name);
                    }
                    expected++;
                    current = new TableData(name);
                    tables.enqueue(current);
                } else if (line.startsWith("COLUMNS")) {
                    if (current == null || current.columns != null) {
                        throw new IOException("linha COLUMNS fora do sítio");
                    }
                    current.columns = line.substring(8).split("\t", -1);
                } else {
                    if (current == null || current.columns == null) {
                        throw new IOException("linha de dados antes de TABLE e COLUMNS");
                    }
                    String[] values = line.split("\t", -1);
                    if (values.length != current.columns.length) {
                        throw new IOException("linha com " + values.length + " valores em vez de "
                                + current.columns.length + " na tabela " + current.name);
                    }
                    for (int i = 0; i < values.length; i++) {
                        values[i] = unescape(values[i]);
                    }
                    current.rows.enqueue(values);
                }
            }
            if (!ended || expected != TABLES.length) {
                throw new IOException("o ficheiro está incompleto");
            }
        }
        return tables;
    }

    /** Confere que todas as colunas do ficheiro existem na tabela da base. */
    private static void checkColumns(Connection c, TableData table) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT * FROM " + table.name + " WHERE 1 = 0");
                ResultSet rs = st.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            for (String column : table.columns) {
                boolean found = false;
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    if (meta.getColumnName(i).equalsIgnoreCase(column)) {
                        found = true;
                    }
                }
                if (!found) {
                    throw new SQLException("a tabela " + table.name + " não tem a coluna " + column
                            + ": a cópia é de outra versão do esquema");
                }
            }
        }
    }

    private static int insertAll(Connection c, TableData table) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table.name).append(" (");
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < table.columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
                marks.append(", ");
            }
            sql.append(table.columns[i]);
            marks.append('?');
        }
        sql.append(") VALUES (").append(marks).append(')');
        int count = 0;
        try (PreparedStatement st = c.prepareStatement(sql.toString())) {
            for (String[] row : table.rows) {
                for (int i = 0; i < row.length; i++) {
                    if (row[i] == null) {
                        st.setNull(i + 1, Types.VARCHAR);
                    } else {
                        st.setString(i + 1, row[i]);
                    }
                }
                st.executeUpdate();
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Valores
    // ------------------------------------------------------------------

    /** O valor de uma coluna como texto do ficheiro, já com os escapes. */
    private static String valueOf(ResultSet rs, int column, int type) throws SQLException {
        String text;
        if (type == Types.BIT || type == Types.BOOLEAN || type == Types.TINYINT) {
            int number = rs.getInt(column);
            text = rs.wasNull() ? null : String.valueOf(number);
        } else if (type == Types.TIMESTAMP) {
            LocalDateTime time = rs.getObject(column, LocalDateTime.class);
            text = time == null ? null : time.format(DATE_TIME);
        } else if (type == Types.DATE) {
            LocalDate day = rs.getObject(column, LocalDate.class);
            text = day == null ? null : day.toString();
        } else {
            text = rs.getString(column);
        }
        return text == null ? NULL : escape(text);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                out.append("\\\\");
            } else if (ch == '\t') {
                out.append("\\t");
            } else if (ch == '\n') {
                out.append("\\n");
            } else if (ch == '\r') {
                out.append("\\r");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    /** O contrário de {@link #escape}; {@code \N} dá {@code null}. */
    private static String unescape(String value) {
        if (value.equals(NULL)) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 't') {
                    out.append('\t');
                } else if (next == 'n') {
                    out.append('\n');
                } else if (next == 'r') {
                    out.append('\r');
                } else {
                    out.append(next);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
