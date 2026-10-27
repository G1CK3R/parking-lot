package mz.uem.eda.parking.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;

import mz.uem.eda.parking.algorithms.QueueRadixSort;
import mz.uem.eda.parking.algorithms.SortKey;
import mz.uem.eda.parking.algorithms.TwoStackSort;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

/**
 * Calcula os relatórios a partir do histórico de pagamentos: a receita de
 * um período (RF18) e o fecho de caixa de um turno (RF21). Não guarda
 * nada nem grava nada; só lê o histórico e devolve resultados novos.
 *
 * <p><b>Só filas e pilhas.</b> Os relatórios são onde os algoritmos da
 * camada {@code algorithms} trabalham a sério:
 * <ul>
 * <li><b>Radix sort com 10 filas</b> ({@link QueueRadixSort}), O(d·n) e sem
 * comparações, para ordenar os pagamentos do período pela hora, pelo
 * talão, pelo valor, pelo operador e pelo método. É estável, por isso dois
 * pagamentos com a mesma chave ficam pela ordem da hora.</li>
 * <li><b>Inversão com uma pilha</b>, O(n), para pôr a ordem por valor do
 * maior para o menor: o radix sort ordena por ordem crescente, e tirar tudo
 * de uma pilha devolve a ordem ao contrário.</li>
 * <li><b>Totais por grupos seguidos</b>, O(n): depois de ordenar pela
 * chave, os pagamentos do mesmo dia (ou operador, ou método) ficam todos
 * seguidos na fila, e basta uma passagem para os somar. Assim não é
 * preciso uma tabela de dispersão.</li>
 * <li><b>Ordenação com duas pilhas</b> ({@link TwoStackSort}), O(n²), para
 * os pagamentos de um turno, que são poucos. O número de comparações fica
 * no {@link CashClose}, para comparar com o radix sort.</li>
 * </ul>
 */
public final class ReportService {

    private ReportService() {
    }

    /**
     * A receita de um período: os pagamentos com hora de pagamento entre
     * {@code from} (incluído) e {@code to} (excluído).
     *
     * @param history o histórico de pagamentos, em qualquer ordem
     * @param from o início do período, incluído
     * @param to o fim do período, excluído
     * @param operator o nome do operador a que o relatório se limita, ou
     *        {@code null} para todos
     * @return o relatório, com os pagamentos e os totais
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(d·n) para n pagamentos no período (mais O(N) para os
     *             escolher do histórico de N); d são os algarismos da maior
     *             chave
     */
    public static RevenueReport revenue(Iterable<Payment> history, LocalDateTime from, LocalDateTime to,
            String operator) {
        Objects.requireNonNull(history, "history não pode ser null");
        Objects.requireNonNull(from, "from não pode ser null");
        Objects.requireNonNull(to, "to não pode ser null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("o período tem de começar antes de acabar: " + from + " a " + to);
        }

        // 1. Escolher os pagamentos do período (e do operador), somando a receita.
        Queue<Payment> byTime = new LinkedQueue<>();
        long total = 0;
        for (Payment payment : history) {
            LocalDateTime paidAt = payment.getPaidAt();
            boolean inPeriod = !paidAt.isBefore(from) && paidAt.isBefore(to);
            boolean ofOperator = operator == null || operator.equals(payment.getOperator().getUsername());
            if (inPeriod && ofOperator) {
                byTime.enqueue(payment);
                total += payment.getAmountCents();
            }
        }

        // 2. Ordenar pela hora: chave = minutos desde o início do período.
        QueueRadixSort.sort(byTime, minutesSince(from));

        // 3. As outras ordens partem de cópias da ordem pela hora, para os empates ficarem pela hora.
        Queue<Payment> byTicket = copy(byTime);
        QueueRadixSort.sort(byTicket, TICKET);
        Queue<Payment> byAmount = copy(byTime);
        QueueRadixSort.sort(byAmount, AMOUNT);
        byAmount = reversed(byAmount);

        // 4. Totais: com a fila ordenada pela chave, cada grupo fica seguido.
        Queue<RevenueLine<LocalDate>> byDay = runs(byTime, DAY);
        Queue<Payment> byOperatorId = copy(byTime);
        QueueRadixSort.sort(byOperatorId, OPERATOR_ID);
        Queue<RevenueLine<String>> byOperator = runs(byOperatorId, OPERATOR_NAME);
        Queue<Payment> byMethodOrder = copy(byTime);
        QueueRadixSort.sort(byMethodOrder, METHOD);
        Queue<RevenueLine<Payment.Method>> byMethod = runs(byMethodOrder, METHOD_LABEL);

        return new RevenueReport(from, to, operator, total, byTime, byAmount, byTicket, byDay, byOperator,
                byMethod);
    }

