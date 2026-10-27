package mz.uem.eda.parking.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.ConnectionFactory;
import mz.uem.eda.parking.persistence.LaneRepository;
import mz.uem.eda.parking.persistence.ReservationRepository;
import mz.uem.eda.parking.persistence.SessionRepository;
import mz.uem.eda.parking.persistence.TariffRepository;
import mz.uem.eda.parking.persistence.UserRepository;
import mz.uem.eda.parking.persistence.security.PasswordHasher;
import mz.uem.eda.parking.service.policy.PlacementPolicy;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * O balcão do parque: a única classe da camada {@code service} que a
 * interface usa (ADR-032). Cada botão da interface corresponde a um método
 * daqui.
 *
 * <p><b>Porque existe.</b> Os outros serviços fazem cada um uma coisa, e uma
 * operação do operador precisa de vários, pela ordem certa. Uma saída, por
 * exemplo, é: cobrar ({@link BillingService}), tirar o carro com as
 * manobras ({@link ExitService}), gravar ({@link SessionRepository}), juntar
 * o pagamento ao histórico ({@link PaymentHistoryService}) e deixar entrar o
 * primeiro da fila de espera ({@link WaitingQueueService}). Se fosse a
 * interface a fazer esta sequência, as regras do parque ficavam espalhadas
 * pelos botões. Assim ficam aqui, e testam-se sem abrir janela nenhuma.
 *
 * <p><b>Não decide nada sozinha.</b> Onde estacionar, quem sai da fila,
 * quanto se paga e como se tira o carro continuam nos serviços e nas
 * políticas. A {@code ParkingDesk} só os chama pela ordem certa e grava o
 * resultado.
 *
 * <p><b>Gravação.</b> Cada operação muda primeiro as estruturas em memória e
 * depois grava numa transacção. Se a gravação falhar, o método lança
 * {@link SQLException}: a memória e a base ficam diferentes, a interface
 * avisa o operador, e reabrir o programa repõe o último estado gravado (ver
 * {@link SessionRepository}).
 *
 * <p><b>A hora vem de fora.</b> Todos os métodos que dependem da hora
 * recebem-na como parâmetro, tal como os outros serviços. A interface passa
 * {@code LocalDateTime.now()}; os testes passam a hora que quiserem. Na
 * saída, a interface usa a mesma hora para {@link #quote} e para
 * {@link #exit}, para o valor mostrado ser exactamente o valor cobrado.
 *
 * <p>Pensada para um só posto de atendimento: guarda um utilizador
 * autenticado de cada vez.
 */
public class ParkingDesk {

    /** Quanto tempo uma reserva espera pelo carro, em minutos. */
    public static final int RESERVATION_TOLERANCE_MINUTES = 30;

    /** Quantos movimentos o painel "Últimos movimentos" mostra. */
    public static final int RECENT_MOVES = 20;

    private final Queue<Lane> lanes;
    private final Queue<LaneView> laneViews;

    private final EntryService entryService;
    private final ExitService exitService;
    private final BillingService billingService;
    private final ReservationService reservationService;
    private final WaitingQueueService waitingService;
    private final AuthService authService;
    private final PaymentHistoryService paymentHistory;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final TariffRepository tariffRepository;
    private final ReservationRepository reservationRepository;

    private final Queue<Move> recentMoves;
    private User currentUser;

    /**
     * Abre o parque: carrega da base as baias com os carros nas mesmas
     * posições, o número do próximo talão, a tarifa activa, as reservas
     * activas, os utilizadores e o histórico dos pagamentos, e liga os
     * serviços entre si.
     *
     * <p>As filas de espera começam vazias: não são gravadas, porque quem
     * estava à espera quando o programa fechou já não está à porta.
     *
     * @param connections a ligação à base de dados
     * @param placement a política que escolhe a baia de cada carro
     * @param service a política que escolhe de que fila de espera sai o
     *        próximo carro
     * @param now a hora de abertura, para expirar as reservas que já
     *        passaram da tolerância
     * @throws SQLException se a leitura da base falhar
     * @throws NullPointerException se algum argumento for {@code null}
     */
    public ParkingDesk(ConnectionFactory connections, PlacementPolicy placement,
            ServicePolicy service, LocalDateTime now) throws SQLException {
        Objects.requireNonNull(connections, "connections não pode ser null");
        Objects.requireNonNull(placement, "placement não pode ser null");
        Objects.requireNonNull(service, "service não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");

        LaneRepository laneRepository = new LaneRepository(connections);
        this.sessionRepository = new SessionRepository(connections, laneRepository);
        this.userRepository = new UserRepository(connections);
        this.tariffRepository = new TariffRepository(connections);
        this.reservationRepository = new ReservationRepository(connections);

        this.lanes = laneRepository.loadAll();
        this.laneViews = new LinkedQueue<>();
        for (Lane lane : lanes) {
            laneViews.enqueue(new LaneView(lane));
        }

        this.entryService = new EntryService(lanes, placement);
        entryService.restoreNextTicketNumber(sessionRepository.loadNextTicketNumber());
        this.exitService = new ExitService(lanes);
        this.billingService = new BillingService(tariffRepository.loadActive());

        this.reservationService = new ReservationService(lanes, entryService,
                RESERVATION_TOLERANCE_MINUTES);
        for (Reservation reservation : reservationRepository.loadActive(now)) {
            reservationService.load(reservation);
        }
        this.waitingService = new WaitingQueueService(lanes, entryService, service,
                reservationService);

        this.authService = new AuthService(new PasswordHasher());
        for (User user : userRepository.loadAll()) {
            authService.load(user);
        }

        this.paymentHistory = new PaymentHistoryService();
        for (Payment payment : sessionRepository.loadPayments(tariffRepository.loadAll())) {
            paymentHistory.add(payment);
        }

        this.recentMoves = new LinkedQueue<>();
    }

    // ------------------------------------------------------------------
    // Utilizadores e sessão de trabalho
    // ------------------------------------------------------------------

    /**
     * @return {@code true} se ainda não houver nenhum utilizador, caso em que
     *         a interface pede para criar o primeiro administrador em vez de
     *         mostrar o login
     */
    public boolean needsFirstAdmin() {
        return authService.size() == 0;
    }

    /**
     * Cria e grava o primeiro administrador, e deixa-o autenticado.
     *
     * @param username o nome de utilizador
     * @param password a palavra-passe, com pelo menos
     *        {@link AuthService#MIN_PASSWORD_LENGTH} caracteres
     * @return o administrador criado
     * @throws IllegalStateException se já houver utilizadores
     * @throws IllegalArgumentException se a palavra-passe for curta demais
     * @throws SQLException se a gravação falhar
     */
    public User createFirstAdmin(String username, char[] password) throws SQLException {
        User admin = authService.createFirstAdmin(username, password);
        userRepository.insert(admin);
        currentUser = admin;
        return admin;
    }

    /**
     * Autentica um utilizador. Se as credenciais estiverem erradas, fica
     * ninguém autenticado.
     *
     * @param username o nome de utilizador
     * @param password a palavra-passe
     * @return o utilizador autenticado, ou {@code null} se o nome ou a
     *         palavra-passe estiverem errados
     */
    public User login(String username, char[] password) {
        currentUser = authService.login(username, password);
        return currentUser;
    }

    /** Termina a sessão de trabalho: fica ninguém autenticado. */
    public void logout() {
        currentUser = null;
    }

    /** @return o utilizador autenticado, ou {@code null} se não houver */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Muda a palavra-passe de quem está autenticado.
     *
     * @param current a palavra-passe actual
     * @param replacement a nova palavra-passe
     * @return {@code false} se a palavra-passe actual estiver errada
     * @throws IllegalStateException se ninguém estiver autenticado
     * @throws IllegalArgumentException se a nova palavra-passe for curta
     * @throws SQLException se a gravação falhar
     */
    public boolean changeOwnPassword(char[] current, char[] replacement) throws SQLException {
        requireLoggedIn();
        if (!authService.changePassword(currentUser, current, replacement)) {
            return false;
        }
        userRepository.updateCredentials(currentUser);
        return true;
    }

    // ------------------------------------------------------------------
    // Operações do operador
    // ------------------------------------------------------------------

    /**
     * Faz entrar um carro. Há três caminhos:
     * <ol>
     * <li>se a matrícula tiver uma reserva activa, faz check-in e o carro
     * entra com a duração indicada na reserva;</li>
     * <li>se houver um lugar que não esteja reservado, a política escolhe a
     * baia e o carro entra;</li>
     * <li>se não houver, o carro vai para a fila de espera e o método
     * devolve {@code null}.</li>
     * </ol>
     *
     * @param plate a matrícula, com ou sem espaços e hífenes
     * @param type o tipo de viatura
     * @param estimatedMinutes a duração estimada que o condutor indicou, em
     *        minutos; é ignorada se o carro tiver reserva
     * @param now a hora de chegada
     * @return a sessão do carro estacionado, ou {@code null} se ficou na fila
     *         de espera
     * @throws IllegalStateException se ninguém estiver autenticado, ou se o
     *         carro já estiver estacionado ou à espera
     * @throws IllegalArgumentException se a matrícula estiver vazia ou a
     *         duração não for positiva
     * @throws SQLException se a gravação falhar
     * @complexity O(B + n + w), dominado pelas buscas sequenciais nas B
     *             baias, nos n carros estacionados e nos w em espera
     */
    public ParkingSession enter(String plate, Vehicle.Type type, int estimatedMinutes,
            LocalDateTime now) throws SQLException {
        requireLoggedIn();
        Objects.requireNonNull(now, "now não pode ser null");
        Vehicle vehicle = new Vehicle(plate, type, null);
        if (waitingService.isWaiting(vehicle)) {
            throw new IllegalStateException("veículo já está à espera: " + vehicle.getPlate());
        }

        reservationService.expire(now);
        Reservation reservation = reservationService.find(vehicle.getPlate());
        ParkingSession session;
        if (reservation != null) {
            session = reservationService.checkIn(vehicle, now);
        } else if (reservationService.availableForWalkIns(now) > 0) {
            session = entryService.enter(vehicle, estimatedMinutes, now);
        } else {
            session = null;
        }

        if (session == null) {
            waitingService.join(vehicle, estimatedMinutes, now);
            return null;
        }
        recordEntry(session, reservation);
        return session;
    }

    /**
     * Reserva um lugar para uma matrícula. A reserva dura
     * {@link #RESERVATION_TOLERANCE_MINUTES} minutos.
     *
     * @param plate a matrícula
     * @param estimatedMinutes a duração estimada da estadia, em minutos
     * @param now a hora do pedido
     * @return a reserva gravada, ou {@code null} se não houver lugar livre
     *         sem reserva
     * @throws IllegalStateException se ninguém estiver autenticado, se o
     *         carro já estiver estacionado ou se já tiver reserva
     * @throws SQLException se a gravação falhar
     */
    public Reservation reserve(String plate, int estimatedMinutes, LocalDateTime now)
            throws SQLException {
        requireLoggedIn();
        Reservation reservation = reservationService.reserve(plate, estimatedMinutes, now);
        if (reservation != null) {
            reservationRepository.insert(reservation);
        }
        return reservation;
    }

    /**
     * Procura um carro estacionado, para o diálogo de saída. Só lê: não tira
     * o carro nem cobra.
     *
     * @param plateOrTicket a matrícula, ou o número do talão (só algarismos)
     * @return a sessão do carro, ou {@code null} se não estiver no parque
     * @throws IllegalStateException se ninguém estiver autenticado
     * @complexity O(n), busca sequencial pelos carros estacionados
     */
    public ParkingSession findForExit(String plateOrTicket) {
        requireLoggedIn();
        Objects.requireNonNull(plateOrTicket, "plateOrTicket não pode ser null");
        String text = plateOrTicket.strip();
        if (isTicketNumber(text)) {
            return exitService.findByTicket(Integer.parseInt(text));
        }
        return exitService.findByPlate(text);
    }

    /**
     * Diz quanto o carro paga se sair à hora indicada.
     *
     * @param session a sessão do carro
     * @param at a hora de saída; a mesma que se vai passar a {@link #exit}
     * @return o valor em centavos de metical
     */
    public long quote(ParkingSession session, LocalDateTime at) {
        return billingService.amountDue(session, at);
    }

    /**
     * Cobra e tira o carro do parque. Pela ordem:
     * <ol>
     * <li>calcula o pagamento (tem de ser antes, porque a saída fecha a
     * sessão e uma sessão fechada já não se cobra);</li>
     * <li>tira o carro com as manobras e repõe a baia na ordem original;</li>
     * <li>grava o pagamento, o fecho da sessão e a baia numa transacção;</li>
     * <li>junta o pagamento ao histórico e os movimentos aos recentes;</li>
     * <li>deixa entrar o primeiro da fila de espera, se houver, e grava essa
     * entrada.</li>
     * </ol>
     *
     * @param session a sessão devolvida por {@link #findForExit}
     * @param method o método de pagamento escolhido
     * @param at a hora de saída; a mesma usada em {@link #quote}
     * @return o pagamento, o plano de movimentos e o carro que entrou da fila
     * @throws IllegalStateException se ninguém estiver autenticado, se a
     *         sessão já estiver fechada ou se a baia de manobra não tiver
     *         espaço
     * @throws IllegalArgumentException se o carro já não estiver no parque
     * @throws SQLException se a gravação falhar
     * @complexity O(n + k + B), com n carros estacionados (busca), k carros
     *             por cima do que sai (2k manobras) e B baias (fila de espera)
     */
    public ExitResult exit(ParkingSession session, Payment.Method method, LocalDateTime at)
            throws SQLException {
        requireLoggedIn();
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(method, "method não pode ser null");
        if (!session.isActive()) {
            throw new IllegalStateException("a sessão já está fechada: " + session);
        }

        Payment payment = billingService.pay(session, method, at, currentUser);
        RetrievalPlan plan = exitService.exitByTicket(session.getTicketNumber(), at);
        if (plan == null) {
            throw new IllegalArgumentException("o carro não está no parque: " + session);
        }
        sessionRepository.recordExit(session, laneOf(session.getLaneCode()), payment);
        paymentHistory.add(payment);
        for (Move move : plan.moves()) {
            remember(move);
        }

        ParkingSession admitted = waitingService.admitNext(at);
        if (admitted != null) {
            recordEntry(admitted, null);
        }
        return new ExitResult(payment, plan, admitted);
    }

    // ------------------------------------------------------------------
    // Administração
    // ------------------------------------------------------------------

    /**
     * @return todos os utilizadores, para o ecrã de administração
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     */
    public Iterable<User> users() {
        AuthService.requireAdmin(currentUser);
        return authService.users();
    }

    /**
     * Cria e grava um utilizador novo.
     *
     * @param username o nome de utilizador
     * @param password a palavra-passe inicial
     * @param role o perfil, operador ou administrador
     * @return o utilizador criado
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws IllegalStateException se o nome já existir
     * @throws IllegalArgumentException se a palavra-passe for curta
     * @throws SQLException se a gravação falhar
     */
    public User registerUser(String username, char[] password, User.Role role) throws SQLException {
        User user = authService.register(currentUser, username, password, role);
        userRepository.insert(user);
        return user;
    }

    /**
     * Dá uma palavra-passe nova a um utilizador que se esqueceu da sua.
     *
     * @param username o utilizador
     * @param replacement a nova palavra-passe
     * @return {@code false} se o utilizador não existir
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws SQLException se a gravação falhar
     */
    public boolean resetPassword(String username, char[] replacement) throws SQLException {
        if (!authService.resetPassword(currentUser, username, replacement)) {
            return false;
        }
        userRepository.updateCredentials(authService.find(username));
        return true;
    }

    /**
     * Retira um utilizador. Na base fica desactivado, não apagado, porque os
     * pagamentos que cobrou continuam a apontar para ele.
     *
     * @param username o utilizador a retirar
     * @return {@code false} se o utilizador não existir
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws IllegalStateException se for o próprio administrador ou o
     *         último administrador
     * @throws SQLException se a gravação falhar
     */
    public boolean removeUser(String username) throws SQLException {
        AuthService.requireAdmin(currentUser);
        User user = authService.find(username);
        if (user == null || !authService.remove(currentUser, username)) {
            return false;
        }
        userRepository.deactivate(user);
        return true;
    }

    /**
     * Troca a tarifa. A nova passa a valer para as saídas seguintes; os
     * pagamentos antigos continuam ligados à tarifa com que foram calculados
     * (ADR-031).
     *
     * @param tariff a tarifa nova, ainda não gravada
     * @param now a hora da troca
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws SQLException se a gravação falhar
     */
    public void changeTariff(Tariff tariff, LocalDateTime now) throws SQLException {
        AuthService.requireAdmin(currentUser);
        tariffRepository.replaceActive(tariff, now);
        billingService.setTariff(tariff);
    }

    // ------------------------------------------------------------------
    // Leitura, para os ecrãs
    // ------------------------------------------------------------------

    /**
     * @return as baias, pela ordem da configuração, incluindo a de manobra
     * @complexity O(1) para obter; O(B) para percorrer
     */
    public Iterable<LaneView> lanes() {
        return laneViews;
    }

    /** @return a fila de espera preferencial, da frente para o fim */
    public Iterable<WaitingCar> preferentialQueue() {
        return waitingService.preferentialContents();
    }

    /** @return a fila de espera normal, da frente para o fim */
    public Iterable<WaitingCar> normalQueue() {
        return waitingService.normalContents();
    }

    /**
     * @return quantos carros esperam na fila preferencial
     * @complexity O(1)
     */
    public int preferentialWaitingCount() {
        return waitingService.preferentialSize();
    }

    /**
     * @return quantos carros esperam na fila normal
     * @complexity O(1)
     */
    public int normalWaitingCount() {
        return waitingService.normalSize();
    }

    /**
     * Os últimos {@link #RECENT_MOVES} movimentos, do mais antigo para o mais
     * recente: entradas, manobras e saídas.
     *
     * @return os movimentos recentes
     */
    public Iterable<Move> recentMoves() {
        return recentMoves;
    }

    /**
     * As reservas ainda na fila de expiração. Pode incluir reservas que já
     * fizeram check-in e ainda não saíram da fila: a interface mostra só as
     * que {@link Reservation#isActive()}.
     *
     * @return as reservas, da mais antiga para a mais recente
     */
    public Iterable<Reservation> reservations() {
        return reservationService.reservations();
    }

    /** @return a tarifa em vigor */
    public Tariff getTariff() {
        return billingService.getTariff();
    }

    /**
     * Quanto pagaria uma estadia com uma tarifa que ainda não está em vigor.
     * Serve para o ecrã da tarifa mostrar exemplos antes de a gravar; usa as
     * mesmas regras de cálculo da cobrança verdadeira e não muda nada.
     *
     * @param tariff a tarifa a experimentar
     * @param minutes a duração da estadia, em minutos
     * @return o valor, em centavos
     * @complexity O(1)
     */
    public long priceWith(Tariff tariff, long minutes) {
        return new BillingService(tariff).amountFor(minutes);
    }

    /**
     * @return quantos carros estão estacionados, sem contar a baia de
     *         manobra
     * @complexity O(B)
     */
    public int occupiedPlaces() {
        int occupied = 0;
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                occupied += lane.size();
            }
        }
        return occupied;
    }

    /**
     * @return quantos lugares o parque tem, sem contar a baia de manobra
     * @complexity O(B)
     */
    public int totalPlaces() {
        int total = 0;
        for (Lane lane : lanes) {
            if (!lane.isBuffer()) {
                total += lane.getCapacity();
            }
        }
        return total;
    }

    /**
     * @param now a hora actual, para expirar reservas
     * @return quantos lugares estão livres e sem reserva
     */
    public int freeForWalkIns(LocalDateTime now) {
        return reservationService.availableForWalkIns(now);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Grava uma entrada e junta-a aos movimentos recentes. */
    private void recordEntry(ParkingSession session, Reservation checkedIn) throws SQLException {
        sessionRepository.recordEntry(session, laneOf(session.getLaneCode()),
                entryService.getNextTicketNumber(), checkedIn);
        remember(new Move(1, session.getVehicle().getPlate(), null, session.getLaneCode()));
    }

    /**
     * Guarda um movimento nos recentes. Quando a fila já tem
     * {@link #RECENT_MOVES}, sai o mais antigo antes de entrar o novo.
     */
    private void remember(Move move) {
        if (recentMoves.size() == RECENT_MOVES) {
            recentMoves.dequeue();
        }
        recentMoves.enqueue(move);
    }

    /** Busca sequencial da baia pelo código, entre as B baias. */
    private Lane laneOf(String code) {
        for (Lane lane : lanes) {
            if (lane.getCode().equals(code)) {
                return lane;
            }
        }
        throw new IllegalStateException("baia desconhecida: " + code);
    }

    private void requireLoggedIn() {
        if (currentUser == null) {
            throw new IllegalStateException("nenhum utilizador autenticado");
        }
    }

    /** Um talão é escrito só com algarismos; uma matrícula tem letras. */
    private static boolean isTicketNumber(String text) {
        if (text.isEmpty() || text.length() > 9) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}