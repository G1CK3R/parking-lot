package mz.uem.eda.parking.simulation;

import java.util.Objects;

import mz.uem.eda.parking.structures.Queue;

/**
 * O resumo de uma combinação (configuração, colocação, atendimento) ao fim
 * de vários dias simulados: as médias dos dias, os máximos e os próprios
 * dias, para o CSV. É o que a {@link Comparison} devolve, uma linha da
 * tabela de comparação.
 *
 * <p>As médias são médias dos valores de cada dia: a média das manobras por
 * saída de cada dia, a média das esperas médias, e assim por diante. Os
 * máximos (espera e ocupação) são o maior de todos os dias, e os carros que
 * ficaram sem lugar somam-se.
 *
 * <p>Imutável. Calcula tudo no construtor, numa passagem pelos dias.
 */
public final class Summary {

    private final String layout;
    private final String placement;
    private final String service;
    private final Queue<Metrics> days;

    private final double averageManoeuvres;
    private final double percentExitsWithManoeuvres;
    private final double peakManoeuvreMinutes;
    private final double preferentialAverageWait;
    private final int preferentialMaxWait;
    private final double normalAverageWait;
    private final int normalMaxWait;
    private final int notServed;
    private final double averageOccupancyPercent;
    private final int peakOccupied;
    private final int capacity;
    private final double runMillis;
    /** A ocupação média de cada hora do relógio, em percentagem: 24 valores. */
    private final double[] hourlyOccupancy = new double[24];

    /**
     * @param days as métricas dos dias simulados de uma combinação, pelo
     *        menos um; a fila fica guardada, não é consumida
     * @throws IllegalArgumentException se a fila estiver vazia
     */
    Summary(Queue<Metrics> days) {
        Objects.requireNonNull(days, "days não pode ser null");
        if (days.isEmpty()) {
            throw new IllegalArgumentException("é preciso pelo menos um dia simulado");
        }
        Metrics first = days.peek();
        this.layout = first.getLayout();
        this.placement = first.getPlacement();
        this.service = first.getService();
        this.capacity = first.getCapacity();
        this.days = days;

        int n = days.size();
        double moves = 0;
        double withMoves = 0;
        double peak = 0;
        double prefWait = 0;
        double normalWait = 0;
        double occupancy = 0;
        double millis = 0;
        int prefMax = 0;
        int normalMax = 0;
        int waitingAtEnd = 0;
        int peakCars = 0;
        for (Metrics day : days) {
            moves += day.averageManoeuvres();
            withMoves += day.percentExitsWithManoeuvres();
            peak += day.peakManoeuvreMinutes();
            prefWait += day.preferentialAverageWait();
            normalWait += day.normalAverageWait();
            occupancy += day.averageOccupancyPercent();
            millis += day.getRunMillis();
            prefMax = Math.max(prefMax, day.preferentialMaxWait());
            normalMax = Math.max(normalMax, day.normalMaxWait());
            waitingAtEnd += day.getStillWaiting();
            peakCars = Math.max(peakCars, day.getPeakOccupied());
            for (int hour = 0; hour < 24; hour++) {
                hourlyOccupancy[hour] += day.hourlyOccupancyPercent(hour) / n;
            }
        }
        this.averageManoeuvres = moves / n;
        this.percentExitsWithManoeuvres = withMoves / n;
        this.peakManoeuvreMinutes = peak / n;
        this.preferentialAverageWait = prefWait / n;
        this.normalAverageWait = normalWait / n;
        this.averageOccupancyPercent = occupancy / n;
        this.preferentialMaxWait = prefMax;
        this.normalMaxWait = normalMax;
        this.notServed = waitingAtEnd;
        this.peakOccupied = peakCars;
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

    /** @return as métricas de cada dia, para o CSV */
    public Iterable<Metrics> days() {
        return days;
    }

    /** @return quantos dias foram simulados */
    public int getDayCount() {
        return days.size();
    }

    /** @return manobras por saída, média dos dias */
    public double getAverageManoeuvres() {
        return averageManoeuvres;
    }

    /** @return percentagem de saídas com manobras, média dos dias */
    public double getPercentExitsWithManoeuvres() {
        return percentExitsWithManoeuvres;
    }

    /** @return minutos gastos em manobras das 16h às 18h, média dos dias */
    public double getPeakManoeuvreMinutes() {
        return peakManoeuvreMinutes;
    }

    /** @return espera média na fila preferencial, em minutos, média dos dias */
    public double getPreferentialAverageWait() {
        return preferentialAverageWait;
    }

    /** @return a maior espera na fila preferencial em todos os dias, em minutos */
    public int getPreferentialMaxWait() {
        return preferentialMaxWait;
    }

    /** @return espera média na fila normal, em minutos, média dos dias */
    public double getNormalAverageWait() {
        return normalAverageWait;
    }

    /** @return a maior espera na fila normal em todos os dias, em minutos */
    public int getNormalMaxWait() {
        return normalMaxWait;
    }

    /** @return carros que ainda esperavam à meia-noite, somados em todos os dias */
    public int getNotServed() {
        return notServed;
    }

    /** @return ocupação média, em percentagem dos lugares, média dos dias */
    public double getAverageOccupancyPercent() {
        return averageOccupancyPercent;
    }

    /** @return o maior número de lugares ocupados ao mesmo tempo, em todos os dias */
    public int getPeakOccupied() {
        return peakOccupied;
    }

    /** @return quantos lugares tem o parque */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @param hour a hora do relógio, de 0 a 23
     * @return a ocupação média nessa hora, em percentagem, média dos dias
     */
    public double getHourlyOccupancy(int hour) {
        return hourlyOccupancy[hour];
    }

    /** @return a hora com maior ocupação média; com empate, a primeira */
    public int getBusiestHour() {
        int best = 0;
        for (int hour = 1; hour < 24; hour++) {
            if (hourlyOccupancy[hour] > hourlyOccupancy[best]) {
                best = hour;
            }
        }
        return best;
    }

    /** @return quanto tempo real levaram todos os dias desta combinação, em milissegundos */
    public double getRunMillis() {
        return runMillis;
    }
}
