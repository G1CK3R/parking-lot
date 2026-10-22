package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe a tarifa em vigor (tabela {@code tariffs}).
 *
 * <p>Como {@link Tariff} é imutável, mudar preços é inserir uma tarifa nova
 * e desactivar a anterior, na mesma transacção: há sempre exactamente uma
 * activa, e as antigas ficam como histórico. Os pagamentos apontam para a
 * tarifa com que foram calculados (ADR-031), por isso nenhuma se apaga.
 */
public class TariffRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     * @throws NullPointerException se {@code connections} for {@code null}
     */
    public TariffRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega a tarifa activa, para o {@code BillingService}.
     *
     * @return a tarifa em vigor, com o id da base
     * @throws SQLException se a leitura falhar, ou se não houver exactamente
     *         uma tarifa activa
     */
    public Tariff loadActive() throws SQLException {
        String sql = "SELECT id, free_minutes, fraction_minutes, hourly_rate_cents, daily_cap_cents "
                + "FROM tariffs WHERE active = TRUE";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("não há tarifa activa: correr sql/02-seed.sql");
            }
            Tariff tariff = new Tariff(rs.getInt("free_minutes"), rs.getInt("fraction_minutes"),
                    rs.getLong("hourly_rate_cents"), rs.getLong("daily_cap_cents"));
            tariff.setId(rs.getLong("id"));
            if (rs.next()) {
                throw new SQLException("há mais de uma tarifa activa");
            }
            return tariff;
        }
    }

    /**
     * Carrega todas as tarifas, em vigor e antigas, pela ordem em que foram
     * criadas, para ligar cada pagamento do histórico à sua (ADR-031). O
     * {@code ORDER BY id} só repõe a ordem de gravação, como em
     * {@code lane_slots}; não responde a nenhuma pergunta (ADR-012).
     *
     * @return as tarifas, a mais antiga à frente, com o id da base
     * @throws SQLException se a leitura falhar
     * @complexity O(t), em que t é o número de tarifas
     */
    public Queue<Tariff> loadAll() throws SQLException {
        String sql = "SELECT id, free_minutes, fraction_minutes, hourly_rate_cents, daily_cap_cents "
                + "FROM tariffs ORDER BY id";
        Queue<Tariff> tariffs = new LinkedQueue<>();
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Tariff tariff = new Tariff(rs.getInt("free_minutes"), rs.getInt("fraction_minutes"),
                        rs.getLong("hourly_rate_cents"), rs.getLong("daily_cap_cents"));
                tariff.setId(rs.getLong("id"));
                tariffs.enqueue(tariff);
            }
        }
        return tariffs;
    }

    /**
     * Põe em vigor uma tarifa nova (RF13): desactiva a actual e grava a nova,
     * na mesma transacção.
     *
     * @param tariff a tarifa nova, ainda sem id
     * @param now a hora da mudança
     * @throws IllegalStateException se a tarifa já tiver id
     * @throws SQLException se a gravação falhar (fica a tarifa anterior)
     */
    public void replaceActive(Tariff tariff, LocalDateTime now) throws SQLException {
        Objects.requireNonNull(tariff, "tariff não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");
        if (tariff.getId() != 0) {
            throw new IllegalStateException("tarifa já gravada: " + tariff.getId());
        }
        String insert = "INSERT INTO tariffs "
                + "(free_minutes, fraction_minutes, hourly_rate_cents, daily_cap_cents, active, created_at) "
                + "VALUES (?, ?, ?, ?, TRUE, ?)";
        long id;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement st = c.prepareStatement("UPDATE tariffs SET active = FALSE WHERE active = TRUE")) {
                    st.executeUpdate();
                }
                try (PreparedStatement st = c.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    st.setInt(1, tariff.getFreeMinutes());
                    st.setInt(2, tariff.getFractionMinutes());
                    st.setLong(3, tariff.getHourlyRateCents());
                    st.setLong(4, tariff.getDailyCapCents());
                    Sql.setTime(st, 5, now);
                    st.executeUpdate();
                    id = Sql.generatedKey(st);
                }
                c.commit();
            } catch (SQLException e) {
                Sql.rollbackQuietly(c, e);
                throw e;
            }
        }
        tariff.setId(id);
    }
}