    /**
     * O fecho de caixa de um turno: o que {@code operator} cobrou entre
     * {@code from} e {@code to}, com os pagamentos do maior para o menor,
     * ordenados com duas pilhas.
     *
     * @param history o histórico de pagamentos
     * @param operator o operador do turno
     * @param from o início do turno, normalmente a hora a que entrou
     * @param to o fim do turno, normalmente agora
     * @return o fecho de caixa
     * @throws IllegalArgumentException se {@code from} não for antes de {@code to}
     * @complexity O(d·n) para os totais e O(n²) para a ordem por valor, com
     *             n pagamentos no turno
     */
    public static CashClose cashClose(Iterable<Payment> history, User operator, LocalDateTime from,
            LocalDateTime to) {
        Objects.requireNonNull(operator, "operator não pode ser null");
        RevenueReport report = revenue(history, from, to, operator.getUsername());

        Stack<Payment> input = new LinkedStack<>();
        for (Payment payment : report.paymentsByTime()) {
            input.push(payment);
        }
        TwoStackSort<Payment> sorter = new TwoStackSort<>();
        Stack<Payment> sorted = sorter.sort(input, BY_AMOUNT);
        // A pilha ordenada tem o maior valor no topo: tirar tudo dá do maior para o menor.
        Queue<Payment> byAmount = new LinkedQueue<>();
        while (!sorted.isEmpty()) {
            byAmount.enqueue(sorted.pop());
        }
        return new CashClose(report, byAmount, sorter.getComparisons());
    }

    // ------------------------------------------------------------------
    // Ajudantes
    // ------------------------------------------------------------------

    /** Copia uma fila sem a mudar: percorre-a e enfileira cada elemento numa nova. */
    private static Queue<Payment> copy(Queue<Payment> source) {
        Queue<Payment> copy = new LinkedQueue<>();
        for (Payment payment : source) {
            copy.enqueue(payment);
        }
        return copy;
    }

    /** Inverte a ordem de uma fila com uma pilha; a fila de entrada fica vazia. */
    private static Queue<Payment> reversed(Queue<Payment> source) {
        Stack<Payment> stack = new LinkedStack<>();
        while (!source.isEmpty()) {
            stack.push(source.dequeue());
        }
        Queue<Payment> result = new LinkedQueue<>();
        while (!stack.isEmpty()) {
            result.enqueue(stack.pop());
        }
        return result;
    }

    /**
     * Soma os grupos seguidos de uma fila já ordenada pela chave: cada vez
     * que a chave muda, fecha-se uma linha.
     */
    private static <K> Queue<RevenueLine<K>> runs(Queue<Payment> sorted, Label<K> label) {
        Queue<RevenueLine<K>> lines = new LinkedQueue<>();
        K current = null;
        int count = 0;
        long total = 0;
        for (Payment payment : sorted) {
            K key = label.of(payment);
            if (count > 0 && !key.equals(current)) {
                lines.enqueue(new RevenueLine<>(current, count, total));
                count = 0;
                total = 0;
            }
            current = key;
            count++;
            total += payment.getAmountCents();
        }
        if (count > 0) {
            lines.enqueue(new RevenueLine<>(current, count, total));
        }
        return lines;
    }

    /** O que identifica o grupo de um pagamento, para os totais. */
    private interface Label<K> {
        K of(Payment payment);
    }

    /** Chave da hora: os minutos desde o início do período. */
    private static SortKey<Payment> minutesSince(final LocalDateTime start) {
        return new SortKey<Payment>() {
            @Override
            public int of(Payment payment) {
                return Math.toIntExact(Duration.between(start, payment.getPaidAt()).toMinutes());
            }
        };
    }

    private static final SortKey<Payment> TICKET = new SortKey<Payment>() {
        @Override
        public int of(Payment payment) {
            return payment.getSession().getTicketNumber();
        }
    };

    private static final SortKey<Payment> AMOUNT = new SortKey<Payment>() {
        @Override
        public int of(Payment payment) {
            return Math.toIntExact(payment.getAmountCents());
        }
    };

    private static final SortKey<Payment> OPERATOR_ID = new SortKey<Payment>() {
        @Override
        public int of(Payment payment) {
            return Math.toIntExact(payment.getOperator().getId());
        }
    };

    private static final SortKey<Payment> METHOD = new SortKey<Payment>() {
        @Override
        public int of(Payment payment) {
            return payment.getMethod().ordinal();
        }
    };

    private static final Label<LocalDate> DAY = new Label<LocalDate>() {
        @Override
        public LocalDate of(Payment payment) {
            return payment.getPaidAt().toLocalDate();
        }
    };

    private static final Label<String> OPERATOR_NAME = new Label<String>() {
        @Override
        public String of(Payment payment) {
            return payment.getOperator().getUsername();
        }
    };

    private static final Label<Payment.Method> METHOD_LABEL = new Label<Payment.Method>() {
        @Override
        public Payment.Method of(Payment payment) {
            return payment.getMethod();
        }
    };

    private static final Comparator<Payment> BY_AMOUNT = new Comparator<Payment>() {
        @Override
        public int compare(Payment a, Payment b) {
            return Long.compare(a.getAmountCents(), b.getAmountCents());
        }
    };
}
