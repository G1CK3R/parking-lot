package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import mz.uem.eda.parking.model.Customer;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.Vehicle;

/**
 * Grava as entradas e as saídas: sessões, talões, viaturas, clientes e
 * pagamentos (tabelas {@code parking_sessions}, {@code vehicles},
 * {@code customers}, {@code payments} e {@code park_state}).
 *
 * <p><b>Cada operação é uma transacção.</b> Uma entrada grava a sessão, a
 * baia onde o carro ficou e o número do próximo talão de uma vez; uma saída
 * grava o pagamento, fecha a sessão e regrava a baia de uma vez (RNF de
 * fiabilidade: saída e pagamento numa única transacção atómica). Se alguma
 * parte falhar, nada fica gravado.
 *
 * <p>O fluxo é: o serviço muda as pilhas em memória e devolve a sessão; logo
 * a seguir, quem coordena (a interface) chama {@link #recordEntry} ou
 * {@link #recordExit} com o resultado. Se a gravação falhar, a memória e a
 * base ficam diferentes: a interface avisa o operador, e reabrir o programa
 * repõe o último estado gravado.
 *
 * <p>Os ids da base só são escritos nos objectos depois do {@code commit},
 * para nunca ficar em memória um id de uma linha que foi desfeita.
 */
public class SessionRepository {

    private final ConnectionFactory connections;
    private final LaneRepository lanes;

