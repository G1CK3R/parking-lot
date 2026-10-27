package mz.uem.eda.parking.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

import mz.uem.eda.parking.algorithms.QueueRadixSort;
import mz.uem.eda.parking.algorithms.SortKey;
import mz.uem.eda.parking.algorithms.TwoStackSort;
import mz.uem.eda.parking.model.Charge;
import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

/**
 * Calcula os relatórios a partir do histórico: a receita de um período
 * (RF18), o fecho de caixa de um turno (RF21) e o histórico de estadias com
 * filtros (RF20). Não guarda nada nem grava nada; só lê e devolve resultados
 * novos.
 *
 * <p><b>Receita = pagamentos de estadias + avenças vendidas.</b> As duas
 * coisas são {@link Charge}: têm valor, método, hora e operador, e somam-se
 * da mesma maneira nos totais. A lista de pagamentos do relatório tem só as
 * estadias; as avenças têm a sua própria lista.
 *
 * <p><b>Só filas e pilhas.</b> Os relatórios são onde os algoritmos da
 * camada {@code algorithms} trabalham a sério:
 * <ul>
 * <li><b>Radix sort com 10 filas</b> ({@link QueueRadixSort}), O(d·n) e sem
 * comparações, para ordenar pela hora, pelo talão, pelo valor, pelo operador
 * e pelo método. É estável, por isso dois valores com a mesma chave ficam
 * pela ordem da hora.</li>
 * <li><b>Inversão com uma pilha</b>, O(n), para pôr a ordem por valor do
 * maior para o menor: o radix sort ordena por ordem crescente, e tirar tudo
 * de uma pilha devolve a ordem ao contrário.</li>
 * <li><b>Totais por grupos seguidos</b>, O(n): depois de ordenar pela
 * chave, os valores do mesmo dia (ou operador, ou método) ficam todos
 * seguidos na fila, e basta uma passagem para os somar. Assim não é
 * preciso uma tabela de dispersão.</li>
 * <li><b>Ordenação com duas pilhas</b> ({@link TwoStackSort}), O(n²), para
 * os valores de um turno, que são poucos. O número de comparações fica no
 * {@link CashClose}, para comparar com o radix sort.</li>
 * <li><b>Busca sequencial</b>, O(n), para os filtros do histórico.</li>
 * </ul>
 */
public final class ReportService {

    private ReportService() {
    }

    /**
     * A receita de um período, só com pagamentos de estadias (sem avenças).
     *
     * @see #revenue(Iterable, Iterable, LocalDateTime, LocalDateTime, String)
     */
    public static RevenueReport revenue(Iterable<Payment> history, LocalDateTime from, LocalDateTime to,
            String operator) {
        return revenue(history, new LinkedQueue<MonthlyPass>(), from, to, operator);
    }

