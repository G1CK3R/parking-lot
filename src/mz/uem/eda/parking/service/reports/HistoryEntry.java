package mz.uem.eda.parking.service.reports;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.Payment;

/**
 * Uma linha do histórico de estadias (RF20): a sessão e, se o carro já
 * saiu, o pagamento. Só para ler; quem a monta é o {@link ReportService}.
 */
public final class HistoryEntry {

    private final ParkingSession session;
    private final Payment payment;

    public HistoryEntry(ParkingSession session, Payment payment) {
        this.session = session;
        this.payment = payment;
    }

    /** @return a sessão: talão, matrícula, baia, entrada e, se saiu, saída e manobras */
    public ParkingSession getSession() {
        return session;
    }

    /** @return o pagamento da saída, ou {@code null} se o carro ainda está no parque */
    public Payment getPayment() {
        return payment;
    }

    /** @return {@code true} se o carro ainda está no parque */
    public boolean isInPark() {
        return payment == null;
    }
}
