package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import mz.uem.eda.parking.model.ParkPolicies;

/**
 * Guarda e repõe as políticas escolhidas pelo administrador, na linha única
 * de {@code park_state} (colunas {@code placement_policy},
 * {@code service_policy} e {@code preferential_turns}).
 *
 * <p>Guarda os nomes das constantes de {@link ParkPolicies}, como os outros
 * enumerados do esquema. Uma base criada com o {@code 02-seed.sql} começa
 * com os valores por omissão das colunas: colocação pela hora de saída e
 * rotação com três preferenciais seguidos.
 */
public class PolicyRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     * @throws NullPointerException se {@code connections} for {@code null}
     */
    public PolicyRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega as políticas em vigor.
     *
     * @return as políticas gravadas
     * @throws SQLException se a leitura falhar, se faltar a linha de
     *         {@code park_state} (o {@code 02-seed.sql} não correu), ou se a
     *         linha tiver um nome de política que o programa não conhece
     */
    public ParkPolicies load() throws SQLException {
        String sql = "SELECT placement_policy, service_policy, preferential_turns FROM park_state WHERE id = 1";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("falta a linha de park_state: correr sql/02-seed.sql");
            }
            String placement = rs.getString("placement_policy");
            String service = rs.getString("service_policy");
            try {
                return new ParkPolicies(ParkPolicies.Placement.valueOf(placement),
                        ParkPolicies.Service.valueOf(service), rs.getInt("preferential_turns"));
            } catch (IllegalArgumentException e) {
                throw new SQLException("políticas inválidas em park_state: " + placement + ", " + service
                        + ", " + rs.getInt("preferential_turns"), e);
            }
        }
    }

    /**
     * Grava as políticas novas.
     *
     * @param policies as políticas escolhidas
     * @throws SQLException se a gravação falhar, ou se faltar a linha de
     *         {@code park_state}
     */
    public void save(ParkPolicies policies) throws SQLException {
        Objects.requireNonNull(policies, "policies não pode ser null");
        String sql = "UPDATE park_state SET placement_policy = ?, service_policy = ?, preferential_turns = ? "
                + "WHERE id = 1";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, policies.getPlacement().name());
            st.setString(2, policies.getService().name());
            st.setInt(3, policies.getPreferentialTurns());
            if (st.executeUpdate() != 1) {
                throw new SQLException("falta a linha de park_state: correr sql/02-seed.sql");
            }
        }
    }
}
