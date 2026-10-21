package mz.uem.eda.parking.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import mz.uem.eda.parking.model.Customer;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Guarda e repõe o estado das baias, com a posição de cada carro, para o
 * parque reabrir exactamente como ficou (tabelas {@code lanes} e
 * {@code lane_slots}).
 *
 * <p><b>A posição é o que permite reconstruir cada pilha.</b> Ao gravar,
 * cada carro recebe a sua posição a contar do fundo (1 é o fundo, o topo tem
 * a maior). Ao repor, empilha-se por posição crescente: o primeiro
 * {@code push} é o do fundo e o último é o do topo, e a pilha fica igual. É o
 * único {@code ORDER BY} do projecto com efeito no domínio, e não ordena nada
 * do negócio: só repõe a ordem que a pilha já tinha.
 *
 * <p>As baias em si vêm do {@code 02-seed.sql}; este repositório não as cria
 * nem as apaga, só lhes grava e repõe o conteúdo.
 */
public class LaneRepository {

    private final ConnectionFactory connections;

    /**
     * @param connections a fábrica de ligações
     * @throws NullPointerException se {@code connections} for {@code null}
     */
    public LaneRepository(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections, "connections não pode ser null");
    }

    /**
     * Carrega as baias, pela ordem da configuração, com os carros nas mesmas
     * posições em que ficaram. As sessões repostas estão activas e têm o id
     * da base; as viaturas e os clientes também.
     *
     * @return as baias, incluindo a de manobra (vazia)
     * @throws SQLException se a leitura falhar
     * @throws IllegalStateException se uma posição estiver fora da baia
     * @complexity O(B + n): uma passagem pelas B baias e outra pelos n
     *             carros estacionados, mais a procura da baia de cada carro
     *             entre as B
     */
    public Queue<Lane> loadAll() throws SQLException {
        Queue<Lane> lanes = new LinkedQueue<>();
        try (Connection c = connections.open()) {
            loadLanes(c, lanes);
            loadSlots(c, lanes);
        }
        return lanes;
    }

    /**
     * Regrava o conteúdo de uma baia: apaga as posições antigas e escreve as
     * actuais. Usa a ligação de quem chama, para fazer parte da mesma
     * transacção que a entrada ou a saída (ver {@link SessionRepository}).
     *
     * <p>As sessões são identificadas pelo número do talão, que é único, por
     * isso não precisam de ter o id da base preenchido.
     *
     * @param c a ligação, normalmente dentro de uma transacção
     * @param lane a baia, no estado em que ficou
     * @throws SQLException se a gravação falhar
     * @complexity O(c), em que c é o número de carros na baia
     */
    public void saveSlots(Connection c, Lane lane) throws SQLException {
        Objects.requireNonNull(c, "c não pode ser null");
        Objects.requireNonNull(lane, "lane não pode ser null");
        try (PreparedStatement delete = c.prepareStatement(
                "DELETE FROM lane_slots WHERE lane_id = (SELECT id FROM lanes WHERE code = ?)")) {
            delete.setString(1, lane.getCode());
            delete.executeUpdate();
        }
        String insert = "INSERT INTO lane_slots (lane_id, position, session_id) "
                + "SELECT l.id, ?, s.id FROM lanes l, parking_sessions s "
                + "WHERE l.code = ? AND s.ticket_no = ?";
        try (PreparedStatement st = c.prepareStatement(insert)) {
            // contents() vai do topo para o fundo: o topo tem a posição size
            int position = lane.size();
            for (ParkingSession session : lane.contents()) {
                st.setInt(1, position);
                st.setString(2, lane.getCode());
                st.setInt(3, session.getTicketNumber());
                if (st.executeUpdate() != 1) {
                    throw new SQLException("sessão por gravar ou baia desconhecida: " + session
                            + " em " + lane.getCode());
                }
                position--;
            }
        }
    }

    private static void loadLanes(Connection c, Queue<Lane> lanes) throws SQLException {
        String sql = "SELECT id, code, capacity, stay_class, is_buffer FROM lanes ORDER BY id";
        try (PreparedStatement st = c.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Lane lane;
                if (rs.getBoolean("is_buffer")) {
                    lane = Lane.buffer(rs.getString("code"), rs.getInt("capacity"));
                } else {
                    lane = Lane.parking(rs.getString("code"), rs.getInt("capacity"),
                            StayClass.valueOf(rs.getString("stay_class")));
                }
                lane.setId(rs.getLong("id"));
                lanes.enqueue(lane);
            }
        }
    }

    private static void loadSlots(Connection c, Queue<Lane> lanes) throws SQLException {
        String sql = "SELECT ls.lane_id, ls.position, l.code AS lane_code, "
                + "s.id AS session_id, s.ticket_no, s.stay_class, s.estimated_minutes, s.entry_at, "
                + "v.id AS vehicle_id, v.plate, v.vehicle_type, "
                + "cu.id AS customer_id, cu.name, cu.phone, cu.stay_class AS customer_class, cu.preferential "
                + "FROM lane_slots ls "
                + "JOIN lanes l ON l.id = ls.lane_id "
                + "JOIN parking_sessions s ON s.id = ls.session_id "
                + "JOIN vehicles v ON v.id = s.vehicle_id "
                + "LEFT JOIN customers cu ON cu.id = v.customer_id "
                + "ORDER BY ls.lane_id, ls.position";
        try (PreparedStatement st = c.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                Lane lane = findById(lanes, rs.getLong("lane_id"));
                if (rs.getInt("position") != lane.size() + 1) {
                    throw new IllegalStateException("posições com buracos na baia " + lane.getCode()
                            + ": esperava " + (lane.size() + 1) + ", veio " + rs.getInt("position"));
                }
                ParkingSession session = new ParkingSession(rs.getInt("ticket_no"), vehicleFrom(rs),
                        rs.getString("lane_code"), StayClass.valueOf(rs.getString("stay_class")),
                        rs.getInt("estimated_minutes"), Sql.getTime(rs, "entry_at"));
                session.setId(rs.getLong("session_id"));
                lane.asStack().push(session);
            }
        }
    }

    /** Monta a viatura, e o cliente se tiver, a partir de uma linha com as colunas do JOIN. */
    static Vehicle vehicleFrom(ResultSet rs) throws SQLException {
        Customer customer = null;
        long customerId = rs.getLong("customer_id");
        if (!rs.wasNull()) {
            String usual = rs.getString("customer_class");
            customer = new Customer(rs.getString("name"), rs.getString("phone"),
                    usual == null ? null : StayClass.valueOf(usual), rs.getBoolean("preferential"));
            customer.setId(customerId);
        }
        Vehicle vehicle = new Vehicle(rs.getString("plate"), Vehicle.Type.valueOf(rs.getString("vehicle_type")),
                customer);
        vehicle.setId(rs.getLong("vehicle_id"));
        return vehicle;
    }

    /** Procura sequencial da baia pelo id da base, entre as B baias. */
    private static Lane findById(Queue<Lane> lanes, long id) {
        for (Lane lane : lanes) {
            if (lane.getId() == id) {
                return lane;
            }
        }
        throw new IllegalStateException("lane_slots aponta para uma baia que não existe: " + id);
    }
}