    /**
     * A receita de um período: os pagamentos de estadias e as avenças
     * vendidas com hora de pagamento entre {@code from} (incluído) e
     * {@code to} (excluído).
     *
     * @param history o histórico de pagamentos, em qualquer ordem
     * @param passes as avenças vendidas, em qualquer ordem
     * @param from o início do período, incluído
     * @param to o fim do período, excluído
     * @param operator o nome do operador a que o relatório se limita, ou
     *        {@code null} para todos
     * @return o relatório, com os pagamentos, as avenças e os totais
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(d·n) para n valores no período (mais O(N) para os
     *             escolher dos N do histórico); d são os algarismos da maior
     *             chave
     */
    public static RevenueReport revenue(Iterable<Payment> history, Iterable<MonthlyPass> passes,
            LocalDateTime from, LocalDateTime to, String operator) {
        Objects.requireNonNull(history, "history não pode ser null");
        Objects.requireNonNull(passes, "passes não pode ser null");
        checkPeriod(from, to);

        // 1. Escolher os pagamentos e as avenças do período (e do operador), somando a receita.
        Queue<Payment> byTime = new LinkedQueue<>();
        Queue<MonthlyPass> passesByTime = new LinkedQueue<>();
        Queue<Charge> charges = new LinkedQueue<>();
        long total = 0;
        for (Payment payment : history) {
            if (selected(payment, from, to, operator)) {
                byTime.enqueue(payment);
                charges.enqueue(payment);
                total += payment.getAmountCents();
            }
        }
        for (MonthlyPass pass : passes) {
            if (selected(pass, from, to, operator)) {
                passesByTime.enqueue(pass);
                charges.enqueue(pass);
                total += pass.getAmountCents();
            }
        }

        // 2. Ordenar pela hora: chave = minutos desde o início do período.
        QueueRadixSort.sort(byTime, ReportService.<Payment>minutesSince(from));
        QueueRadixSort.sort(passesByTime, ReportService.<MonthlyPass>minutesSince(from));
        QueueRadixSort.sort(charges, ReportService.<Charge>minutesSince(from));

        // 3. As outras ordens partem de cópias da ordem pela hora, para os empates ficarem pela hora.
        Queue<Payment> byTicket = copy(byTime);
        QueueRadixSort.sort(byTicket, TICKET);
        Queue<Payment> byAmount = copy(byTime);
        QueueRadixSort.sort(byAmount, ReportService.<Payment>amount());
        byAmount = reversed(byAmount);

        // 4. Totais: com a fila ordenada pela chave, cada grupo fica seguido.
        Queue<RevenueLine<LocalDate>> byDay = runs(charges, DAY);
        Queue<Charge> byOperatorId = copy(charges);
        QueueRadixSort.sort(byOperatorId, OPERATOR_ID);
        Queue<RevenueLine<String>> byOperator = runs(byOperatorId, OPERATOR_NAME);
        Queue<Charge> byMethodOrder = copy(charges);
        QueueRadixSort.sort(byMethodOrder, METHOD);
        Queue<RevenueLine<Payment.Method>> byMethod = runs(byMethodOrder, METHOD_LABEL);

        return new RevenueReport(from, to, operator, total, byTime, byAmount, byTicket, passesByTime, byDay,
                byOperator, byMethod);
    }

    /**
     * O fecho de caixa de um turno, só com pagamentos de estadias.
     *
     * @see #cashClose(Iterable, Iterable, User, LocalDateTime, LocalDateTime)
     */
    public static CashClose cashClose(Iterable<Payment> history, User operator, LocalDateTime from,
            LocalDateTime to) {
        return cashClose(history, new LinkedQueue<MonthlyPass>(), operator, from, to);
    }

    /**
     * O fecho de caixa de um turno: o que {@code operator} cobrou entre
     * {@code from} e {@code to}, pagamentos de estadias e avenças, do maior
     * para o menor, ordenados com duas pilhas.
     *
     * @param history o histórico de pagamentos
     * @param passes as avenças vendidas
     * @param operator o operador do turno
     * @param from o início do turno, normalmente a hora a que entrou
     * @param to o fim do turno, normalmente agora
     * @return o fecho de caixa
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(d·n) para os totais e O(n²) para a ordem por valor, com
     *             n valores no turno
     */
    public static CashClose cashClose(Iterable<Payment> history, Iterable<MonthlyPass> passes, User operator,
            LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(operator, "operator não pode ser null");
        RevenueReport report = revenue(history, passes, from, to, operator.getUsername());

        Stack<Charge> input = new LinkedStack<>();
        for (Payment payment : report.paymentsByTime()) {
            input.push(payment);
        }
        for (MonthlyPass pass : report.passes()) {
            input.push(pass);
        }
        TwoStackSort<Charge> sorter = new TwoStackSort<>();
        Stack<Charge> sorted = sorter.sort(input, BY_AMOUNT);
        // A pilha ordenada tem o maior valor no topo: tirar tudo dá do maior para o menor.
        Queue<Charge> byAmount = new LinkedQueue<>();
        while (!sorted.isEmpty()) {
            byAmount.enqueue(sorted.pop());
        }
        return new CashClose(report, byAmount, sorter.getComparisons());
    }

