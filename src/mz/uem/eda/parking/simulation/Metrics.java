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

    private double runMillis;

    /**
     * @param layout o nome da configuração das baias
     * @param placement o nome da política de colocação
     * @param service o nome da política de atendimento
     * @param seed a semente do dia simulado
     */
    public Metrics(String layout, String placement, String service, long seed) {
        this.layout = Objects.requireNonNull(layout, "layout não pode ser null");
        this.placement = Objects.requireNonNull(placement, "placement não pode ser null");
        this.service = Objects.requireNonNull(service, "service não pode ser null");
        this.seed = seed;
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

    /** Regista quantos carros ainda esperavam quando o dia acabou. */
    public void setStillWaiting(int count) {
        this.stillWaiting = count;
    }

    /** Regista quanto tempo real a simulação demorou. */
    public void setRunMillis(double millis) {
        this.runMillis = millis;
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

    /** @return o cabeçalho do CSV, com as colunas de {@link #toCsvRow()} */
    public static String csvHeader() {
        return "layout,placement,service,seed,arrivals,entries,exits,still_waiting,"
                + "manoeuvres,avg_manoeuvres_per_exit,max_manoeuvres,pct_exits_with_manoeuvres,"
                + "peak_exits,peak_manoeuvres,peak_manoeuvre_minutes,"
                + "pref_waited,pref_avg_wait,pref_max_wait,normal_waited,normal_avg_wait,normal_max_wait,"
                + "run_ms";
    }

    /** @return uma linha CSV, com ponto decimal, na ordem de {@link #csvHeader()} */
    public String toCsvRow() {
        return String.format(Locale.ROOT,
                "%s,%s,%s,%d,%d,%d,%d,%d,%d,%.3f,%d,%.1f,%d,%d,%.1f,%d,%.1f,%d,%d,%.1f,%d,%.2f",
                layout, placement, service, seed, arrivals, entries, exits, stillWaiting,
                manoeuvres, averageManoeuvres(), maxManoeuvres, percentExitsWithManoeuvres(),
                peakExits, peakManoeuvres, peakManoeuvreMinutes(),
                preferential.count, preferential.average(), preferential.max,
                normal.count, normal.average(), normal.max, runMillis);
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
