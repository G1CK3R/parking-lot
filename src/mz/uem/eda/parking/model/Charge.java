package mz.uem.eda.parking.model;

import java.time.LocalDateTime;

/**
 * Um valor cobrado ao balcão: o pagamento de uma estadia ({@link Payment})
 * ou a venda de uma avença mensal ({@link MonthlyPass}). Os relatórios de
 * receita e o fecho de caixa somam os dois da mesma maneira: quanto, por que
 * método, quando e por quem.
 */
public interface Charge {

    /** @return o valor cobrado, em centavos de metical */
    long getAmountCents();

    /** @return o método de pagamento */
    Payment.Method getMethod();

    /** @return a hora a que foi cobrado */
    LocalDateTime getPaidAt();

    /** @return o operador que cobrou */
    User getOperator();

    /** @return a matrícula a que o valor diz respeito */
    String getPlate();
}
