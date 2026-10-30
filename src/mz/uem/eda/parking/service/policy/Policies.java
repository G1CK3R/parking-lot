package mz.uem.eda.parking.service.policy;

import java.util.Objects;

import mz.uem.eda.parking.model.ParkPolicies;

/**
 * Transforma a escolha do administrador ({@link ParkPolicies}, que só tem
 * nomes) nas políticas que os serviços usam.
 *
 * <p>É o único sítio que liga cada nome à sua classe. Uma política nova
 * entra no enumerado de {@link ParkPolicies} e num {@code case} daqui.
 *
 * <p>Cada chamada cria um objecto novo. Para a colocação não faz diferença
 * (as quatro políticas não guardam estado); para a {@link RoundRobin}, quer
 * dizer que a rotação recomeça do zero.
 */
public final class Policies {

    private Policies() {
        // só métodos estáticos
    }

    /**
     * @param policies a escolha do administrador
     * @return a política de colocação escolhida
     * @throws NullPointerException se {@code policies} for {@code null}
     * @complexity O(1)
     */
    public static PlacementPolicy placement(ParkPolicies policies) {
        Objects.requireNonNull(policies, "policies não pode ser null");
        switch (policies.getPlacement()) {
            case FIRST_WITH_SPACE:
                return new FirstWithSpace();
            case LEAST_OCCUPIED:
                return new LeastOccupied();
            case BY_STAY_CLASS:
                return new ByStayClass();
            case BY_DEPARTURE:
                return new ByDeparture();
            default:
                throw new IllegalArgumentException("política de colocação desconhecida: "
                        + policies.getPlacement());
        }
    }

    /**
     * @param policies a escolha do administrador
     * @return a política de atendimento escolhida; a rotação usa o k da
     *         escolha
     * @throws NullPointerException se {@code policies} for {@code null}
     * @complexity O(1)
     */
    public static ServicePolicy service(ParkPolicies policies) {
        Objects.requireNonNull(policies, "policies não pode ser null");
        switch (policies.getService()) {
            case STRICT_PRIORITY:
                return new StrictPriority();
            case ROUND_ROBIN:
                return new RoundRobin(policies.getPreferentialTurns());
            default:
                throw new IllegalArgumentException("política de atendimento desconhecida: "
                        + policies.getService());
        }
    }
}
