package mz.uem.eda.parking.service.billing;

import java.util.Objects;

import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.service.reports.ReportService;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * O histórico dos pagamentos gravados: uma fila, pela ordem em que foram
 * gravados, o mais antigo à frente.
 *
 * <p>Cada pagamento guarda a tarifa com que foi calculado (ADR-031). Por
 * isso, procurar o pagamento de uma sessão antiga devolve também os preços
 * dessa altura, mesmo que a tarifa já tenha sido substituída.
 *
 * <p><b>Porque é uma fila.</b> Os pagamentos chegam por ordem do tempo e são
 * lidos por essa ordem: ao repor o histórico, a {@code persistence} entrega-os
 * do mais antigo para o mais recente, e cada saída nova junta-se no fim. A
 * fila é sobre lista ligada porque o histórico só cresce e não tem tamanho
 * conhecido à partida. Procurar é percorrê-la sem tirar nada (percurso só
 * de leitura), como as baias.
 *
 * <p>O fluxo: ao arrancar, quem coordena carrega as tarifas
 * ({@code TariffRepository.loadAll}), depois os pagamentos
 * ({@code SessionRepository.loadPayments}) e passa cada um a {@link #add}.
 * Depois de cada saída gravada ({@code SessionRepository.recordExit}), passa
 * também o pagamento novo a {@link #add}.
 *
 * <p>A interface recebe {@link Payment}, nunca a fila.
 */
public class PaymentHistoryService {

    private final Queue<Payment> payments = new LinkedQueue<>();

    /**
     * Junta um pagamento gravado ao fim do histórico.
     *
     * @param payment o pagamento, já gravado e com a sessão fechada
     * @throws NullPointerException se {@code payment} for {@code null}
     * @throws IllegalArgumentException se o pagamento ainda não tiver sido
     *         gravado, ou se a sessão ainda estiver activa
     * @complexity O(1)
     */
    public void add(Payment payment) {
        Objects.requireNonNull(payment, "payment não pode ser null");
        if (payment.getId() == 0) {
            throw new IllegalArgumentException("pagamento ainda não gravado: só entram no histórico depois de recordExit");
        }
        if (payment.getSession().isActive()) {
            throw new IllegalArgumentException("a sessão ainda está activa: " + payment.getSession());
        }
        payments.enqueue(payment);
    }

    /**
     * O pagamento da sessão com este talão, com a tarifa com que foi
     * calculado. Cada sessão tem um só pagamento, gravado na saída.
     *
     * @param ticketNumber o número do talão
     * @return o pagamento, ou {@code null} se não houver nenhum para esse
     *         talão
     * @complexity O(n), em que n é o número de pagamentos: busca sequencial,
     *             do mais antigo para o mais recente
     */
    public Payment findByTicket(int ticketNumber) {
        for (Payment payment : payments) {
            if (payment.getSession().getTicketNumber() == ticketNumber) {
                return payment;
            }
        }
        return null;
    }

    /**
     * Percurso só de leitura do histórico, do mais antigo para o mais
     * recente. Para os relatórios ({@link ReportService}).
     *
     * @return os pagamentos
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<Payment> payments() {
        return payments;
    }

    /**
     * @return quantos pagamentos tem o histórico
     * @complexity O(1)
     */
    public int size() {
        return payments.size();
    }
}