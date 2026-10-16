package mz.uem.eda.parking.model;

import java.time.LocalDateTime;

/**
 * O pagamento de uma estadia (RF15). Sem pagamento registado, a saída não se
 * conclui (RF08).
 *
 * <p>O valor é em centavos de metical, num {@code long}, pela razão
 * explicada em {@link Tariff}.
 *
 * <p>Registar o pagamento não fecha a sessão sozinho: é a camada
 * {@code service} que, com o pagamento feito, chama
 * {@link ParkingSession#close(LocalDateTime, int)}, as duas coisas na mesma
 * transacção.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class Payment {

    /** Método de pagamento (RF15). No MVP os pagamentos são simulados. */
    public enum Method {
        CASH,
        MPESA,
        EMOLA,
        CARD
    }

    /**
     * Regista um pagamento.
     *
     * @param session a sessão paga, nunca {@code null}
     * @param amountCents o valor cobrado, em centavos; 0 ou mais (uma estadia
     *        dentro da tolerância gratuita paga 0)
     * @param method o método de pagamento, nunca {@code null}
     * @param paidAt a hora do pagamento, nunca {@code null}
     * @param operator o operador que recebeu o pagamento, nunca {@code null};
     *        é o que permite a receita por operador (RF18)
     * @throws NullPointerException se algum argumento de objecto for
     *         {@code null}
     * @throws IllegalArgumentException se {@code amountCents} for negativo
     */
    public Payment(ParkingSession session, long amountCents, Method method,
            LocalDateTime paidAt, User operator) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a sessão paga */
    public ParkingSession getSession() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o valor cobrado, em centavos de metical */
    public long getAmountCents() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o método de pagamento */
    public Method getMethod() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a hora do pagamento */
    public LocalDateTime getPaidAt() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o operador que recebeu o pagamento */
    public User getOperator() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se o pagamento ainda não
     *         foi gravado
     */
    public long getId() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
