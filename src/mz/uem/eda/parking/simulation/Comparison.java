package mz.uem.eda.parking.simulation;

import java.util.Objects;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.service.policy.ByDeparture;
import mz.uem.eda.parking.service.policy.ByStayClass;
import mz.uem.eda.parking.service.policy.FirstWithSpace;
import mz.uem.eda.parking.service.policy.LeastOccupied;
import mz.uem.eda.parking.service.policy.PlacementPolicy;
import mz.uem.eda.parking.service.policy.RoundRobin;
import mz.uem.eda.parking.service.policy.ServicePolicy;
import mz.uem.eda.parking.service.policy.StrictPriority;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * A comparação completa das políticas: 2 configurações de baias × 4
 * políticas de colocação × 2 de atendimento, 16 combinações, cada uma
 * simulada nos mesmos dias (sementes 1, 2, …, {@code days}).
 *
 * <p>Cada combinação corre-se à parte, com {@link #run(int)}, para quem
 * chama poder mostrar o progresso: o {@link Simulator#main} escreve na
 * consola, o ecrã de simulação avança uma barra. Cada dia usa baias,
 * serviços e políticas novos, por isso nada é partilhado entre dias nem com
 * o parque real.
 *
 * <p>A ordem das combinações é fixa: primeiro a configuração, depois a
 * colocação, depois o atendimento.
 */
public class Comparison {

    /** Os nomes das duas configurações, como aparecem no CSV. */
    public static final String HETEROGENEOUS = "heterogénea";
    public static final String HOMOGENEOUS = "homogénea";

    /** O k da rotação k:1 da política de atendimento {@link RoundRobin}. */
    public static final int PREFERENTIAL_TURNS = 3;

    public static final int LAYOUTS = 2;
    public static final int PLACEMENTS = 4;
    public static final int SERVICES = 2;

    private final ArrivalGenerator generator;
    private final int days;
    private final Simulator simulator = new Simulator();

    /**
     * @param generator gera as chegadas de cada dia
     * @param days quantos dias simular por combinação, pelo menos 1
     * @throws IllegalArgumentException se {@code days} for menor que 1
     */
    public Comparison(ArrivalGenerator generator, int days) {
        this.generator = Objects.requireNonNull(generator, "generator não pode ser null");
        if (days < 1) {
            throw new IllegalArgumentException("days tem de ser pelo menos 1: " + days);
        }
        this.days = days;
    }

    /** @return quantas combinações há: {@value #LAYOUTS} × {@value #PLACEMENTS} × {@value #SERVICES} */
    public int size() {
        return LAYOUTS * PLACEMENTS * SERVICES;
    }

    /** @return quantos dias se simulam por combinação */
    public int getDays() {
        return days;
    }

    /**
     * Simula uma combinação em todos os dias.
     *
     * @param index a combinação, de 0 a {@code size() - 1}
     * @return o resumo dos dias
     * @throws IllegalArgumentException se o índice estiver fora do intervalo
     * @complexity O(D · M · n): D dias, M minutos por dia, n carros no parque
     */
    public Summary run(int index) {
        if (index < 0 || index >= size()) {
            throw new IllegalArgumentException("combinação fora de 0.." + (size() - 1) + ": " + index);
        }
        int layout = index / (PLACEMENTS * SERVICES);
        int placement = index / SERVICES % PLACEMENTS;
        int service = index % SERVICES;

        Queue<Metrics> results = new LinkedQueue<>();
        for (long seed = 1; seed <= days; seed++) {
            Queue<Lane> lanes = layout == 0 ? Simulator.heterogeneousLanes() : Simulator.homogeneousLanes();
            String name = layout == 0 ? HETEROGENEOUS : HOMOGENEOUS;
            results.enqueue(simulator.run(name, lanes, placement(placement), service(service),
                    generator.generate(seed), seed));
        }
        return new Summary(results);
    }

    /** Uma política de colocação nova, pela ordem da comparação. */
    static PlacementPolicy placement(int index) {
        switch (index) {
            case 0:
                return new FirstWithSpace();
            case 1:
                return new LeastOccupied();
            case 2:
                return new ByStayClass();
            default:
                return new ByDeparture();
        }
    }

    /** Uma política de atendimento nova (guarda estado, por isso uma por dia). */
    static ServicePolicy service(int index) {
        return index == 0 ? new StrictPriority() : new RoundRobin(PREFERENTIAL_TURNS);
    }
}
