package mz.uem.eda.parking.service.billing;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.parking.ExitService;

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
 *   <li><b>Noite e fim-de-semana (RF17)</b>: no resto, cada fracção paga o
 *       valor da hora a que começa, o de noite entre as 20h e as 7h, e leva
 *       o desconto do fim-de-semana se começar a um sábado ou domingo. O
 *       arredondamento ao centavo faz-se uma só vez, no fim.</li>
 *   <li><b>Talão perdido (RF17)</b>: se o carro sai sem talão, soma-se a
 *       multa da tarifa.</li>
 *   <li><b>Avença (RF16)</b>: se a matrícula tem uma avença válida, a estadia
 *       não se paga (só a multa, se houver).</li>
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
     * feitos não mudam: cada um guarda a tarifa com que foi calculado
     * (ADR-031).
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
        return amountBetween(session.getEntryTime(), at);
    }

    /**
     * O valor de uma estadia entre duas horas, com todas as regras da
     * classe, incluindo a noite e o fim-de-semana.
     *
     * @param entry a hora de entrada
     * @param exit a hora de saída
     * @return o valor em centavos, 0 dentro da tolerância
     * @throws IllegalArgumentException se {@code exit} for anterior a {@code entry}
     * @complexity O(1440 / f): no pior caso uma volta por fracção do último dia
     */
    public long amountBetween(LocalDateTime entry, LocalDateTime exit) {
        long minutes = minutesBetween(entry, exit);
        if (minutes <= tariff.getFreeMinutes()) {
            return 0;
        }
        long fullDays = minutes / MINUTES_PER_DAY;
        long rest = minutes % MINUTES_PER_DAY;
        return fullDays * tariff.getDailyCapCents() + partialDay(entry.plusDays(fullDays), rest);
    }

    /**
     * Calcula o valor de uma estadia com esta duração, sem saber a que horas
     * foi: tudo ao valor por hora de dia, sem desconto de fim-de-semana. Serve
     * para comparar tarifas pela duração.
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
        return fullDays * tariff.getDailyCapCents() + partialDayAtDayRate(rest);
    }

    /**
     * Regista o pagamento da sessão, pelo valor devido à hora do pagamento.
     * Paga-se com o carro ainda no parque: só depois se chama o
     * {@link ExitService}. O pagamento guarda a tarifa em vigor, com que o
     * valor foi calculado (ADR-031).
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
        return new Payment(session, tariff, amount, method, paidAt, operator);
    }

    /**
     * Regista o pagamento da sessão com as regras do talão perdido e da
     * avença (RF16, RF17).
     *
     * <ul>
     * <li>Com avença válida, a estadia vale 0.</li>
     * <li>Sem talão, soma-se a multa da tarifa.</li>
     * <li>Se o total for 0 por causa da avença, o método fica
     * {@link Payment.Method#PASS}; senão, é o escolhido.</li>
     * </ul>
     *
     * @param session a sessão a pagar, ainda activa
     * @param method o método escolhido para o que houver a pagar
     * @param paidAt a hora do pagamento
     * @param operator o operador que cobra
     * @param lostTicket se o carro sai sem talão
     * @param coveredByPass se a matrícula tem uma avença válida
     * @return o pagamento
     * @throws IllegalStateException se a sessão já estiver fechada
     * @complexity O(1440 / f)
     */
    public Payment pay(ParkingSession session, Payment.Method method, LocalDateTime paidAt, User operator,
            boolean lostTicket, boolean coveredByPass) {
        Objects.requireNonNull(session, "session não pode ser null");
        if (!session.isActive()) {
            throw new IllegalStateException("a sessão já está fechada: " + session);
        }
        long fee = lostTicket ? tariff.getLostTicketCents() : 0;
        long amount = quote(session, paidAt, lostTicket, coveredByPass);
        Payment.Method charged = coveredByPass && amount == 0 ? Payment.Method.PASS : method;
        return new Payment(session, tariff, amount, charged, paidAt, operator, fee);
    }

    /**
     * Quanto se paga à saída, com as regras do talão perdido e da avença.
     *
     * @param session a sessão
     * @param at a hora de saída
     * @param lostTicket se o carro sai sem talão
     * @param coveredByPass se a matrícula tem uma avença válida
     * @return o valor em centavos
     */
    public long quote(ParkingSession session, LocalDateTime at, boolean lostTicket, boolean coveredByPass) {
        long stay = coveredByPass ? 0 : amountDue(session, at);
        return stay + (lostTicket ? tariff.getLostTicketCents() : 0);
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
    private long partialDayAtDayRate(long minutes) {
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
     * Valor do resto de um dia que começa a {@code start}: arredonda os
     * minutos para cima até à fracção e soma as fracções, cada uma ao valor
     * da hora a que começa (dia ou noite) e com o desconto do fim-de-semana
     * se começar a um sábado ou domingo. A soma faz-se em «centavos ×
     * minutos × percentagem» e só no fim se divide, arredondando para cima
     * ao centavo: sem noite nem desconto, o resultado é exactamente o de
     * {@link #partialDayAtDayRate}. Nunca passa do tecto diário.
     *
     * @complexity O(minutes / f)
     */
    private long partialDay(LocalDateTime start, long minutes) {
        if (minutes == 0) {
            return 0;
        }
        int fraction = tariff.getFractionMinutes();
        long fractions = divideRoundingUp(minutes, fraction);
        long sum = 0;
        for (long i = 0; i < fractions; i++) {
            LocalDateTime begins = start.plusMinutes(i * fraction);
            long rate = isNight(begins) ? tariff.getNightRateCents() : tariff.getHourlyRateCents();
            int percent = isWeekend(begins) ? 100 - tariff.getWeekendDiscountPercent() : 100;
            sum += rate * fraction * percent;
        }
        long amount = divideRoundingUp(sum, MINUTES_PER_HOUR * 100L);
        return Math.min(amount, tariff.getDailyCapCents());
    }

    /** @return {@code true} se a hora for de noite, entre as 20h e as 7h */
    static boolean isNight(LocalDateTime time) {
        int hour = time.getHour();
        return hour >= Tariff.NIGHT_START_HOUR || hour < Tariff.NIGHT_END_HOUR;
    }

    /** @return {@code true} se o dia for sábado ou domingo */
    static boolean isWeekend(LocalDateTime time) {
        DayOfWeek day = time.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Divisão inteira arredondada para cima, para valores não negativos.
     * O Java 17 não tem {@code Math.ceilDiv}, que só chegou no Java 18.
     */
    private static long divideRoundingUp(long dividend, long divisor) {
        return (dividend + divisor - 1) / divisor;
    }
}