    /**
     * @param connections a fábrica de ligações
     * @param lanes o repositório das baias, para regravar a baia na mesma
     *        transacção
     * @throws NullPointerException se algum argumento for {@code null}
     */
    public SessionRepository(ConnectionFactory connections, LaneRepository lanes) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
        this.lanes = Objects.requireNonNull(lanes, "lanes não pode ser null");
    }

    /**
     * Grava uma entrada, numa só transacção: a viatura (e o cliente) se
     * ainda não existirem, a sessão, o conteúdo da baia onde o carro ficou, o
     * número do próximo talão e, se o carro tinha reserva, o check-in.
     *
     * @param session a sessão devolvida pelo {@code EntryService} ou pelo
     *        {@code ReservationService}
     * @param lane a baia onde o carro ficou, já com ele no topo
     * @param nextTicketNumber o {@code EntryService.getNextTicketNumber()}
     *        depois da entrada
     * @param checkedIn a reserva que deu lugar a esta entrada, ou
     *        {@code null} se o carro não tinha reserva
     * @throws IllegalArgumentException se a sessão não estiver nesta baia
     * @throws SQLException se a gravação falhar (nada fica gravado)
     */
    public void recordEntry(ParkingSession session, Lane lane, int nextTicketNumber,
            Reservation checkedIn) throws SQLException {
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(lane, "lane não pode ser null");
        if (!session.getLaneCode().equals(lane.getCode())) {
            throw new IllegalArgumentException("a sessão " + session + " não está na baia " + lane.getCode());
        }
        Vehicle vehicle = session.getVehicle();
        Customer customer = vehicle.getCustomer();
        long customerId = 0;
        long vehicleId;
        long sessionId;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                if (customer != null && customer.getId() == 0) {
                    customerId = insertCustomer(c, customer);
                }
                vehicleId = findOrInsertVehicle(c, vehicle, customer == null ? 0
                        : (customer.getId() != 0 ? customer.getId() : customerId));
                sessionId = insertSession(c, session, vehicleId);
                lanes.saveSlots(c, lane);
                saveNextTicketNumber(c, nextTicketNumber);
                if (checkedIn != null) {
                    updateReservationStatus(c, checkedIn);
                }
                c.commit();
            } catch (SQLException e) {
                Sql.rollbackQuietly(c, e);
                throw e;
            }
        }
        if (customerId != 0) {
            customer.setId(customerId);
        }
        if (vehicle.getId() == 0) {
            vehicle.setId(vehicleId);
        }
        session.setId(sessionId);
    }

    /**
     * Grava uma saída, numa só transacção: o pagamento, o fecho da sessão
     * (hora de saída, manobras, estado) e o conteúdo da baia sem o carro.
     *
     * @param session a sessão já fechada pelo {@code ExitService}
     * @param lane a baia de onde o carro saiu, já sem ele
     * @param payment o pagamento registado pelo {@code BillingService} antes
     *        da saída
     * @throws IllegalArgumentException se a sessão ainda estiver activa, se
     *         o pagamento for de outra sessão, ou se o operador ainda não
     *         estiver gravado
     * @throws SQLException se a gravação falhar (nada fica gravado)
     */
    public void recordExit(ParkingSession session, Lane lane, Payment payment) throws SQLException {
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(lane, "lane não pode ser null");
        Objects.requireNonNull(payment, "payment não pode ser null");
        if (session.isActive()) {
            throw new IllegalArgumentException("a sessão ainda está activa: " + session);
        }
        if (!payment.getSession().equals(session)) {
            throw new IllegalArgumentException("o pagamento é de outra sessão: " + payment.getSession());
        }
        if (payment.getOperator().getId() == 0) {
            throw new IllegalArgumentException("operador ainda não gravado: " + payment.getOperator());
        }
        long paymentId;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                paymentId = insertPayment(c, payment);
                closeSession(c, session);
                lanes.saveSlots(c, lane);
                c.commit();
            } catch (SQLException e) {
                Sql.rollbackQuietly(c, e);
                throw e;
            }
        }
        payment.setId(paymentId);
    }

    /**
     * O número do próximo talão, para o {@code EntryService} continuar a
     * numeração depois de reabrir.
     *
     * @return o número gravado na última entrada (1 numa base nova)
     * @throws SQLException se a leitura falhar, ou se faltar a linha de
     *         {@code park_state} (o {@code 02-seed.sql} não correu)
     */
    public int loadNextTicketNumber() throws SQLException {
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement("SELECT next_ticket_no FROM park_state WHERE id = 1");
                ResultSet rs = st.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("falta a linha de park_state: correr sql/02-seed.sql");
            }
            return rs.getInt(1);
        }
    }

    private static long insertCustomer(Connection c, Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (name, phone, preferential) VALUES (?, ?, ?)";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, customer.getName());
            st.setString(2, customer.getPhone());
            st.setBoolean(3, customer.isPreferential());
            st.executeUpdate();
            return Sql.generatedKey(st);
        }
    }

    /**
     * O id da viatura: a que já está na base com esta matrícula, ou uma
     * nova. Uma viatura que já existe fica como está (o cliente associado
     * não é mudado aqui).
     */
    private static long findOrInsertVehicle(Connection c, Vehicle vehicle, long customerId) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT id FROM vehicles WHERE plate = ?")) {
            st.setString(1, vehicle.getPlate());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        String sql = "INSERT INTO vehicles (plate, vehicle_type, customer_id) VALUES (?, ?, ?)";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, vehicle.getPlate());
            st.setString(2, vehicle.getType().name());
            if (customerId == 0) {
                st.setNull(3, java.sql.Types.BIGINT);
            } else {
                st.setLong(3, customerId);
            }
            st.executeUpdate();
            return Sql.generatedKey(st);
        }
    }

    private static long insertSession(Connection c, ParkingSession session, long vehicleId) throws SQLException {
        String sql = "INSERT INTO parking_sessions "
                + "(ticket_no, vehicle_id, lane_id, stay_class, estimated_minutes, entry_at, status) "
                + "SELECT ?, ?, id, ?, ?, ?, ? FROM lanes WHERE code = ?";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setInt(1, session.getTicketNumber());
            st.setLong(2, vehicleId);
            st.setString(3, session.getStayClass().name());
            st.setInt(4, session.getEstimatedMinutes());
            Sql.setTime(st, 5, session.getEntryTime());
            st.setString(6, session.getStatus().name());
            st.setString(7, session.getLaneCode());
            if (st.executeUpdate() != 1) {
                throw new SQLException("baia desconhecida: " + session.getLaneCode());
            }
            return Sql.generatedKey(st);
        }
    }

    private static long insertPayment(Connection c, Payment payment) throws SQLException {
        String sql = "INSERT INTO payments (session_id, amount_cents, method, paid_at, user_id) "
                + "SELECT id, ?, ?, ?, ? FROM parking_sessions WHERE ticket_no = ?";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, payment.getAmountCents());
            st.setString(2, payment.getMethod().name());
            Sql.setTime(st, 3, payment.getPaidAt());
            st.setLong(4, payment.getOperator().getId());
            st.setInt(5, payment.getSession().getTicketNumber());
            if (st.executeUpdate() != 1) {
                throw new SQLException("sessão por gravar: " + payment.getSession());
            }
            return Sql.generatedKey(st);
        }
    }

    private static void closeSession(Connection c, ParkingSession session) throws SQLException {
        String sql = "UPDATE parking_sessions SET exit_at = ?, moves_count = ?, status = ? WHERE ticket_no = ?";
        try (PreparedStatement st = c.prepareStatement(sql)) {
            Sql.setTime(st, 1, session.getExitTime());
            st.setInt(2, session.getManoeuvres());
            st.setString(3, session.getStatus().name());
            st.setInt(4, session.getTicketNumber());
            if (st.executeUpdate() != 1) {
                throw new SQLException("sessão por gravar: " + session);
            }
        }
    }

    private static void saveNextTicketNumber(Connection c, int next) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("UPDATE park_state SET next_ticket_no = ? WHERE id = 1")) {
            st.setInt(1, next);
            if (st.executeUpdate() != 1) {
                throw new SQLException("falta a linha de park_state: correr sql/02-seed.sql");
            }
        }
    }

    private static void updateReservationStatus(Connection c, Reservation reservation) throws SQLException {
        if (reservation.getId() == 0) {
            throw new SQLException("reserva por gravar: " + reservation);
        }
        try (PreparedStatement st = c.prepareStatement("UPDATE reservations SET status = ? WHERE id = ?")) {
            st.setString(1, reservation.getStatus().name());
            st.setLong(2, reservation.getId());
            st.executeUpdate();
        }
    }
}