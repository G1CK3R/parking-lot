package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe as reservas (tabela {@code reservations}).
 *
 * <p>Repõem-se só as activas, pela ordem de criação, que é a ordem de
 * expiração da fila do {@code ReservationService}. As que expiraram com o
 * programa fechado são marcadas como expiradas ao carregar.
 *
 * <p>O check-in grava-se na mesma transacção que a entrada do carro, em
 * {@link SessionRepository#recordEntry}. As expirações durante o
 * funcionamento não precisam de ser gravadas uma a uma: ao reabrir, as
 * vencidas são expiradas de novo.
 */
public class ReservationRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     * @throws NullPointerException se {@code connections} for {@code null}
     */
    public ReservationRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Marca como expiradas as reservas vencidas e carrega as que continuam
     * activas, pela ordem de criação, para o {@code ReservationService.load}.
     *
     * @param now a hora do arranque
     * @return as reservas activas, com o id da base
     * @throws SQLException se a leitura falhar
     */
    public Queue<Reservation> loadActive(LocalDateTime now) throws SQLException {
        Objects.requireNonNull(now, "now não pode ser null");
        Queue<Reservation> active = new LinkedQueue<>();
        String expire = "UPDATE reservations SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at <= ?";
        String select = "SELECT id, plate, stay_class, estimated_minutes, created_at, expires_at "
                + "FROM reservations WHERE status = 'ACTIVE' ORDER BY created_at, id";
        try (Connection c = connections.open()) {
            try (PreparedStatement st = c.prepareStatement(expire)) {
                Sql.setTime(st, 1, now);
                st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement(select); ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation(rs.getString("plate"),
                            StayClass.valueOf(rs.getString("stay_class")), rs.getInt("estimated_minutes"),
                            Sql.getTime(rs, "created_at"), Sql.getTime(rs, "expires_at"));
                    reservation.setId(rs.getLong("id"));
                    active.enqueue(reservation);
                }
            }
        }
        return active;
    }

    /**
     * Grava uma reserva nova e preenche-lhe o id.
     *
     * @param reservation a reserva devolvida pelo {@code ReservationService}
     * @throws IllegalStateException se a reserva já tiver id
     * @throws SQLException se a gravação falhar
     */
    public void insert(Reservation reservation) throws SQLException {
        Objects.requireNonNull(reservation, "reservation não pode ser null");
        if (reservation.getId() != 0) {
            throw new IllegalStateException("reserva já gravada: " + reservation);
        }
        String sql = "INSERT INTO reservations "
                + "(plate, stay_class, estimated_minutes, created_at, expires_at, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, reservation.getPlate());
            st.setString(2, reservation.getStayClass().name());
            st.setInt(3, reservation.getEstimatedMinutes());
            Sql.setTime(st, 4, reservation.getCreatedAt());
            Sql.setTime(st, 5, reservation.getExpiresAt());
            st.setString(6, reservation.getStatus().name());
            st.executeUpdate();
            reservation.setId(Sql.generatedKey(st));
        }
    }
}