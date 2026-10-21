package mz.uem.eda.parking.simulation;

import java.util.Random;

import mz.uem.eda.parking.algorithms.QueueRadixSort;
import mz.uem.eda.parking.algorithms.SortKey;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Gera as chegadas de um dia, com o perfil de Maputo (jornada das 8h às
 * 17h) usado no relatório:
 * <ul>
 *   <li><b>70% jornada completa</b>: chegam entre as 7h e as 9h (mais
 *       perto das 8h) e saem entre as 16h e as 18h (mais perto das 17h);</li>
 *   <li><b>25% curta duração</b>: chegam a qualquer hora entre as 8h e as
 *       16h e ficam de 15 minutos a 2 horas;</li>
 *   <li><b>5% permanência longa</b>: chegam entre as 7h e as 10h e ficam
 *       vários dias, por isso não saem durante o dia simulado.</li>
 * </ul>
 *
 * <p>A duração que o condutor <b>declara</b> à entrada não é exactamente a
 * real: leva um erro aleatório até {@code estimateErrorPercent}, para
 * mostrar como as políticas se portam com estimativas imperfeitas.
 *
 * <p><b>Com a mesma semente, o dia é o mesmo.</b> É isso que permite
 * comparar políticas: todas recebem exactamente os mesmos carros, às mesmas
 * horas.
 *
 * <p>As chegadas saem ordenadas por minuto com o {@link QueueRadixSort} do
 * projecto: o simulador consome-as da frente da fila, pela ordem do relógio.
 */
public class ArrivalGenerator {

    /** Percentagens do perfil (somam 100). */
    public static final int FULL_DAY_PERCENT = 70;
    public static final int SHORT_PERCENT = 25;

    /** Um dia de permanência longa dura entre 2 e 5 dias. */
    private static final int LONG_TERM_MIN_DAYS = 2;
    private static final int LONG_TERM_MAX_DAYS = 5;

    private final int carsPerDay;
    private final int preferentialPercent;
    private final int estimateErrorPercent;

    /**
     * Cria o gerador.
     *
     * @param carsPerDay quantos carros chegam no dia, pelo menos 1
     * @param preferentialPercent percentagem de carros de clientes
     *        preferenciais, de 0 a 100
     * @param estimateErrorPercent erro máximo da duração declarada, em
     *        percentagem da real, de 0 a 100
     * @throws IllegalArgumentException se algum valor estiver fora do
     *         intervalo
     */
    public ArrivalGenerator(int carsPerDay, int preferentialPercent, int estimateErrorPercent) {
        if (carsPerDay < 1) {
            throw new IllegalArgumentException("carsPerDay tem de ser pelo menos 1: " + carsPerDay);
        }
        if (preferentialPercent < 0 || preferentialPercent > 100) {
            throw new IllegalArgumentException("preferentialPercent fora de 0..100: " + preferentialPercent);
        }
        if (estimateErrorPercent < 0 || estimateErrorPercent > 100) {
            throw new IllegalArgumentException("estimateErrorPercent fora de 0..100: " + estimateErrorPercent);
        }
        this.carsPerDay = carsPerDay;
        this.preferentialPercent = preferentialPercent;
        this.estimateErrorPercent = estimateErrorPercent;
    }

    /** @return quantos carros chegam por dia */
    public int getCarsPerDay() {
        return carsPerDay;
    }

    /**
     * Gera as chegadas de um dia, ordenadas pelo minuto de chegada.
     *
     * @param seed a semente: o mesmo valor dá sempre o mesmo dia
     * @return as chegadas, da primeira para a última
     * @complexity O(n) para gerar e O(d · n) para ordenar com o radix sort,
     *             d = 4 algarismos do minuto (0 a 1439)
     */
    public Queue<Arrival> generate(long seed) {
        Random random = new Random(seed);
        Queue<Arrival> arrivals = new LinkedQueue<>();
        for (int i = 1; i <= carsPerDay; i++) {
            arrivals.enqueue(next(random, i));
        }
        QueueRadixSort.sort(arrivals, new ByArrivalMinute());
        return arrivals;
    }

