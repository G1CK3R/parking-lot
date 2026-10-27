package mz.uem.eda.parking.service.views;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.RetrievalPlan;

/**
 * O que uma saída devolve à interface: o pagamento, para o recibo; o plano
 * de movimentos, para o painel reproduzir a manobra; e o carro da fila de
 * espera que entrou no lugar libertado, se algum entrou.
 *
 * <p>Só guarda dados, não tem lógica. Imutável.
 */
public class ExitResult {

    private final Payment payment;
    private final RetrievalPlan plan;
    private final ParkingSession admitted;

    /**
     * @param payment o pagamento gravado
     * @param plan os movimentos que tiraram o carro e repuseram a baia
     * @param admitted a sessão do carro que saiu da fila de espera, ou
     *        {@code null} se ninguém entrou
     */
    public ExitResult(Payment payment, RetrievalPlan plan, ParkingSession admitted) {
        this.payment = payment;
        this.plan = plan;
        this.admitted = admitted;
    }

    /** @return o pagamento, já gravado na base */
    public Payment getPayment() {
        return payment;
    }

    /**
     * @return o plano da saída. É o original: quem chamar
     *         {@link RetrievalPlan#nextMove()} vai esvaziando-o
     */
    public RetrievalPlan getPlan() {
        return plan;
    }

    /**
     * @return a sessão do carro que entrou da fila de espera, ou
     *         {@code null} se a fila estava vazia
     */
    public ParkingSession getAdmitted() {
        return admitted;
    }
}