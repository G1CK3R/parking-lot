package mz.uem.eda.parking.service.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServicePolicyTest {

    // ---- StrictPriority -------------------------------------------------

    @Test
    void strictPriorityAlwaysPrefersThePreferentialQueue() {
        ServicePolicy policy = new StrictPriority();
        assertTrue(policy.servePreferential(1, 100));
        assertTrue(policy.servePreferential(5, 0));
        assertFalse(policy.servePreferential(0, 3));
    }

    @Test
    void strictPriorityRejectsEmptyQueues() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrictPriority().servePreferential(0, 0));
    }

    // ---- RoundRobin -----------------------------------------------------

    @Test
    void roundRobinServesKPreferentialThenOneNormal() {
        ServicePolicy policy = new RoundRobin(3);
        assertEquals("PPPNPPPNPP", sequence(policy, 10));
    }

    @Test
    void roundRobinWithOneAlternates() {
        assertEquals("PNPNPN", sequence(new RoundRobin(1), 6));
    }

    @Test
    void roundRobinServesTheOtherQueueWhenOneIsEmpty() {
        ServicePolicy policy = new RoundRobin(1);
        assertTrue(policy.servePreferential(4, 0));
        assertTrue(policy.servePreferential(4, 0));
        assertFalse(policy.servePreferential(0, 4));
    }

    @Test
    void roundRobinRestartsTheTurnAfterThePreferentialQueueEmpties() {
        RoundRobin policy = new RoundRobin(2);
        assertTrue(policy.servePreferential(1, 5));     // P, 1 seguido
        assertFalse(policy.servePreferential(0, 5));    // fila P vazia: N, recomeça
        assertTrue(policy.servePreferential(3, 4));     // P
        assertTrue(policy.servePreferential(2, 4));     // P
        assertFalse(policy.servePreferential(1, 4));    // vez da N
    }

    @Test
    void roundRobinValidates() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobin(0));
        assertThrows(IllegalArgumentException.class,
                () -> new RoundRobin(2).servePreferential(0, 0));
        assertEquals(2, new RoundRobin(2).getPreferentialTurns());
    }

    /** Atende n vezes com as duas filas sempre cheias; P ou N por atendimento. */
    private static String sequence(ServicePolicy policy, int n) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < n; i++) {
            text.append(policy.servePreferential(100, 100) ? 'P' : 'N');
        }
        return text.toString();
    }
}
