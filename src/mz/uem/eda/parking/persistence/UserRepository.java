package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe os utilizadores (tabela {@code users}). Grava o hash, o sal
 * e as iterações, nunca a palavra-passe.
 *
 * <p>Apagar um utilizador é desactivá-lo: os pagamentos que cobrou continuam
 * a apontar para ele, e o histórico de caixa não perde o operador. Os
 * desactivados não voltam a ser carregados.
 */
public class UserRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     * @throws NullPointerException se {@code connections} for {@code null}
     */
    public UserRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega os utilizadores activos, pela ordem de criação, para o
     * {@code AuthService.load}.
     *
     * @return os utilizadores, com o id da base preenchido
     * @throws SQLException se a leitura falhar
     */
    public Queue<User> loadAll() throws SQLException {
        String sql = "SELECT id, username, password_hash, salt, iterations, role "
                + "FROM users WHERE active = TRUE ORDER BY id";
        Queue<User> users = new LinkedQueue<>();
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                User user = new User(rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("salt"), rs.getInt("iterations"), User.Role.valueOf(rs.getString("role")));
                user.setId(rs.getLong("id"));
                users.enqueue(user);
            }
        }
        return users;
    }

    /**
     * Grava um utilizador novo e preenche-lhe o id.
     *
     * @param user o utilizador criado pelo {@code AuthService}, ainda sem id
     * @throws IllegalStateException se o utilizador já tiver id
     * @throws SQLException se a gravação falhar, por exemplo com um nome
     *         repetido
     */
    public void insert(User user) throws SQLException {
        Objects.requireNonNull(user, "user não pode ser null");
        if (user.getId() != 0) {
            throw new IllegalStateException("utilizador já gravado: " + user);
        }
        String sql = "INSERT INTO users (username, password_hash, salt, iterations, role) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, user.getUsername());
            st.setString(2, user.getPasswordHash());
            st.setString(3, user.getSalt());
            st.setInt(4, user.getIterations());
            st.setString(5, user.getRole().name());
            st.executeUpdate();
            user.setId(Sql.generatedKey(st));
        }
    }

    /**
     * Grava a palavra-passe nova (hash, sal e iterações) depois de um
     * {@code changePassword} ou {@code resetPassword}.
     *
     * @param user o utilizador já gravado
     * @throws SQLException se a gravação falhar
     */
    public void updateCredentials(User user) throws SQLException {
        requireStored(user);
        String sql = "UPDATE users SET password_hash = ?, salt = ?, iterations = ? WHERE id = ?";
        try (Connection c = connections.open(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, user.getPasswordHash());
            st.setString(2, user.getSalt());
            st.setInt(3, user.getIterations());
            st.setLong(4, user.getId());
            st.executeUpdate();
        }
    }

    /**
     * Desactiva o utilizador depois de um {@code AuthService.remove}.
     *
     * @param user o utilizador já gravado
     * @throws SQLException se a gravação falhar
     */
    public void deactivate(User user) throws SQLException {
        requireStored(user);
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement("UPDATE users SET active = FALSE WHERE id = ?")) {
            st.setLong(1, user.getId());
            st.executeUpdate();
        }
    }

    private static void requireStored(User user) {
        Objects.requireNonNull(user, "user não pode ser null");
        if (user.getId() == 0) {
            throw new IllegalStateException("utilizador ainda não gravado: " + user);
        }
    }
}