    /**
     * O histórico de estadias (RF20): as sessões que entraram no período,
     * as que ainda estão no parque e as que já saíram, pela hora de entrada;
     * as que entraram no mesmo minuto ficam pela ordem do talão.
     *
     * @param payments o histórico de pagamentos (cada um com a sua sessão já fechada)
     * @param lanes as baias, para as sessões ainda no parque
     * @param from o início do período de entrada, incluído
     * @param to o fim do período de entrada, excluído
     * @param plateText um pedaço da matrícula, ou vazio para todas
     * @param operator o operador que cobrou a saída, ou {@code null} para
     *        todos (com operador, as estadias ainda no parque ficam de fora)
     * @param includeInPark se entram as estadias ainda no parque
     * @param includeLeft se entram as estadias que já saíram
     * @return as linhas, da entrada mais antiga para a mais recente
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(N + n + d·h): N pagamentos e n carros percorridos uma vez
     *             (busca sequencial), e as h linhas escolhidas ordenadas com
     *             duas passagens do radix sort (talão, depois minuto de entrada)
     */
    public static Queue<HistoryEntry> history(Iterable<Payment> payments, Iterable<Lane> lanes,
            final LocalDateTime from, LocalDateTime to, String plateText, String operator, boolean includeInPark,
            boolean includeLeft) {
        Objects.requireNonNull(payments, "payments não pode ser null");
        Objects.requireNonNull(lanes, "lanes não pode ser null");
        checkPeriod(from, to);
        String wanted = plateText == null ? "" : Vehicle.normalizePlate(plateText);

        Queue<HistoryEntry> entries = new LinkedQueue<>();
        if (includeInPark && operator == null) {
            for (Lane lane : lanes) {
                if (lane.isBuffer()) {
                    continue;
                }
                for (ParkingSession session : lane.contents()) {
                    if (matches(session, from, to, wanted)) {
                        entries.enqueue(new HistoryEntry(session, null));
                    }
                }
            }
        }
        if (includeLeft) {
            for (Payment payment : payments) {
                boolean ofOperator = operator == null || operator.equals(payment.getOperator().getUsername());
                if (ofOperator && matches(payment.getSession(), from, to, wanted)) {
                    entries.enqueue(new HistoryEntry(payment.getSession(), payment));
                }
            }
        }
        // Duas passagens do radix sort, que é estável: primeiro pelo talão,
        // depois pelo minuto de entrada. Os carros que entraram no mesmo
        // minuto ficam pela ordem do talão.
        QueueRadixSort.sort(entries, new SortKey<HistoryEntry>() {
            @Override
            public int of(HistoryEntry entry) {
                return entry.getSession().getTicketNumber();
            }
        });
        QueueRadixSort.sort(entries, new SortKey<HistoryEntry>() {
            @Override
            public int of(HistoryEntry entry) {
                return Math.toIntExact(Duration.between(from, entry.getSession().getEntryTime()).toMinutes());
            }
        });
        return entries;
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    private static void checkPeriod(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from não pode ser null");
        Objects.requireNonNull(to, "to não pode ser null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("o período tem de começar antes de acabar: " + from + " a " + to);
        }
    }

    /** Diz se o valor cobrado entra no relatório: no período e do operador pedido. */
    private static boolean selected(Charge charge, LocalDateTime from, LocalDateTime to, String operator) {
        LocalDateTime paidAt = charge.getPaidAt();
        boolean inPeriod = !paidAt.isBefore(from) && paidAt.isBefore(to);
        return inPeriod && (operator == null || operator.equals(charge.getOperator().getUsername()));
    }

    /** Diz se a sessão entrou no período e tem o pedaço de matrícula pedido. */
    private static boolean matches(ParkingSession session, LocalDateTime from, LocalDateTime to, String plate) {
        LocalDateTime entry = session.getEntryTime();
        return !entry.isBefore(from) && entry.isBefore(to)
                && session.getVehicle().getPlate().contains(plate);
    }

    /** Copia uma fila sem a mudar: percorre-a e enfileira cada elemento numa nova. */
    private static <T> Queue<T> copy(Queue<T> source) {
        Queue<T> copy = new LinkedQueue<>();
        for (T element : source) {
            copy.enqueue(element);
        }
        return copy;
    }

    /** Inverte a ordem de uma fila com uma pilha; a fila de entrada fica vazia. */
    private static <T> Queue<T> reversed(Queue<T> source) {
        Stack<T> stack = new LinkedStack<>();
        while (!source.isEmpty()) {
            stack.push(source.dequeue());
        }
        Queue<T> result = new LinkedQueue<>();
        while (!stack.isEmpty()) {
            result.enqueue(stack.pop());
        }
        return result;
    }

    /**
     * Soma os grupos seguidos de uma fila já ordenada pela chave: cada vez
     * que a chave muda, fecha-se uma linha.
     */
    private static <K> Queue<RevenueLine<K>> runs(Queue<Charge> sorted, Label<K> label) {
        Queue<RevenueLine<K>> lines = new LinkedQueue<>();
        K current = null;
        int count = 0;
        long total = 0;
        for (Charge charge : sorted) {
            K key = label.of(charge);
            if (count > 0 && !key.equals(current)) {
                lines.enqueue(new RevenueLine<>(current, count, total));
                count = 0;
                total = 0;
            }
            current = key;
            count++;
            total += charge.getAmountCents();
        }
        if (count > 0) {
            lines.enqueue(new RevenueLine<>(current, count, total));
        }
        return lines;
    }

    /** O que identifica o grupo de um valor cobrado, para os totais. */
    private interface Label<K> {
        K of(Charge charge);
    }

    /** Chave da hora: os minutos desde o início do período. */
    private static <T extends Charge> SortKey<T> minutesSince(final LocalDateTime start) {
        return new SortKey<T>() {
            @Override
            public int of(T charge) {
                return Math.toIntExact(Duration.between(start, charge.getPaidAt()).toMinutes());
            }
        };
    }

    /** Chave do valor, em centavos. */
    private static <T extends Charge> SortKey<T> amount() {
        return new SortKey<T>() {
            @Override
            public int of(T charge) {
                return Math.toIntExact(charge.getAmountCents());
            }
        };
    }

    private static final SortKey<Payment> TICKET = new SortKey<Payment>() {
        @Override
        public int of(Payment payment) {
            return payment.getSession().getTicketNumber();
        }
    };

    private static final SortKey<Charge> OPERATOR_ID = new SortKey<Charge>() {
        @Override
        public int of(Charge charge) {
            return Math.toIntExact(charge.getOperator().getId());
        }
    };

    private static final SortKey<Charge> METHOD = new SortKey<Charge>() {
        @Override
        public int of(Charge charge) {
            return charge.getMethod().ordinal();
        }
    };

    private static final Label<LocalDate> DAY = new Label<LocalDate>() {
        @Override
        public LocalDate of(Charge charge) {
            return charge.getPaidAt().toLocalDate();
        }
    };

    private static final Label<String> OPERATOR_NAME = new Label<String>() {
        @Override
        public String of(Charge charge) {
            return charge.getOperator().getUsername();
        }
    };

    private static final Label<Payment.Method> METHOD_LABEL = new Label<Payment.Method>() {
        @Override
        public Payment.Method of(Charge charge) {
            return charge.getMethod();
        }
    };

    private static final Comparator<Charge> BY_AMOUNT = new Comparator<Charge>() {
        @Override
        public int compare(Charge a, Charge b) {
            return Long.compare(a.getAmountCents(), b.getAmountCents());
        }
    };
}
