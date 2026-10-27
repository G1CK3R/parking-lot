package mz.uem.eda.parking.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Uma avença mensal (RF16): uma matrícula que pagou ao balcão um valor fixo
 * por um mês de calendário e, durante esse mês, sai sem pagar a estadia.
 *
 * <p>O mês vai do primeiro ao último dia, inclusive. A avença é paga de uma
 * vez, no dia da venda; esse valor conta na receita desse dia, como qualquer
 * outro pagamento ({@link Charge}).
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class MonthlyPass implements Charge {

    private final String customerName;
    private final String phone;
    private final String plate;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final long amountCents;
    private final Payment.Method method;
    private final LocalDateTime paidAt;
    private final User operator;
    private long id;

    /**
     * @param customerName o nome do cliente
     * @param phone o telefone, ou {@code null}
     * @param plate a matrícula, com ou sem espaços e hífenes
     * @param month o mês de calendário que a avença cobre
     * @param amountCents o valor pago, em centavos
     * @param method o método com que foi paga (nunca {@link Payment.Method#PASS})
     * @param paidAt a hora da venda
     * @param operator o operador que vendeu
     * @throws IllegalArgumentException se o nome ou a matrícula estiverem
     *         vazios, se o valor for negativo, ou se o método for
     *         {@code PASS}
     */
    public MonthlyPass(String customerName, String phone, String plate, YearMonth month, long amountCents,
            Payment.Method method, LocalDateTime paidAt, User operator) {
        Objects.requireNonNull(customerName, "customerName não pode ser null");
        Objects.requireNonNull(month, "month não pode ser null");
        Objects.requireNonNull(method, "method não pode ser null");
        Objects.requireNonNull(paidAt, "paidAt não pode ser null");
        Objects.requireNonNull(operator, "operator não pode ser null");
        String normalized = Vehicle.normalizePlate(plate);
        if (customerName.isBlank()) {
            throw new IllegalArgumentException("o nome do cliente não pode estar vazio");
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("a matrícula não pode estar vazia: '" + plate + "'");
        }
        if (amountCents < 0) {
            throw new IllegalArgumentException("amountCents não pode ser negativo: " + amountCents);
        }
        if (method == Payment.Method.PASS) {
            throw new IllegalArgumentException("uma avença não se paga com outra avença");
        }
        this.customerName = customerName.strip();
        this.phone = phone == null || phone.isBlank() ? null : phone.strip();
        this.plate = normalized;
        this.validFrom = month.atDay(1);
        this.validUntil = month.atEndOfMonth();
        this.amountCents = amountCents;
        this.method = method;
        this.paidAt = paidAt;
        this.operator = operator;
    }

    /**
     * @param day o dia
     * @return {@code true} se a avença cobrir esse dia
     */
    public boolean coversDay(LocalDate day) {
        Objects.requireNonNull(day, "day não pode ser null");
        return !day.isBefore(validFrom) && !day.isAfter(validUntil);
    }

    /**
     * @param plateToCheck a matrícula, com ou sem espaços e hífenes
     * @param at a hora
     * @return {@code true} se a avença for desta matrícula e cobrir este dia
     */
    public boolean covers(String plateToCheck, LocalDateTime at) {
        return plate.equals(Vehicle.normalizePlate(plateToCheck)) && coversDay(at.toLocalDate());
    }

    /** @return o mês que a avença cobre */
    public YearMonth getMonth() {
        return YearMonth.from(validFrom);
    }

    public String getCustomerName() {
        return customerName;
    }

    /** @return o telefone, ou {@code null} se não foi indicado */
    public String getPhone() {
        return phone;
    }

    @Override
    public String getPlate() {
        return plate;
    }

    /** @return o primeiro dia coberto */
    public LocalDate getValidFrom() {
        return validFrom;
    }

    /** @return o último dia coberto, inclusive */
    public LocalDate getValidUntil() {
        return validUntil;
    }

    @Override
    public long getAmountCents() {
        return amountCents;
    }

    @Override
    public Payment.Method getMethod() {
        return method;
    }

    @Override
    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    @Override
    public User getOperator() {
        return operator;
    }

    /** @return o identificador na base de dados, ou 0 se ainda não foi gravada */
    public long getId() {
        return id;
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param newId o identificador, maior que 0
     */
    public void setId(long newId) {
        if (newId <= 0) {
            throw new IllegalArgumentException("id tem de ser positivo: " + newId);
        }
        if (id != 0) {
            throw new IllegalStateException("id já definido: " + id);
        }
        id = newId;
    }

    @Override
    public String toString() {
        return plate + " " + getMonth();
    }
}
