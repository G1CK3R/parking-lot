package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Payment {
    public enum Method {
        CASH,
        MPESA,
        EMOLA,
        CARD
    }

    private final ParkingSession session;
    private final long amountCents;
    private final Method method;
    private final LocalDateTime paidAt;
    private final User operator;

    private long id;
    
    public Payment(ParkingSession session, long amountCents, Method method,
            LocalDateTime paidAt, User operator) {
        Objects.requireNonNull(session, "session não pode ser null");
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
        this.amountCents = amountCents;
        this.method = method;
        this.paidAt = paidAt;
        this.operator = operator;
    }

    public ParkingSession getSession() {
        return session;
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
