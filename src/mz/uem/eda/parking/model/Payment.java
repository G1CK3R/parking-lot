package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * O pagamento de uma estadia: a sessão paga, a tarifa com que o valor foi
 * calculado, o valor, o método, a hora e o operador que cobrou.
 *
 * <p><b>O pagamento guarda a sua tarifa (ADR-031).</b> Mudar de preços é
 * criar uma {@link Tariff} nova; a antiga deixa de estar em vigor, mas os
 * pagamentos feitos com ela continuam a apontar para ela. Assim, o histórico
 * diz sempre com que preços cada estadia foi cobrada, sem deduções por datas.
 *
 * <p>O valor fica em centavos de metical, num {@code long}, como na
 * {@link Tariff}, e já calculado: mudar de tarifa não muda pagamentos feitos.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class Payment {
    public enum Method {
        CASH,
        MPESA,
        EMOLA,
        CARD
    }

    private final ParkingSession session;
    private final Tariff tariff;
    private final long amountCents;
    private final Method method;
    private final LocalDateTime paidAt;
    private final User operator;

    private long id;

    /**
     * @param session a sessão paga
     * @param tariff a tarifa com que {@code amountCents} foi calculado
     * @param amountCents o valor pago, em centavos de metical
     * @param method o método de pagamento (só registado, ADR-027)
     * @param paidAt a hora do pagamento
     * @param operator o operador que cobrou
     * @throws NullPointerException se algum argumento for {@code null}
     * @throws IllegalArgumentException se o valor for negativo, ou se o
     *         pagamento for anterior à entrada do carro
     */
    public Payment(ParkingSession session, Tariff tariff, long amountCents, Method method,
            LocalDateTime paidAt, User operator) {
        Objects.requireNonNull(session, "session não pode ser null");
        Objects.requireNonNull(tariff, "tariff não pode ser null");
        Objects.requireNonNull(method, "method não pode ser null");
        Objects.requireNonNull(paidAt, "paidAt não pode ser null");
        Objects.requireNonNull(operator, "operator não pode ser null");
        if (amountCents < 0) {
            throw new IllegalArgumentException("amountCents tem de ser positiva: " + amountCents);
        }
        if (paidAt.isBefore(session.getEntryTime())) {
            throw new IllegalArgumentException("paidAt está antes da entrada entry: " + paidAt);
        }
        this.session = session;
        this.tariff = tariff;
        this.amountCents = amountCents;
        this.method = method;
        this.paidAt = paidAt;
        this.operator = operator;
    }

    public ParkingSession getSession() {
        return session;
    }

    /**
     * @return a tarifa com que o valor foi calculado, que pode já não ser a
     *         que está em vigor
     */
    public Tariff getTariff() {
        return tariff;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Method getMethod() {
        return method;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public User getOperator() {
        return operator;
    }

    /**
     * @return o identificador na base de dados, ou 0 se o pagamento ainda
     *         não foi gravado
     */
    public long getId() {
        return id;
    }

     public void setId(long newId) {
        if (newId <= 0) {
            throw new IllegalArgumentException("id tem de ser positivo: " + newId);
        }
        if (id != 0) {
            throw new IllegalStateException("id já definido: " + id);
        }
        id = newId;
    }
}