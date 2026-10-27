package mz.uem.eda.parking.simulation;

import java.util.Locale;
import java.util.Objects;

/**
 * Os números de uma simulação: o que o relatório compara entre políticas.
 *
 * <ul>
 *   <li><b>Manobras por saída</b>: a métrica central das políticas de
 *       colocação. Tirar um carro à profundidade k custa 2k manobras.</li>
 *   <li><b>Pico das 16h às 18h</b>: as manobras feitas nas saídas dessa
 *       janela, e o tempo que levam a {@value #SECONDS_PER_MOVE} segundos por
 *       manobra. É a medida de «quanto custa esvaziar o parque ao fim do
 *       dia».</li>
 *   <li><b>Espera por fila</b>: quantos carros esperaram em cada fila, e
 *       quanto, em média e no máximo. É a métrica das políticas de
 *       atendimento; a inanição vê-se no máximo da fila normal.</li>
 *   <li><b>Ocupação (RF19)</b>: quantos lugares estavam ocupados em cada
 *       minuto. Dá a ocupação média do dia, o momento de maior ocupação e a
 *       ocupação média de cada hora, para ver as horas de pico.</li>
 * </ul>
 *
 * <p>Os contadores só sobem; o {@code Simulator} chama {@link #recordEntry}
 * e {@link #recordExit} à medida que o dia avança.
 */
public class Metrics {

    /** Segundos que uma manobra leva, para converter manobras em tempo (estimativa). */
    public static final int SECONDS_PER_MOVE = 45;

    /** Janela do pico de saídas, em minutos desde a meia-noite: 16h00 a 18h00. */
    public static final int PEAK_FROM = 16 * 60;
    public static final int PEAK_TO = 18 * 60;

    private final String layout;
    private final String placement;
    private final String service;
    private final long seed;
    private final int capacity;

    private int arrivals;
    private int entries;
    private int exits;
    private int stillWaiting;

    private long manoeuvres;
    private int maxManoeuvres;
    private int exitsWithManoeuvres;
    private int peakExits;
    private long peakManoeuvres;

    private final Wait preferential = new Wait();
    private final Wait normal = new Wait();

    // Ocupação: a soma dos lugares ocupados minuto a minuto, no dia e em cada hora.
    // Os dois vectores são só 24 contadores, um por hora do relógio, não uma colecção de dados.
    private long occupiedMinutes;
    private int occupancySamples;
    private int peakOccupied;
    private int peakOccupiedMinute;
    private final long[] occupiedByHour = new long[24];
    private final int[] samplesByHour = new int[24];

    private double runMillis;

