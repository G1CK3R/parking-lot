package mz.uem.eda.parking.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

import mz.uem.eda.parking.model.MonthlyPass;
import mz.uem.eda.parking.model.Payment;
import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * As avenças mensais (RF16): vende-as e diz se uma matrícula tem uma avença
 * válida a uma certa hora.
 *
 * <p><b>Porque é uma fila.</b> As avenças vendem-se pela ordem do tempo e
 * lêem-se por essa ordem (relatórios, lista do ecrã). Procurar a avença de
 * uma matrícula é uma busca sequencial, só de leitura, como nas baias: com
 * algumas dezenas de clientes, O(a) não pesa.
 *
 * <p>Cada avença cobre um mês de calendário e uma matrícula. Uma matrícula
 * só pode ter uma avença por mês. O valor é o da tarifa em vigor no momento
 * da venda.
 */
public class MonthlyPassService {

    private final Queue<MonthlyPass> passes = new LinkedQueue<>();

    /**
     * Repõe uma avença gravada, no fim da fila. Só a {@code persistence}
     * chama este método, no arranque, pela ordem de venda.
     *
     * @param pass a avença gravada
     */
    public void load(MonthlyPass pass) {
        passes.enqueue(Objects.requireNonNull(pass, "pass não pode ser null"));
    }

    /**
     * Vende uma avença para um mês.
     *
     * @param customerName o nome do cliente
     * @param phone o telefone, ou {@code null}
     * @param plate a matrícula
     * @param month o mês que a avença cobre: o actual ou um seguinte
     * @param method o método de pagamento
     * @param now a hora da venda
     * @param operator o operador que vende
     * @param tariff a tarifa em vigor, que dá o valor
     * @return a avença vendida, já no fim da fila
     * @throws IllegalArgumentException se o mês já tiver passado, ou se o
     *         nome, a matrícula ou o método não servirem
     * @throws IllegalStateException se a matrícula já tiver avença para esse mês
     * @complexity O(a), a = avenças vendidas
     */
    public MonthlyPass sell(String customerName, String phone, String plate, YearMonth month,
            Payment.Method method, LocalDateTime now, User operator, Tariff tariff) {
        Objects.requireNonNull(month, "month não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");
        Objects.requireNonNull(tariff, "tariff não pode ser null");
        if (month.isBefore(YearMonth.from(now))) {
            throw new IllegalArgumentException("o mês já passou: " + month);
        }
        if (find(plate, month) != null) {
            throw new IllegalStateException("a matrícula já tem avença para " + month + ": "
                    + Vehicle.normalizePlate(plate));
        }
        MonthlyPass pass = new MonthlyPass(customerName, phone, plate, month, tariff.getMonthlyPassCents(),
                method, now, operator);
        passes.enqueue(pass);
        return pass;
    }

    /**
     * A avença que cobre esta matrícula a esta hora.
     *
     * @param plate a matrícula, com ou sem espaços e hífenes
     * @param at a hora
     * @return a avença, ou {@code null} se não houver
     * @complexity O(a), busca sequencial
     */
    public MonthlyPass validFor(String plate, LocalDateTime at) {
        Objects.requireNonNull(at, "at não pode ser null");
        for (MonthlyPass pass : passes) {
            if (pass.covers(plate, at)) {
                return pass;
            }
        }
        return null;
    }

    /**
     * A avença desta matrícula para este mês.
     *
     * @param plate a matrícula
     * @param month o mês
     * @return a avença, ou {@code null} se não houver
     * @complexity O(a)
     */
    public MonthlyPass find(String plate, YearMonth month) {
        String wanted = Vehicle.normalizePlate(plate);
        for (MonthlyPass pass : passes) {
            if (pass.getPlate().equals(wanted) && pass.getMonth().equals(month)) {
                return pass;
            }
        }
        return null;
    }

    /**
     * @return todas as avenças, pela ordem de venda
     * @complexity O(1) para obter; O(a) para percorrer
     */
    public Iterable<MonthlyPass> passes() {
        return passes;
    }

    /** @return quantas avenças foram vendidas; O(1) */
    public int size() {
        return passes.size();
    }
}
