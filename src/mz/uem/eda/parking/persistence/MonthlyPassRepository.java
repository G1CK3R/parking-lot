package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.YearMonth;
import java.util.Objects;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe as avenças mensais (RF16). Como os pagamentos, só guarda e
 * repõe: procurar a avença de uma matrícula é trabalho do serviço, com uma
 * busca sequencial.
 */
public class MonthlyPassRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     */
    public MonthlyPassRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega todas as avenças, pela ordem em que foram vendidas, cada uma
     * com o operador que a vendeu (mesmo que entretanto tenha sido retirado).
     *
     * @return as avenças, com o id da base
     * @throws SQLException se a leitura falhar
     */
    public Queue<MonthlyPass> loadAll() throws SQLException {
        String sql = "SELECT m.id, m.customer_name, m.phone, m.plate, m.valid_from, m.amount_cents, m.method, "
                + "m.paid_at, u.id AS user_id, u.username, u.password_hash, u.salt, u.iterations, u.role "
                + "FROM monthly_passes m JOIN users u ON u.id = m.user_id ORDER BY m.id";
        Queue<MonthlyPass> passes = new LinkedQueue<>();
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                User operator = new User(rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("salt"), rs.getInt("iterations"), User.Role.valueOf(rs.getString("role")));
                operator.setId(rs.getLong("user_id"));
                MonthlyPass pass = new MonthlyPass(rs.getString("customer_name"), rs.getString("phone"),
                        rs.getString("plate"), YearMonth.from(Sql.getDate(rs, "valid_from")),
                        rs.getLong("amount_cents"), Payment.Method.valueOf(rs.getString("method")),
                        Sql.getTime(rs, "paid_at"), operator);
                pass.setId(rs.getLong("id"));
                passes.enqueue(pass);
            }
        }
        return passes;
    }

    /**
     * Grava uma avença nova e preenche-lhe o id.
     *
     * @param pass a avença vendida
     * @throws IllegalStateException se já tiver sido gravada, ou se o
     *         operador ainda não estiver gravado
     * @throws SQLException se a gravação falhar
     */
    public void insert(MonthlyPass pass) throws SQLException {
        Objects.requireNonNull(pass, "pass não pode ser null");
        if (pass.getId() != 0) {
            throw new IllegalStateException("avença já gravada: " + pass);
        }
        if (pass.getOperator().getId() == 0) {
            throw new IllegalStateException("operador ainda não gravado: " + pass.getOperator());
        }
        String sql = "INSERT INTO monthly_passes (customer_name, phone, plate, valid_from, valid_until, "
                + "amount_cents, method, paid_at, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, pass.getCustomerName());
            st.setString(2, pass.getPhone());
            st.setString(3, pass.getPlate());
            Sql.setDate(st, 4, pass.getValidFrom());
            Sql.setDate(st, 5, pass.getValidUntil());
            st.setLong(6, pass.getAmountCents());
            st.setString(7, pass.getMethod().name());
            Sql.setTime(st, 8, pass.getPaidAt());
            st.setLong(9, pass.getOperator().getId());
            st.executeUpdate();
            pass.setId(Sql.generatedKey(st));
        }
    }
}
