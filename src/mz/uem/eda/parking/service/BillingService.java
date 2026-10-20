package mz.uem.eda.parking.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;

/**
 * Calcula o valor de uma estadia e regista o pagamento (RF13, RF14, RF15).
 * Todos os valores são em centavos de metical, num {@code long} (ver
 * {@link Tariff}).
 *
 * <p>As regras, por esta ordem:
 * <ol>
 *   <li><b>Minutos da estadia</b>: da entrada até à hora pedida, com o
 *       minuto começado a contar inteiro (15 min e 1 s são 16 min).</li>
 *   <li><b>Tolerância gratuita (RN01)</b>: até {@code freeMinutes}
 *       inclusive, não se paga nada. Passada a tolerância, paga-se a estadia
 *       toda, desde a entrada.</li>
 *   <li><b>Dias completos</b>: cada bloco de 24 h desde a entrada paga o
 *       tecto diário (RN03).</li>
 *   <li><b>O resto</b>: arredonda-se para cima até à fracção mínima (RN02)
 *       e cobra-se ao valor por hora, sem passar do tecto diário.</li>
 * </ol>
 *
 * <p>Exemplo com tolerância de 15 min, fracção de 15 min, 60 MZN por hora e
 * tecto de 400 MZN: 10 min pagam 0; 61 min contam como 75 e pagam 75 MZN;
 * 10 h pagam 400 MZN; 25 h pagam 400 + 60 = 460 MZN.
 *
 * <p>O pagamento faz-se <b>antes</b> da saída (RF08): a interface mostra o
 * valor com {@link #amountDue}, regista o pagamento com {@link #pay} e só
 * depois chama o {@link ExitService}.
 */
public class BillingService {

    public static final int MINUTES_PER_HOUR = 60;
    public static final int MINUTES_PER_DAY = 24 * 60;

    private Tariff tariff;

    /**
     * Cria o serviço com a tarifa em vigor.
     *
     * @param tariff a tarifa em vigor
     * @throws NullPointerException se {@code tariff} for {@code null}
     */
    public BillingService(Tariff tariff) {
        this.tariff = Objects.requireNonNull(tariff, "tariff não pode ser null");
    }

    /**
     * @return a tarifa em vigor, nunca {@code null}
     */
    public Tariff getTariff() {
        return tariff;
    }

    /**
     * Troca a tarifa em vigor (RF13). Como {@link Tariff} é imutável, mudar
     * preços é criar uma tarifa nova e passá-la aqui. Os pagamentos já
     * feitos não mudam.
     *
     * @param tariff a nova tarifa
     * @throws NullPointerException se {@code tariff} for {@code null}
     */
    public void setTariff(Tariff tariff) {
        this.tariff = Objects.requireNonNull(tariff, "tariff não pode ser null");
    }

    /**
     * Quanto paga esta sessão se sair à hora indicada. Não muda nada: serve
     * para a interface mostrar o valor antes de cobrar.
     *
     * @param session a sessão do carro
     * @param at a hora a que se calcula
     * @return o valor em centavos, 0 dentro da tolerância
     * @throws IllegalArgumentException se {@code at} for anterior à entrada
     * @complexity O(1)
     */
    public long amountDue(ParkingSession session, LocalDateTime at) {
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(at, "at não pode ser null");
        return amountFor(minutesBetween(session.getEntryTime(), at));
    }

    /**
     * Calcula o valor de uma estadia com esta duração, pelas regras da
     * classe.
     *
     * @param minutes a duração da estadia, em minutos
     * @return o valor em centavos
     * @throws IllegalArgumentException se {@code minutes} for negativo
     * @complexity O(1)
     */
    public long amountFor(long minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("minutes não pode ser negativo: " + minutes);
        }
        if (minutes <= tariff.getFreeMinutes()) {
            return 0;
        }
        long fullDays = minutes / MINUTES_PER_DAY;
        long rest = minutes % MINUTES_PER_DAY;
        return fullDays * tariff.getDailyCapCents() + partialDay(rest);
    }

    /**
     * Regista o pagamento da sessão, pelo valor devido à hora do pagamento.
     * Paga-se com o carro ainda no parque: só depois se chama o
     * {@link ExitService}.
     *
     * @param session a sessão a pagar, ainda activa
     * @param method o método de pagamento (só registado, ADR-027)
     * @param paidAt a hora do pagamento
     * @param operator o operador que cobra
     * @return o pagamento, com o valor calculado
     * @throws IllegalStateException se a sessão já estiver fechada
     * @throws IllegalArgumentException se {@code paidAt} for anterior à
     *         entrada
     * @complexity O(1)
     */
    public Payment pay(ParkingSession session, Payment.Method method,
            LocalDateTime paidAt, User operator) {
        Objects.requireNonNull(session, "session não pode ser null");
        if (!session.isActive()) {
            throw new IllegalStateException("a sessão já está fechada: " + session);
        }
        long amount = amountDue(session, paidAt);
        return new Payment(session, amount, method, paidAt, operator);
    }

    /**
     * Minutos entre duas horas, com o minuto começado a contar inteiro.
     *
     * @param from a hora de entrada
     * @param to a hora final
     * @return os minutos, arredondados para cima
     * @throws IllegalArgumentException se {@code to} for anterior a
     *         {@code from}
     * @complexity O(1)
     */
    public static long minutesBetween(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from não pode ser null");
        Objects.requireNonNull(to, "to não pode ser null");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("a hora final é anterior à entrada: " + to);
        }
        long seconds = Duration.between(from, to).getSeconds();
        return divideRoundingUp(seconds, 60);
    }

    /**
     * Valor de um resto de dia (menos de 24 h): arredonda para cima até à
     * fracção mínima, cobra ao valor por hora e não passa do tecto.
     *
     * <p>O valor por minuto pode não dar centavos inteiros (por exemplo 50
     * MZN por hora em fracções de 20 min). Multiplica-se primeiro e divide-se
     * no fim, arredondando para cima ao centavo, para o erro não se repetir
     * em cada fracção.
     *
     * @complexity O(1)
     */
    private long partialDay(long minutes) {
        if (minutes == 0) {
            return 0;
        }
        int fraction = tariff.getFractionMinutes();
        long charged = divideRoundingUp(minutes, fraction) * fraction;
        long amount = divideRoundingUp(charged * tariff.getHourlyRateCents(), MINUTES_PER_HOUR);
        if (amount > tariff.getDailyCapCents()) {
            return tariff.getDailyCapCents();
        }
        return amount;
    }

    /**
     * Divisão inteira arredondada para cima, para valores não negativos.
     * O Java 17 não tem {@code Math.ceilDiv}, que só chegou no Java 18.
     */
    private static long divideRoundingUp(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}