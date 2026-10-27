package mz.uem.eda.parking.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** As contas das métricas de um dia: manobras, pico, esperas e ocupação. */
class MetricsTest {

    private static Metrics metrics() {
        return new Metrics("teste", "Colocação", "Atendimento", 1, 10);
    }

    @Test
    void manoeuvresPerExitAndThePeakWindow() {
        Metrics m = metrics();
        m.recordExit(0, 9 * 60);
        m.recordExit(4, Metrics.PEAK_FROM);          // dentro do pico
        m.recordExit(2, Metrics.PEAK_TO - 1);        // dentro do pico
        m.recordExit(6, Metrics.PEAK_TO);            // já fora

        assertEquals(4, m.getExits());
        assertEquals(12, m.getManoeuvres());
        assertEquals(3.0, m.averageManoeuvres(), 1e-9);
        assertEquals(75.0, m.percentExitsWithManoeuvres(), 1e-9);
        assertEquals(6, m.getMaxManoeuvres());
        assertEquals(6, m.getPeakManoeuvres());
        assertEquals(6 * Metrics.SECONDS_PER_MOVE / 60.0, m.peakManoeuvreMinutes(), 1e-9);
    }

    @Test
    void waitsAreCountedPerQueueOnlyForThoseWhoWaited() {
        Metrics m = metrics();
        m.recordEntry(true, 0);
        m.recordEntry(true, 10);
        m.recordEntry(false, 30);
        m.recordEntry(false, 50);

        assertEquals(4, m.getEntries());
        assertEquals(1, m.preferentialWaited());
        assertEquals(10.0, m.preferentialAverageWait(), 1e-9);
        assertEquals(2, m.normalWaited());
        assertEquals(40.0, m.normalAverageWait(), 1e-9);
        assertEquals(50, m.normalMaxWait());
    }

    @Test
    void occupancyAveragePeakAndBusiestHour() {
        Metrics m = metrics();                       // 10 lugares
        for (int minute = 8 * 60; minute < 9 * 60; minute++) {
            m.recordOccupancy(minute, 5);            // 8h: 50 %
        }
        for (int minute = 9 * 60; minute < 10 * 60; minute++) {
            m.recordOccupancy(minute, minute == 9 * 60 + 30 ? 10 : 8);   // 9h: cerca de 80 %
        }

        assertEquals(50.0, m.hourlyOccupancyPercent(8), 1e-9);
        assertEquals((59 * 8 + 10) * 100.0 / (60 * 10), m.hourlyOccupancyPercent(9), 1e-9);
        assertEquals(0.0, m.hourlyOccupancyPercent(10), 1e-9);
        assertEquals(9, m.busiestHour());
        assertEquals(10, m.getPeakOccupied());
        assertEquals(9 * 60 + 30, m.getPeakOccupiedMinute());
        assertEquals((60 * 5 + 59 * 8 + 10) * 100.0 / (120 * 10), m.averageOccupancyPercent(), 1e-9);
    }

    @Test
    void occupancyOutsideTheParkIsRefused() {
        Metrics m = metrics();
        assertThrows(IllegalArgumentException.class, () -> m.recordOccupancy(600, 11));
        assertThrows(IllegalArgumentException.class, () -> m.recordOccupancy(600, -1));
        assertThrows(IllegalArgumentException.class, () -> m.recordOccupancy(24 * 60, 1));
        assertThrows(IllegalArgumentException.class, () -> new Metrics("a", "b", "c", 1, 0));
    }

    @Test
    void theCsvRowHasOneValuePerHeaderColumn() {
        Metrics m = metrics();
        m.recordOccupancy(8 * 60 + 5, 3);
        String[] header = Metrics.csvHeader().split(",");
        String[] row = m.toCsvRow().split(",");

        assertEquals(header.length, row.length);
        assertTrue(m.toCsvRow().contains(",08:05,"));
    }
}