    private Arrival next(Random random, int index) {
        String plate = "SIM" + pad(index);
        boolean preferential = random.nextInt(100) < preferentialPercent;
        int kind = random.nextInt(100);
        int arrival;
        int stay;
        StayClass stayClass;
        if (kind < FULL_DAY_PERCENT) {
            stayClass = StayClass.FULL_DAY;
            arrival = triangular(random, 7 * 60, 9 * 60);
            int leave = triangular(random, 16 * 60, 18 * 60);
            stay = leave - arrival;
        } else if (kind < FULL_DAY_PERCENT + SHORT_PERCENT) {
            stayClass = StayClass.SHORT;
            arrival = 8 * 60 + random.nextInt(8 * 60 + 1);
            stay = 15 + random.nextInt(120 - 15 + 1);
        } else {
            stayClass = StayClass.LONG_TERM;
            arrival = 7 * 60 + random.nextInt(3 * 60 + 1);
            stay = (LONG_TERM_MIN_DAYS + random.nextInt(LONG_TERM_MAX_DAYS - LONG_TERM_MIN_DAYS + 1)) * 24 * 60;
        }
        return new Arrival(plate, stayClass, arrival, stay, declared(random, stay), preferential);
    }

    /** A duração declarada: a real com um erro de até ± estimateErrorPercent. */
    private int declared(Random random, int stay) {
        if (estimateErrorPercent == 0) {
            return stay;
        }
        int error = random.nextInt(2 * estimateErrorPercent + 1) - estimateErrorPercent;
        int value = stay + stay * error / 100;
        return value < 1 ? 1 : value;
    }

    /** Um valor entre {@code from} e {@code to}, mais provável a meio (soma de duas uniformes). */
    private static int triangular(Random random, int from, int to) {
        int half = (to - from) / 2;
        return from + random.nextInt(half + 1) + random.nextInt(to - from - half + 1);
    }

    private static String pad(int n) {
        String digits = Integer.toString(n);
        StringBuilder text = new StringBuilder();
        for (int i = digits.length(); i < 5; i++) {
            text.append('0');
        }
        return text.append(digits).toString();
    }

    /**
     * Um carro que chega: matrícula, classe real, minuto de chegada,
     * quanto tempo fica de facto e quanto diz que fica.
     *
     * <p>Os minutos contam-se desde a meia-noite do dia simulado (8h00 é o
     * minuto 480). Imutável.
     */
    public static final class Arrival {

        private final String plate;
        private final StayClass stayClass;
        private final int arrivalMinute;
        private final int stayMinutes;
        private final int declaredMinutes;
        private final boolean preferential;

        /**
         * @param plate a matrícula
         * @param stayClass a classe real da estadia
         * @param arrivalMinute o minuto de chegada, desde a meia-noite
         * @param stayMinutes quanto tempo o carro fica de facto, depois de
         *        entrar, maior que 0
         * @param declaredMinutes quanto o condutor diz que fica, maior que 0
         * @param preferential se o cliente tem direito à fila preferencial
         * @throws IllegalArgumentException se algum tempo for inválido
         */
        public Arrival(String plate, StayClass stayClass, int arrivalMinute, int stayMinutes,
                int declaredMinutes, boolean preferential) {
            if (arrivalMinute < 0 || stayMinutes <= 0 || declaredMinutes <= 0) {
                throw new IllegalArgumentException("tempos inválidos: " + arrivalMinute + ", "
                        + stayMinutes + ", " + declaredMinutes);
            }
            this.plate = plate;
            this.stayClass = stayClass;
            this.arrivalMinute = arrivalMinute;
            this.stayMinutes = stayMinutes;
            this.declaredMinutes = declaredMinutes;
            this.preferential = preferential;
        }

        public String getPlate() {
            return plate;
        }

        /** @return a classe real, que pode não ser a que a duração declarada dá */
        public StayClass getStayClass() {
            return stayClass;
        }

        public int getArrivalMinute() {
            return arrivalMinute;
        }

        public int getStayMinutes() {
            return stayMinutes;
        }

        public int getDeclaredMinutes() {
            return declaredMinutes;
        }

        public boolean isPreferential() {
            return preferential;
        }

        @Override
        public String toString() {
            return plate + " " + stayClass + " " + arrivalMinute + "+" + stayMinutes;
        }
    }

    /** Chave do radix sort: o minuto de chegada (0 a 1439, nunca negativo). */
    public static final class ByArrivalMinute implements SortKey<Arrival> {
        @Override
        public int of(Arrival arrival) {
            return arrival.getArrivalMinute();
        }
    }
}