    /**
     * @param layout o nome da configuração das baias
     * @param placement o nome da política de colocação
     * @param service o nome da política de atendimento
     * @param seed a semente do dia simulado
     * @param capacity quantos lugares tem o parque, sem a baia de manobra
     * @throws IllegalArgumentException se {@code capacity} não for positiva
     */
    public Metrics(String layout, String placement, String service, long seed, int capacity) {
        this.layout = Objects.requireNonNull(layout, "layout não pode ser null");
        this.placement = Objects.requireNonNull(placement, "placement não pode ser null");
        this.service = Objects.requireNonNull(service, "service não pode ser null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity tem de ser positiva: " + capacity);
        }
        this.seed = seed;
        this.capacity = capacity;
    }

    /** Conta um carro que chegou ao parque. */
    public void recordArrival() {
        arrivals++;
    }

    /**
     * Conta um carro que entrou, e quanto esperou na fila.
     *
     * @param preferentialQueue se veio (ou viria) da fila preferencial
     * @param waitedMinutes os minutos entre chegar e entrar, 0 se entrou logo
     */
    public void recordEntry(boolean preferentialQueue, int waitedMinutes) {
        if (waitedMinutes < 0) {
            throw new IllegalArgumentException("waitedMinutes negativo: " + waitedMinutes);
        }
        entries++;
        if (waitedMinutes > 0) {
            (preferentialQueue ? preferential : normal).add(waitedMinutes);
        }
    }

    /**
     * Conta uma saída e as manobras que custou.
     *
     * @param manoeuvreCount as manobras do plano (2k)
     * @param minute o minuto da saída, desde a meia-noite
     */
    public void recordExit(int manoeuvreCount, int minute) {
        if (manoeuvreCount < 0) {
            throw new IllegalArgumentException("manoeuvreCount negativo: " + manoeuvreCount);
        }
        exits++;
        manoeuvres += manoeuvreCount;
        if (manoeuvreCount > 0) {
            exitsWithManoeuvres++;
        }
        if (manoeuvreCount > maxManoeuvres) {
            maxManoeuvres = manoeuvreCount;
        }
        if (minute >= PEAK_FROM && minute < PEAK_TO) {
            peakExits++;
            peakManoeuvres += manoeuvreCount;
        }
    }

    /**
     * Regista quantos lugares estavam ocupados num minuto do dia. O
     * {@code Simulator} chama-o uma vez por minuto, no fim do minuto.
     *
     * @param minute o minuto, desde a meia-noite (0 a 1439)
     * @param occupied quantos lugares estavam ocupados
     * @throws IllegalArgumentException se o minuto ou a ocupação estiverem
     *         fora do intervalo
     */
    public void recordOccupancy(int minute, int occupied) {
        if (minute < 0 || minute >= 24 * 60) {
            throw new IllegalArgumentException("minuto fora do dia: " + minute);
        }
        if (occupied < 0 || occupied > capacity) {
            throw new IllegalArgumentException("ocupação fora de 0.." + capacity + ": " + occupied);
        }
        occupiedMinutes += occupied;
        occupancySamples++;
        if (occupied > peakOccupied) {
            peakOccupied = occupied;
            peakOccupiedMinute = minute;
        }
        occupiedByHour[minute / 60] += occupied;
        samplesByHour[minute / 60]++;
    }

    /** Regista quantos carros ainda esperavam quando o dia acabou. */
    public void setStillWaiting(int count) {
        this.stillWaiting = count;
    }

    /** Regista quanto tempo real a simulação demorou. */
    public void setRunMillis(double millis) {
        this.runMillis = millis;
    }

    /** @return o nome da configuração das baias */
    public String getLayout() {
        return layout;
    }

    /** @return o nome da política de colocação */
    public String getPlacement() {
        return placement;
    }

    /** @return o nome da política de atendimento */
    public String getService() {
        return service;
    }

    /** @return a semente do dia simulado */
    public long getSeed() {
        return seed;
    }

    public int getArrivals() {
        return arrivals;
    }

    public int getEntries() {
        return entries;
    }

    public int getExits() {
        return exits;
    }

    public int getStillWaiting() {
        return stillWaiting;
    }

    public long getManoeuvres() {
        return manoeuvres;
    }

    public int getMaxManoeuvres() {
        return maxManoeuvres;
    }

    public long getPeakManoeuvres() {
        return peakManoeuvres;
    }

    /** @return quanto tempo real a simulação deste dia demorou, em milissegundos */
    public double getRunMillis() {
        return runMillis;
    }

    /** @return manobras por saída, em média; 0 se ninguém saiu */
    public double averageManoeuvres() {
        return exits == 0 ? 0 : (double) manoeuvres / exits;
    }

    /** @return percentagem de saídas que obrigaram a mexer noutros carros */
    public double percentExitsWithManoeuvres() {
        return exits == 0 ? 0 : 100.0 * exitsWithManoeuvres / exits;
    }

    /** @return minutos gastos em manobras no pico, a {@value #SECONDS_PER_MOVE} s cada */
    public double peakManoeuvreMinutes() {
        return peakManoeuvres * SECONDS_PER_MOVE / 60.0;
    }

    /** @return quantos carros esperaram na fila preferencial */
    public int preferentialWaited() {
        return preferential.count;
    }

    /** @return quantos carros esperaram na fila normal */
    public int normalWaited() {
        return normal.count;
    }

    /** @return espera média na fila preferencial, em minutos, de quem esperou */
    public double preferentialAverageWait() {
        return preferential.average();
    }

    /** @return espera média na fila normal, em minutos, de quem esperou */
    public double normalAverageWait() {
        return normal.average();
    }

    /** @return a maior espera na fila preferencial, em minutos */
    public int preferentialMaxWait() {
        return preferential.max;
    }

    /** @return a maior espera na fila normal, em minutos */
    public int normalMaxWait() {
        return normal.max;
    }

    /** @return quantos lugares tem o parque, sem a baia de manobra */
    public int getCapacity() {
        return capacity;
    }

    /** @return a ocupação média do dia simulado, em percentagem dos lugares */
    public double averageOccupancyPercent() {
        return occupancySamples == 0 ? 0 : 100.0 * occupiedMinutes / ((double) occupancySamples * capacity);
    }

    /** @return o maior número de lugares ocupados ao mesmo tempo */
    public int getPeakOccupied() {
        return peakOccupied;
    }

    /** @return o primeiro minuto (desde a meia-noite) em que se chegou a {@link #getPeakOccupied()} */
    public int getPeakOccupiedMinute() {
        return peakOccupiedMinute;
    }

    /**
     * @param hour a hora do relógio, de 0 a 23
     * @return a ocupação média nessa hora, em percentagem; 0 se a hora não
     *         foi simulada
     */
    public double hourlyOccupancyPercent(int hour) {
        return samplesByHour[hour] == 0 ? 0
                : 100.0 * occupiedByHour[hour] / ((double) samplesByHour[hour] * capacity);
    }

    /** @return a hora com maior ocupação média; com empate, a primeira */
    public int busiestHour() {
        int best = 0;
        for (int hour = 1; hour < 24; hour++) {
            if (hourlyOccupancyPercent(hour) > hourlyOccupancyPercent(best)) {
                best = hour;
            }
        }
        return best;
    }

    /** @return o cabeçalho do CSV, com as colunas de {@link #toCsvRow()} */
    public static String csvHeader() {
        return "layout,placement,service,seed,arrivals,entries,exits,still_waiting,"
                + "manoeuvres,avg_manoeuvres_per_exit,max_manoeuvres,pct_exits_with_manoeuvres,"
                + "peak_exits,peak_manoeuvres,peak_manoeuvre_minutes,"
                + "pref_waited,pref_avg_wait,pref_max_wait,normal_waited,normal_avg_wait,normal_max_wait,"
                + "capacity,avg_occupancy_pct,peak_occupied,peak_occupied_at,busiest_hour,run_ms";
    }

    /** @return uma linha CSV, com ponto decimal, na ordem de {@link #csvHeader()} */
    public String toCsvRow() {
        return String.format(Locale.ROOT,
                "%s,%s,%s,%d,%d,%d,%d,%d,%d,%.3f,%d,%.1f,%d,%d,%.1f,%d,%.1f,%d,%d,%.1f,%d,%d,%.1f,%d,%02d:%02d,%d,%.2f",
                layout, placement, service, seed, arrivals, entries, exits, stillWaiting,
                manoeuvres, averageManoeuvres(), maxManoeuvres, percentExitsWithManoeuvres(),
                peakExits, peakManoeuvres, peakManoeuvreMinutes(),
                preferential.count, preferential.average(), preferential.max,
                normal.count, normal.average(), normal.max, capacity, averageOccupancyPercent(), peakOccupied,
                peakOccupiedMinute / 60, peakOccupiedMinute % 60, busiestHour(), runMillis);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "%s/%s/%s: %d saídas, %.2f manobras/saída, pico %d manobras (%.0f min); "
                        + "espera P %.0f min (máx %d), N %.0f min (máx %d)",
                layout, placement, service, exits, averageManoeuvres(), peakManoeuvres,
                peakManoeuvreMinutes(), preferential.average(), preferential.max,
                normal.average(), normal.max);
    }

    /** Contador de esperas de uma fila. */
    private static final class Wait {
        private int count;
        private long total;
        private int max;

        void add(int minutes) {
            count++;
            total += minutes;
            if (minutes > max) {
                max = minutes;
            }
        }

        double average() {
            return count == 0 ? 0 : (double) total / count;
        }
    }
}
