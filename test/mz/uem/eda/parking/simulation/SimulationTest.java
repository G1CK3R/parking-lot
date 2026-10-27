package mz.uem.eda.parking.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.service.policy.ByDeparture;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.service.policy.StrictPriority;
import mz.uem.eda.parking.simulation.ArrivalGenerator.Arrival;
import mz.uem.eda.parking.structures.Queue;

/** O gerador de chegadas, o simulador de um dia e a comparação das políticas. */
class SimulationTest {

    private static final ArrivalGenerator GENERATOR = new ArrivalGenerator(Simulator.CARS_PER_DAY,
            Simulator.PREFERENTIAL_PERCENT, Simulator.ESTIMATE_ERROR_PERCENT);

    // ---- ArrivalGenerator ------------------------------------------------

    @Test
    void theSameSeedGivesTheSameDayInOrderOfArrival() {
        Queue<Arrival> first = GENERATOR.generate(7);
        Queue<Arrival> second = GENERATOR.generate(7);

        assertEquals(Simulator.CARS_PER_DAY, first.size());
        int previous = -1;
        while (!first.isEmpty()) {
            Arrival a = first.dequeue();
            Arrival b = second.dequeue();
            assertEquals(a.toString(), b.toString());
            assertTrue(a.getArrivalMinute() >= previous);
            previous = a.getArrivalMinute();
        }
    }

    @Test
    void theGeneratorRefusesValuesOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new ArrivalGenerator(0, 15, 20));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalGenerator(100, 101, 20));
        assertThrows(IllegalArgumentException.class, () -> new ArrivalGenerator(100, 15, -1));
    }

    // ---- Simulator --------------------------------------------------------

    @Test
    void everyCarThatArrivesEntersOrIsStillWaiting() {
        Metrics m = new Simulator().run("teste", Simulator.heterogeneousLanes(), new FirstWithSpace(),
                new StrictPriority(), GENERATOR.generate(1), 1);

        assertEquals(Simulator.CARS_PER_DAY, m.getArrivals());
        assertEquals(m.getArrivals(), m.getEntries() + m.getStillWaiting());
        assertTrue(m.getExits() <= m.getEntries());
        assertEquals(200, m.getCapacity());
        assertTrue(m.getPeakOccupied() <= 200);
        assertTrue(m.averageOccupancyPercent() > 0);
    }

    @Test
    void theSameDayGivesTheSameNumbers() {
        Metrics a = new Simulator().run("teste", Simulator.heterogeneousLanes(), new ByDeparture(),
                new StrictPriority(), GENERATOR.generate(3), 3);
        Metrics b = new Simulator().run("teste", Simulator.heterogeneousLanes(), new ByDeparture(),
                new StrictPriority(), GENERATOR.generate(3), 3);

        assertEquals(a.getManoeuvres(), b.getManoeuvres());
        assertEquals(a.normalMaxWait(), b.normalMaxWait());
        assertEquals(a.getPeakOccupied(), b.getPeakOccupied());
    }

    @Test
    void byDepartureNeedsFewerManoeuvresThanFirstWithSpace() {
        long byDeparture = 0;
        long firstWithSpace = 0;
        for (long seed = 1; seed <= 5; seed++) {
            byDeparture += new Simulator().run("teste", Simulator.heterogeneousLanes(), new ByDeparture(),
                    new StrictPriority(), GENERATOR.generate(seed), seed).getManoeuvres();
            firstWithSpace += new Simulator().run("teste", Simulator.heterogeneousLanes(), new FirstWithSpace(),
                    new StrictPriority(), GENERATOR.generate(seed), seed).getManoeuvres();
        }
        assertTrue(byDeparture < firstWithSpace, byDeparture + " >= " + firstWithSpace);
    }

    // ---- Comparison -------------------------------------------------------

    @Test
    void theComparisonCoversEveryCombinationInAFixedOrder() {
        Comparison comparison = new Comparison(GENERATOR, 2);
        assertEquals(16, comparison.size());

        Summary first = comparison.run(0);
        assertEquals(Comparison.HETEROGENEOUS, first.getLayout());
        assertEquals("FirstWithSpace", first.getPlacement());
        assertEquals("StrictPriority", first.getService());
        assertEquals(2, first.getDayCount());

        Summary last = comparison.run(15);
        assertEquals(Comparison.HOMOGENEOUS, last.getLayout());
        assertEquals("ByDeparture", last.getPlacement());
        assertEquals("RoundRobin(" + Comparison.PREFERENTIAL_TURNS + ")", last.getService());

        assertThrows(IllegalArgumentException.class, () -> comparison.run(16));
        assertThrows(IllegalArgumentException.class, () -> new Comparison(GENERATOR, 0));
    }

    @Test
    void theSummaryAveragesTheDays() {
        Summary summary = new Comparison(GENERATOR, 3).run(6);   // heterogénea, ByDeparture, StrictPriority
        double total = 0;
        int max = 0;
        int days = 0;
        for (Metrics day : summary.days()) {
            total += day.averageManoeuvres();
            max = Math.max(max, day.normalMaxWait());
            days++;
        }
        assertEquals(3, days);
        assertEquals(total / 3, summary.getAverageManoeuvres(), 1e-9);
        assertEquals(max, summary.getNormalMaxWait());
        assertTrue(summary.getHourlyOccupancy(summary.getBusiestHour()) >= summary.getHourlyOccupancy(12));
    }
}
