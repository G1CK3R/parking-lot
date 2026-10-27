package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Grava as entradas e as saídas: sessões, talões, viaturas e pagamentos
 * (tabelas {@code parking_sessions}, {@code vehicles}, {@code payments} e
 * {@code park_state}).
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
 *
 * <p>Também repõe o histórico dos pagamentos ({@link #loadPayments}), cada
 * um ligado à tarifa com que foi calculado (ADR-031).
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
     * Grava uma entrada, numa só transacção: a viatura (nova, ou com o tipo
     * actualizado se a matrícula já existir), a sessão, o conteúdo da baia onde o carro ficou, o
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
        long vehicleId;
        long sessionId;
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                vehicleId = findOrInsertVehicle(c, vehicle);
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
     *         o pagamento for de outra sessão, ou se o operador ou a tarifa
     *         ainda não estiverem gravados
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
        if (payment.getTariff().getId() == 0) {
            throw new IllegalArgumentException("tarifa ainda não gravada: o pagamento tem de apontar para uma tarifa da base");
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
     * Grava a anulação de uma entrada (desfazer), numa só transacção: a
     * sessão fica {@code VOIDED} e a baia é regravada sem o carro. O talão
     * não volta a ser usado.
     *
     * @param session a sessão já anulada ({@code ParkingSession.voidEntry})
     * @param lane a baia de onde o carro saiu, já sem ele
     * @throws IllegalArgumentException se a sessão não estiver anulada
     * @throws SQLException se a gravação falhar (nada fica gravado)
     */
    public void recordVoidedEntry(ParkingSession session, Lane lane) throws SQLException {
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(lane, "lane não pode ser null");
        if (session.getStatus() != ParkingSession.Status.VOIDED) {
            throw new IllegalArgumentException("a sessão não foi anulada: " + session);
        }
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement st = c.prepareStatement(
                        "UPDATE parking_sessions SET status = ? WHERE ticket_no = ?")) {
                    st.setString(1, session.getStatus().name());
                    st.setInt(2, session.getTicketNumber());
                    if (st.executeUpdate() != 1) {
                        throw new SQLException("sessão por gravar: " + session);
                    }
                }
                lanes.saveSlots(c, lane);
                c.commit();
            } catch (SQLException e) {
                Sql.rollbackQuietly(c, e);
                throw e;
            }
        }
    }

    /**
     * Carrega o histórico dos pagamentos, pela ordem em que foram gravados,
     * cada um com a sua sessão já fechada, o operador que cobrou e a tarifa
     * com que foi calculado (ADR-031). Quem coordena passa-os, um a um, ao
     * {@code PaymentHistoryService}.
     *
     * <p>A tarifa não se monta de novo a partir da linha: procura-se pelo id
     * entre as tarifas de {@link TariffRepository#loadAll()}, para todos os
     * pagamentos feitos com a mesma tarifa apontarem para o mesmo objecto. O
     * operador monta-se da linha, mesmo que entretanto tenha sido
     * desactivado: o histórico não muda quando alguém deixa de trabalhar no
     * parque.
     *
     * <p>O {@code ORDER BY p.id} só repõe a ordem de gravação; os JOIN só
     * juntam as linhas de cada pagamento. Nenhuma pergunta é respondida em
     * SQL (ADR-012): procurar um pagamento é trabalho do serviço.
     *
     * @param tariffs todas as tarifas, de {@link TariffRepository#loadAll()}
     * @return os pagamentos, o mais antigo à frente, com o id da base
     * @throws IllegalStateException se um pagamento apontar para uma tarifa
     *         que não está em {@code tariffs}
     * @throws SQLException se a leitura falhar
     * @complexity O(p · t), em que p é o número de pagamentos e t o de
     *             tarifas: uma busca sequencial pelas tarifas por pagamento
     */
    public Queue<Payment> loadPayments(Iterable<Tariff> tariffs) throws SQLException {
        Objects.requireNonNull(tariffs, "tariffs não pode ser null");
        String sql = "SELECT p.id AS payment_id, p.tariff_id, p.amount_cents, p.lost_ticket_cents, p.method, p.paid_at, "
                + "s.id AS session_id, s.ticket_no, s.stay_class, s.estimated_minutes, s.entry_at, "
                + "s.exit_at, s.moves_count, l.code AS lane_code, "
                + "v.id AS vehicle_id, v.plate, v.vehicle_type, "
                + "u.id AS user_id, u.username, u.password_hash, u.salt, u.iterations, u.role "
                + "FROM payments p "
                + "JOIN parking_sessions s ON s.id = p.session_id "
                + "JOIN lanes l ON l.id = s.lane_id "
                + "JOIN vehicles v ON v.id = s.vehicle_id "
                + "JOIN users u ON u.id = p.user_id "
                + "ORDER BY p.id";
        Queue<Payment> payments = new LinkedQueue<>();
        try (Connection c = connections.open();
                PreparedStatement st = c.prepareStatement(sql);
                ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                ParkingSession session = new ParkingSession(rs.getInt("ticket_no"), LaneRepository.vehicleFrom(rs),
                        rs.getString("lane_code"), StayClass.valueOf(rs.getString("stay_class")),
                        rs.getInt("estimated_minutes"), Sql.getTime(rs, "entry_at"));
                session.close(Sql.getTime(rs, "exit_at"), rs.getInt("moves_count"));
                session.setId(rs.getLong("session_id"));

                User operator = new User(rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("salt"), rs.getInt("iterations"), User.Role.valueOf(rs.getString("role")));
                operator.setId(rs.getLong("user_id"));

                Payment payment = new Payment(session, findTariff(tariffs, rs.getLong("tariff_id")),
                        rs.getLong("amount_cents"), Payment.Method.valueOf(rs.getString("method")),
                        Sql.getTime(rs, "paid_at"), operator, rs.getLong("lost_ticket_cents"));
                payment.setId(rs.getLong("payment_id"));
                payments.enqueue(payment);
            }
        }
        return payments;
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

    /**
     * O id da viatura: a que já está na base com esta matrícula, ou uma
     * nova. Se a matrícula já existir, o tipo fica o que o operador escolheu
     * agora (a viatura tem um só tipo, o último registado).
     */
    private static long findOrInsertVehicle(Connection c, Vehicle vehicle) throws SQLException {
        long id = 0;
        try (PreparedStatement st = c.prepareStatement("SELECT id FROM vehicles WHERE plate = ?")) {
            st.setString(1, vehicle.getPlate());
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    id = rs.getLong(1);
                }
            }
        }
        if (id != 0) {
            try (PreparedStatement st = c.prepareStatement("UPDATE vehicles SET vehicle_type = ? WHERE id = ?")) {
                st.setString(1, vehicle.getType().name());
                st.setLong(2, id);
                st.executeUpdate();
            }
            return id;
        }
        String sql = "INSERT INTO vehicles (plate, vehicle_type) VALUES (?, ?)";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, vehicle.getPlate());
            st.setString(2, vehicle.getType().name());
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
        String sql = "INSERT INTO payments (session_id, tariff_id, amount_cents, lost_ticket_cents, method, paid_at, "
                + "user_id) SELECT id, ?, ?, ?, ?, ?, ? FROM parking_sessions WHERE ticket_no = ?";
        try (PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setLong(1, payment.getTariff().getId());
            st.setLong(2, payment.getAmountCents());
            st.setLong(3, payment.getLostTicketCents());
            st.setString(4, payment.getMethod().name());
            Sql.setTime(st, 5, payment.getPaidAt());
            st.setLong(6, payment.getOperator().getId());
            st.setInt(7, payment.getSession().getTicketNumber());
            if (st.executeUpdate() != 1) {
                throw new SQLException("sessão por gravar: " + payment.getSession());
            }
            return Sql.generatedKey(st);
        }
    }

    /** Busca sequencial da tarifa pelo id da base, entre as t tarifas. */
    private static Tariff findTariff(Iterable<Tariff> tariffs, long id) {
        for (Tariff tariff : tariffs) {
            if (tariff.getId() == id) {
                return tariff;
            }
        }
        throw new IllegalStateException("payments aponta para uma tarifa que não foi carregada: " + id);
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