package mz.uem.eda.parking.service;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

import mz.uem.eda.parking.model.AuditEntry;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Move;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Reservation;
import mz.uem.eda.parking.model.RetrievalPlan;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.AuditRepository;
import mz.uem.eda.parking.persistence.BackupRepository;
import mz.uem.eda.parking.persistence.ConnectionFactory;
import mz.uem.eda.parking.persistence.LaneRepository;
import mz.uem.eda.parking.persistence.MonthlyPassRepository;
import mz.uem.eda.parking.persistence.ReservationRepository;
import mz.uem.eda.parking.persistence.SessionRepository;
import mz.uem.eda.parking.persistence.TariffRepository;
import mz.uem.eda.parking.persistence.UserRepository;
import mz.uem.eda.parking.persistence.security.PasswordHasher;
import mz.uem.eda.parking.service.policy.PlacementPolicy;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

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
 * <p><b>Desfazer.</b> As entradas e as reservas ficam numa pilha de
 * operações ({@link Operation}); {@link #undo} tira a do topo e faz o
 * contrário. A pilha esvazia-se a cada saída, quando o operador muda e
 * quando um carro entra da fila de espera.
 *
 * <p><b>Auditoria (RF23).</b> As operações sensíveis (logins, utilizadores,
 * tarifa, multas, avenças, cancelamentos, desfazer, cópias de segurança)
 * ficam registadas depois de gravadas. A hora do registo é a da operação
 * quando o método a recebe; nos outros (gestão de utilizadores, por
 * exemplo), é a do relógio.
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
    private final MonthlyPassService passService;
    private final AuditService auditService;

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final TariffRepository tariffRepository;
    private final ReservationRepository reservationRepository;
    private final MonthlyPassRepository passRepository;
    private final AuditRepository auditRepository;
    private final BackupRepository backupRepository;

    private final Queue<Move> recentMoves;
    /** As operações que se podem desfazer, a última no topo. */
    private final Stack<Operation> operations = new LinkedStack<>();
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
        this.passRepository = new MonthlyPassRepository(connections);
        this.auditRepository = new AuditRepository(connections);
        this.backupRepository = new BackupRepository(connections);

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

        this.passService = new MonthlyPassService();
        for (MonthlyPass pass : passRepository.loadAll()) {
            passService.load(pass);
        }
        this.auditService = new AuditService();
        for (AuditEntry entry : auditRepository.loadAll()) {
            auditService.add(entry);
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
        clearOperations();
        record(LocalDateTime.now(), AuditEntry.Action.FIRST_ADMIN, admin.getUsername());
        return admin;
    }

    /**
     * Autentica um utilizador à hora do relógio.
     *
     * @see #login(String, char[], LocalDateTime)
     */
    public User login(String username, char[] password) throws SQLException {
        return login(username, password, LocalDateTime.now());
    }

    /**
     * Autentica um utilizador. Se as credenciais estiverem erradas, fica
     * ninguém autenticado. Cinco palavras-passe erradas seguidas bloqueiam a
     * conta durante 15 minutos (RF03); o estado do bloqueio fica gravado.
     * Cada tentativa fica no registo de auditoria.
     *
     * @param username o nome de utilizador
     * @param password a palavra-passe
     * @param now a hora da tentativa
     * @return o utilizador autenticado, ou {@code null} se o nome ou a
     *         palavra-passe estiverem errados, ou se a conta estiver
     *         bloqueada ({@link #lockedUntil} diz qual)
     * @throws SQLException se a gravação do bloqueio ou do registo falhar
     */
    public User login(String username, char[] password, LocalDateTime now) throws SQLException {
        Objects.requireNonNull(username, "username não pode ser null");
        User known = authService.find(username);
        int attemptsBefore = known == null ? 0 : known.getFailedAttempts();
        LocalDateTime lockBefore = known == null ? null : known.getLockedUntil();

        currentUser = authService.login(username, password, now);
        clearOperations();

        if (known != null && (attemptsBefore != known.getFailedAttempts()
                || !Objects.equals(lockBefore, known.getLockedUntil()))) {
            userRepository.updateLockState(known);
        }
        if (currentUser != null) {
            record(now, AuditEntry.Action.LOGIN);
        } else {
            String typed = username.length() > 50 ? username.substring(0, 50) : username;
            recordAs(now, typed, AuditEntry.Action.LOGIN_FAILED, typed);
            if (known != null && known.isLockedAt(now) && !Objects.equals(lockBefore, known.getLockedUntil())) {
                recordAs(now, typed, AuditEntry.Action.ACCOUNT_LOCKED, typed, known.getLockedUntil());
            }
        }
        return currentUser;
    }

    /**
     * Diz se uma conta está bloqueada, para o ecrã de login explicar porque
     * não entrou.
     *
     * @param username o nome de utilizador
     * @param now a hora
     * @return até quando está bloqueada, ou {@code null} se não estiver (ou
     *         se o nome não existir)
     */
    public LocalDateTime lockedUntil(String username, LocalDateTime now) {
        User user = username == null ? null : authService.find(username);
        return user != null && user.isLockedAt(now) ? user.getLockedUntil() : null;
    }

    /**
     * Termina a sessão de trabalho: fica ninguém autenticado, e a pilha de
     * operações esvazia-se.
     *
     * @throws SQLException se a gravação do registo de auditoria falhar (a
     *         sessão termina na mesma)
     */
    public void logout() throws SQLException {
        User leaving = currentUser;
        currentUser = null;
        clearOperations();
        if (leaving != null) {
            recordAs(LocalDateTime.now(), leaving.getUsername(), AuditEntry.Action.LOGOUT);
        }
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
        record(LocalDateTime.now(), AuditEntry.Action.PASSWORD_CHANGED);
        return true;
    }

    // ------------------------------------------------------------------
    // Operações do operador
    // ------------------------------------------------------------------

    /**
     * Faz entrar um carro que não é cliente preferencial.
     *
     * @see #enter(String, Vehicle.Type, int, boolean, LocalDateTime)
     */
    public ParkingSession enter(String plate, Vehicle.Type type, int estimatedMinutes,
            LocalDateTime now) throws SQLException {
        return enter(plate, type, estimatedMinutes, false, now);
    }

    /**
     * Faz entrar um carro. Há três caminhos:
     * <ol>
     * <li>se a matrícula tiver uma reserva activa, faz check-in e o carro
     * entra com a duração indicada na reserva;</li>
     * <li>se houver um lugar que não esteja reservado, a política escolhe a
     * baia e o carro entra;</li>
     * <li>se não houver, o carro vai para a fila de espera e o método
     * devolve {@code null}. Vai para a fila preferencial se o operador
     * marcou o cliente como preferencial (idoso, grávida, pessoa com
     * deficiência).</li>
     * </ol>
     * A operação fica na pilha de operações, para se poder desfazer.
     *
     * @param plate a matrícula, com ou sem espaços e hífenes
     * @param type o tipo de viatura
     * @param estimatedMinutes a duração estimada que o condutor indicou, em
     *        minutos; é ignorada se o carro tiver reserva
     * @param preferentialCustomer se o operador marcou o cliente como
     *        preferencial; só conta se o carro ficar à espera
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
            boolean preferentialCustomer, LocalDateTime now) throws SQLException {
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
            waitingService.join(vehicle, estimatedMinutes, now, preferentialCustomer);
            operations.push(Operation.queued(vehicle));
            return null;
        }
        recordEntry(session, reservation);
        operations.push(Operation.entry(session));
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
            operations.push(Operation.reservation(reservation));
        }
        return reservation;
    }

    /**
     * Cancela a reserva activa de uma matrícula e grava o cancelamento. O
     * lugar volta a ficar livre para quem chega sem reserva: se houver carros
     * à espera, o primeiro entra logo (ver {@link #admitWaiting}).
     *
     * @param plate a matrícula
     * @param now a hora do cancelamento
     * @return {@code false} se a matrícula não tiver reserva activa (por
     *         exemplo, se já expirou)
     * @throws IllegalStateException se ninguém estiver autenticado
     * @throws SQLException se a gravação falhar
     */
    public boolean cancelReservation(String plate, LocalDateTime now) throws SQLException {
        requireLoggedIn();
        Reservation reservation = reservationService.cancel(plate, now);
        if (reservation == null) {
            return false;
        }
        reservationRepository.updateStatus(reservation);
        record(now, AuditEntry.Action.RESERVATION_CANCELLED, reservation.getPlate());
        operations.push(Operation.cancellation(reservation));
        admitWaiting(now);
        return true;
    }

    /**
     * Desfaz a última operação que ainda está na pilha de operações:
     * <ul>
     * <li><b>entrada</b>: o carro sai do topo da baia sem pagar, o talão
     * fica anulado e o lugar volta a ficar livre;</li>
     * <li><b>ida para a fila</b>: o carro sai da fila de espera;</li>
     * <li><b>reserva</b>: a reserva fica cancelada;</li>
     * <li><b>cancelamento</b>: a reserva volta a estar activa, até à mesma
     * hora.</li>
     * </ul>
     * Se a operação já não se puder desfazer (por exemplo, o cancelamento de
     * uma reserva que entretanto expirou), sai da pilha na mesma e o método
     * lança {@link IllegalStateException}.
     *
     * @param now a hora
     * @return a operação desfeita
     * @throws IllegalStateException se ninguém estiver autenticado, se não
     *         houver nada para desfazer, ou se a operação já não se puder
     *         desfazer
     * @throws SQLException se a gravação falhar
     * @complexity O(1) para tirar da pilha; mais O(w) na fila de espera e
     *             O(r + B) numa reserva
     */
    public Operation undo(LocalDateTime now) throws SQLException {
        requireLoggedIn();
        Objects.requireNonNull(now, "now não pode ser null");
        if (operations.isEmpty()) {
            throw new IllegalStateException("não há nada para desfazer");
        }
        Operation operation = operations.pop();
        switch (operation.getType()) {
            case ENTRY:
                undoEntry(operation.getSession(), now);
                break;
            case QUEUED:
                if (!waitingService.remove(operation.getVehicle())) {
                    throw new IllegalStateException("o carro já não está à espera: " + operation.getPlate());
                }
                record(now, AuditEntry.Action.QUEUE_UNDONE, operation.getPlate());
                break;
            case RESERVATION:
                Reservation made = operation.getReservation();
                if (reservationService.cancel(made.getPlate(), now) != made) {
                    throw new IllegalStateException("a reserva já não está activa: " + made.getPlate());
                }
                reservationRepository.updateStatus(made);
                record(now, AuditEntry.Action.RESERVATION_UNDONE, made.getPlate());
                admitWaiting(now);
                break;
            default: // CANCELLATION
                Reservation cancelled = operation.getReservation();
                if (!reservationService.reactivate(cancelled, now)) {
                    throw new IllegalStateException("a reserva já não pode voltar: " + cancelled.getPlate());
                }
                reservationRepository.updateStatus(cancelled);
                record(now, AuditEntry.Action.CANCELLATION_UNDONE, cancelled.getPlate());
                break;
        }
        return operation;
    }

    /**
     * @return {@code true} se houver uma operação para desfazer
     * @complexity O(1)
     */
    public boolean canUndo() {
        return currentUser != null && !operations.isEmpty();
    }

    /**
     * @return a operação que {@link #undo} desfaz a seguir, para a interface
     *         escrever no menu o que vai desfazer; {@code null} se não houver
     * @complexity O(1)
     */
    public Operation lastOperation() {
        return operations.isEmpty() ? null : operations.peek();
    }

    /**
     * Faz entrar os carros em espera enquanto houver lugar livre sem reserva.
     * A saída já o faz para o lugar que liberta; isto serve para os lugares
     * que ficam livres de outra maneira: uma reserva cancelada ou expirada,
     * ou uma entrada desfeita. A interface chama-o também de 30 em 30
     * segundos, por causa das reservas que expiram sozinhas.
     *
     * <p>Se algum carro entrar, a pilha de operações esvazia-se: a ordem das
     * baias deixou de ser a das operações empilhadas.
     *
     * @param now a hora de entrada
     * @return as sessões dos carros que entraram, pela ordem; vazia se
     *         nenhum entrou
     * @throws SQLException se a gravação falhar
     * @complexity O(a·(B + n)), com a carros admitidos
     */
    public Iterable<ParkingSession> admitWaiting(LocalDateTime now) throws SQLException {
        Objects.requireNonNull(now, "now não pode ser null");
        Queue<ParkingSession> admitted = new LinkedQueue<>();
        if (waitingService.isEmpty()) {
            return admitted;
        }
        reservationService.expire(now);
        ParkingSession session = waitingService.admitNext(now);
        while (session != null) {
            recordEntry(session, null);
            admitted.enqueue(session);
            session = waitingService.admitNext(now);
        }
        if (!admitted.isEmpty()) {
            clearOperations();
        }
        return admitted;
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
     * Diz quanto o carro paga se sair à hora indicada, com talão.
     *
     * @see #quote(ParkingSession, LocalDateTime, boolean)
     */
    public long quote(ParkingSession session, LocalDateTime at) {
        return quote(session, at, false);
    }

    /**
     * Diz quanto o carro paga se sair à hora indicada: a estadia (0 se a
     * matrícula tiver avença válida) mais a multa, se perdeu o talão.
     *
     * @param session a sessão do carro
     * @param at a hora de saída; a mesma que se vai passar a {@link #exit}
     * @param lostTicket se o carro sai sem talão
     * @return o valor em centavos de metical
     * @complexity O(a + 1440 / f), com a avenças vendidas
     */
    public long quote(ParkingSession session, LocalDateTime at, boolean lostTicket) {
        Objects.requireNonNull(session, "session não pode ser null");
        boolean covered = passService.validFor(session.getVehicle().getPlate(), at) != null;
        return billingService.quote(session, at, lostTicket, covered);
    }

    /**
     * A avença que cobre esta matrícula a esta hora, para o diálogo de
     * saída avisar o operador.
     *
     * @param plate a matrícula
     * @param at a hora
     * @return a avença, ou {@code null} se não houver
     * @complexity O(a)
     */
    public MonthlyPass validPass(String plate, LocalDateTime at) {
        return passService.validFor(plate, at);
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
        return exit(session, method, at, false);
    }

    /**
     * Cobra e tira o carro do parque, como {@link #exit(ParkingSession,
     * Payment.Method, LocalDateTime)}, com as regras da avença e do talão
     * perdido: com avença válida a estadia vale 0 (e o método fica
     * {@link Payment.Method#PASS} se não houver nada a pagar); sem talão,
     * soma-se a multa da tarifa, e a saída fica no registo de auditoria.
     * A pilha de operações esvazia-se: uma saída não se desfaz.
     *
     * @param session a sessão devolvida por {@link #findForExit}
     * @param method o método de pagamento escolhido
     * @param at a hora de saída; a mesma usada em {@link #quote}
     * @param lostTicket se o carro sai sem talão
     * @return o pagamento, o plano de movimentos e o carro que entrou da fila
     * @throws IllegalStateException se ninguém estiver autenticado ou se a
     *         sessão já estiver fechada
     * @throws IllegalArgumentException se o carro já não estiver no parque
     * @throws SQLException se a gravação falhar
     */
    public ExitResult exit(ParkingSession session, Payment.Method method, LocalDateTime at,
            boolean lostTicket) throws SQLException {
        requireLoggedIn();
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(method, "method não pode ser null");
        if (!session.isActive()) {
            throw new IllegalStateException("a sessão já está fechada: " + session);
        }

        if (method == Payment.Method.PASS) {
            throw new IllegalArgumentException("o método PASS só é atribuído pela avença");
        }
        boolean covered = passService.validFor(session.getVehicle().getPlate(), at) != null;
        Payment payment = billingService.pay(session, method, at, currentUser, lostTicket, covered);
        clearOperations();
        RetrievalPlan plan = exitService.exitByTicket(session.getTicketNumber(), at);
        if (plan == null) {
            throw new IllegalArgumentException("o carro não está no parque: " + session);
        }
        sessionRepository.recordExit(session, laneOf(session.getLaneCode()), payment);
        paymentHistory.add(payment);
        for (Move move : plan.moves()) {
            remember(move);
        }
        if (lostTicket) {
            record(at, AuditEntry.Action.LOST_TICKET, session.getVehicle().getPlate(),
                    session.getTicketNumber(), payment.getLostTicketCents());
        }

        ParkingSession admitted = waitingService.admitNext(at);
        if (admitted != null) {
            recordEntry(admitted, null);
        }
        return new ExitResult(payment, plan, admitted);
    }

    // ------------------------------------------------------------------
    // Avenças e pesquisa
    // ------------------------------------------------------------------

    /**
     * Vende uma avença mensal (RF16): a matrícula passa a sair sem pagar a
     * estadia durante o mês. O valor é o da tarifa em vigor e conta na
     * receita e no fecho de caixa de quem vende.
     *
     * @param customerName o nome do cliente
     * @param phone o telefone, ou {@code null}
     * @param plate a matrícula
     * @param month o mês que a avença cobre; o actual ou um seguinte
     * @param method o método de pagamento
     * @param now a hora da venda
     * @return a avença vendida e gravada
     * @throws IllegalStateException se ninguém estiver autenticado, ou se a
     *         matrícula já tiver avença para esse mês
     * @throws IllegalArgumentException se o mês já tiver passado, ou se o
     *         nome, a matrícula ou o método não servirem
     * @throws SQLException se a gravação falhar
     */
    public MonthlyPass sellPass(String customerName, String phone, String plate, YearMonth month,
            Payment.Method method, LocalDateTime now) throws SQLException {
        requireLoggedIn();
        MonthlyPass pass = passService.sell(customerName, phone, plate, month, method, now, currentUser,
                getTariff());
        passRepository.insert(pass);
        record(now, AuditEntry.Action.PASS_SOLD, pass.getPlate(), pass.getMonth(), pass.getAmountCents());
        return pass;
    }

    /**
     * @return todas as avenças vendidas, pela ordem de venda
     * @throws IllegalStateException se ninguém estiver autenticado
     */
    public Iterable<MonthlyPass> passes() {
        requireLoggedIn();
        return passService.passes();
    }

    /**
     * Pesquisa de viaturas estacionadas (RF09): percorre as baias pela ordem
     * da configuração e, em cada uma, os carros do topo para o fundo, e
     * guarda os que servem. Diz também a profundidade de cada carro, que é o
     * que custa tirá-lo.
     *
     * @param text um pedaço da matrícula (com ou sem espaços e hífenes), ou
     *        o número exacto do talão; vazio para todos
     * @return os carros encontrados, pela ordem das baias
     * @throws IllegalStateException se ninguém estiver autenticado
     * @complexity O(n), busca sequencial por todos os carros estacionados
     */
    public Iterable<ParkedCar> parkedCars(String text) {
        requireLoggedIn();
        String typed = text == null ? "" : text.strip();
        String wanted = Vehicle.normalizePlate(typed);
        int ticket = isTicketNumber(typed) ? Integer.parseInt(typed) : -1;
        Queue<ParkedCar> found = new LinkedQueue<>();
        for (Lane lane : lanes) {
            if (lane.isBuffer()) {
                continue;
            }
            int depth = 0;
            for (ParkingSession session : lane.contents()) {
                boolean byPlate = session.getVehicle().getPlate().contains(wanted);
                if (byPlate || session.getTicketNumber() == ticket) {
                    found.enqueue(new ParkedCar(session, depth));
                }
                depth++;
            }
        }
        return found;
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
        record(LocalDateTime.now(), AuditEntry.Action.USER_CREATED, user.getUsername(), user.getRole());
        return user;
    }

    /**
     * Dá uma palavra-passe nova a um utilizador que se esqueceu da sua. Se a
     * conta estiver bloqueada, fica desbloqueada.
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
        User user = authService.find(username);
        userRepository.updateCredentials(user);
        userRepository.updateLockState(user);
        record(LocalDateTime.now(), AuditEntry.Action.PASSWORD_RESET, user.getUsername());
        return true;
    }

    /**
     * Desbloqueia uma conta antes de acabarem os 15 minutos (RF03).
     *
     * @param username o utilizador
     * @return {@code false} se o utilizador não existir
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws SQLException se a gravação falhar
     */
    public boolean unlockUser(String username) throws SQLException {
        if (!authService.unlock(currentUser, username)) {
            return false;
        }
        User user = authService.find(username);
        userRepository.updateLockState(user);
        record(LocalDateTime.now(), AuditEntry.Action.USER_UNLOCKED, user.getUsername());
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
        record(LocalDateTime.now(), AuditEntry.Action.USER_REMOVED, user.getUsername());
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
        record(now, AuditEntry.Action.TARIFF_CHANGED, tariff.getHourlyRateCents(), tariff.getDailyCapCents(),
                tariff.getNightRateCents(), tariff.getWeekendDiscountPercent(), tariff.getLostTicketCents(),
                tariff.getMonthlyPassCents());
    }

    /**
     * Grava uma cópia de segurança de todas as tabelas num ficheiro (RF24).
     *
     * @param file o ficheiro a criar ou substituir
     * @param now a hora, para o registo de auditoria
     * @return quantas linhas foram copiadas
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws IOException se a escrita do ficheiro falhar
     * @throws SQLException se a leitura da base falhar
     */
    public int backup(Path file, LocalDateTime now) throws IOException, SQLException {
        AuthService.requireAdmin(currentUser);
        int rows = backupRepository.export(file);
        record(now, AuditEntry.Action.BACKUP_CREATED, file.getFileName(), rows);
        return rows;
    }

    /**
     * Substitui o conteúdo da base pelo de uma cópia de segurança (RF24),
     * numa só transacção: ou fica tudo da cópia, ou não muda nada.
     *
     * <p>Depois disto as estruturas em memória já não correspondem à base:
     * fica ninguém autenticado, e a interface fecha o programa para ser
     * aberto de novo.
     *
     * @param file o ficheiro da cópia
     * @param now a hora, para o registo de auditoria
     * @return quantas linhas foram repostas
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws IOException se o ficheiro não se puder ler ou não for uma cópia
     *         deste programa
     * @throws SQLException se a escrita na base falhar (a base fica como
     *         estava); se só a linha de auditoria falhar, a cópia fica
     *         reposta e o método termina normalmente
     */
    public int restore(Path file, LocalDateTime now) throws IOException, SQLException {
        AuthService.requireAdmin(currentUser);
        int rows = backupRepository.restore(file);
        String admin = currentUser.getUsername();
        currentUser = null;
        clearOperations();
        try {
            recordAs(now, admin, AuditEntry.Action.BACKUP_RESTORED, file.getFileName(), rows);
        } catch (SQLException e) {
            // A cópia já está reposta. Sem esta linha o registo fica incompleto,
            // mas dizer ao administrador que o restauro falhou seria pior.
        }
        return rows;
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

    /**
     * Expira as reservas vencidas e devolve as que ficam na fila, para o
     * ecrã de reservas. Como em {@link #reservations()}, a interface mostra
     * só as que {@link Reservation#isActive()}.
     *
     * @param now a hora actual
     * @return as reservas, da que expira primeiro para a que expira depois
     */
    public Iterable<Reservation> reservations(LocalDateTime now) {
        reservationService.expire(now);
        return reservationService.reservations();
    }

    /**
     * @return quantas reservas estão activas
     * @complexity O(1)
     */
    public int activeReservationCount() {
        return reservationService.activeCount();
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
     * Quanto pagaria uma estadia que começa a uma hora certa, com uma tarifa
     * que ainda não está em vigor: conta a noite e o fim-de-semana.
     *
     * @param tariff a tarifa a experimentar
     * @param entry a hora de entrada
     * @param minutes a duração da estadia, em minutos
     * @return o valor, em centavos
     */
    public long priceWith(Tariff tariff, LocalDateTime entry, long minutes) {
        return new BillingService(tariff).amountBetween(entry, entry.plusMinutes(minutes));
    }

    // ------------------------------------------------------------------
    // Relatórios
    // ------------------------------------------------------------------

    /**
     * A receita de um período, de todos os operadores (RF18): os pagamentos
     * em três ordens, as avenças vendidas, e os totais por dia, por operador
     * e por método. Só para o administrador.
     *
     * @param from o início do período, incluído
     * @param to o fim do período, excluído
     * @return o relatório
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(d·n) para n pagamentos no período; ver {@link ReportService}
     */
    public RevenueReport revenue(LocalDateTime from, LocalDateTime to) {
        AuthService.requireAdmin(currentUser);
        return ReportService.revenue(paymentHistory.payments(), passService.passes(), from, to, null);
    }

    /**
     * O fecho de caixa de quem está autenticado (RF21): só os pagamentos e
     * as avenças que ele próprio cobrou entre {@code from} e {@code to}.
     * Qualquer utilizador pode pedir o seu.
     *
     * @param from o início do turno; a interface passa a hora do login
     * @param to o fim do turno; a interface passa a hora actual
     * @return o fecho de caixa
     * @throws IllegalStateException se ninguém estiver autenticado
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     */
    public CashClose cashClose(LocalDateTime from, LocalDateTime to) {
        requireLoggedIn();
        return ReportService.cashClose(paymentHistory.payments(), passService.passes(), currentUser, from, to);
    }

    /**
     * O histórico das estadias com filtros (RF20), para o ecrã do histórico
     * e para exportar em CSV. Só para o administrador.
     *
     * @param from o início do período de entrada, incluído
     * @param to o fim do período de entrada, excluído
     * @param plateText um pedaço da matrícula, ou vazio para todas
     * @param operator só as saídas cobradas por este operador, ou
     *        {@code null} para todos
     * @param includeInPark se entram os carros ainda no parque
     * @param includeLeft se entram as estadias que já saíram
     * @return as linhas, pela hora de entrada
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @see ReportService#history
     */
    public Iterable<HistoryEntry> history(LocalDateTime from, LocalDateTime to, String plateText,
            String operator, boolean includeInPark, boolean includeLeft) {
        AuthService.requireAdmin(currentUser);
        return ReportService.history(paymentHistory.payments(), lanes, from, to, plateText, operator,
                includeInPark, includeLeft);
    }

    /**
     * As linhas do registo de auditoria (RF23), com filtros. Só para o
     * administrador.
     *
     * @param from o início, incluído
     * @param to o fim, excluído
     * @param username só deste utilizador, ou {@code null} para todos
     * @param action só desta operação, ou {@code null} para todas
     * @return as linhas, da mais antiga para a mais recente
     * @throws SecurityException se quem está autenticado não for
     *         administrador
     * @complexity O(N), uma passagem pelo registo
     */
    public Iterable<AuditEntry> auditLog(LocalDateTime from, LocalDateTime to, String username,
            AuditEntry.Action action) {
        AuthService.requireAdmin(currentUser);
        return auditService.filter(from, to, username, action);
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

    /**
     * Desfaz uma entrada: o carro tem de estar no topo da sua baia, o que é
     * sempre verdade quando as operações se desfazem pela ordem da pilha.
     */
    private void undoEntry(ParkingSession session, LocalDateTime now) throws SQLException {
        Lane lane = laneOf(session.getLaneCode());
        if (lane.isEmpty() || lane.asStack().peek() != session || !session.isActive()) {
            throw new IllegalStateException("o carro já não está no topo da baia: " + session);
        }
        lane.asStack().pop();
        session.voidEntry();
        sessionRepository.recordVoidedEntry(session, lane);
        remember(new Move(1, session.getVehicle().getPlate(), lane.getCode(), null));
        record(now, AuditEntry.Action.ENTRY_UNDONE, session.getVehicle().getPlate(), session.getTicketNumber());
        admitWaiting(now);
    }

    /** Esvazia a pilha de operações. O(m), m = operações empilhadas. */
    private void clearOperations() {
        while (!operations.isEmpty()) {
            operations.pop();
        }
    }

    /** Regista uma operação de quem está autenticado. */
    private void record(LocalDateTime at, AuditEntry.Action action, Object... values) throws SQLException {
        recordAs(at, currentUser == null ? null : currentUser.getUsername(), action, values);
    }

    /** Grava uma linha de auditoria e junta-a ao registo em memória. */
    private void recordAs(LocalDateTime at, String username, AuditEntry.Action action, Object... values)
            throws SQLException {
        AuditEntry entry = new AuditEntry(at, username, action, AuditEntry.join(values));
        auditRepository.insert(entry);
        auditService.add(entry);
    }

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