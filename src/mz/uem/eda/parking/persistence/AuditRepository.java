package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import mz.uem.eda.parking.model.AuditEntry;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe o registo de auditoria (RF23). Cada linha grava-se logo a
 * seguir à operação que regista; o registo nunca se altera nem se apaga
 * (só uma restauração de cópia de segurança o substitui).
 */
public class AuditRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     */
    public AuditRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega o registo inteiro, pela ordem em que foi escrito.
     *
     * @return as linhas, da mais antiga para a mais recente
     * @throws SQLException se a leitura falhar
     */
    public Queue<AuditEntry> loadAll() throws SQLException {
        String sql = "SELECT id, at, username, action, detail FROM audit_log ORDER BY id";
        Queue<AuditEntry> entries = new LinkedQueue<>();
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                AuditEntry entry = new AuditEntry(Sql.getTime(rs, "at"), rs.getString("username"),
                        AuditEntry.Action.valueOf(rs.getString("action")), rs.getString("detail"));
                entry.setId(rs.getLong("id"));
                entries.enqueue(entry);
            }
        }
        return entries;
    }

    /**
     * Grava uma linha nova e preenche-lhe o id.
     *
     * @param entry a linha
     * @throws SQLException se a gravação falhar
     */
    public void insert(AuditEntry entry) throws SQLException {
        Objects.requireNonNull(entry, "entry não pode ser null");
        String sql = "INSERT INTO audit_log (at, username, action, detail) VALUES (?, ?, ?, ?)";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Sql.setTime(st, 1, entry.getAt());
            st.setString(2, entry.getUsername());
            st.setString(3, entry.getAction().name());
            st.setString(4, entry.getDetail());
            st.executeUpdate();
            entry.setId(Sql.generatedKey(st));
        }
    }
}
