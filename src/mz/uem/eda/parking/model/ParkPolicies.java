package mz.uem.eda.parking.model;

import java.util.Objects;

/**
 * As políticas com que o parque trabalha, escolhidas pelo administrador:
 * a de colocação (em que baia fica o carro que entra) e a de atendimento
 * (de qual fila de espera sai o próximo carro), com o número de
 * preferenciais seguidos da rotação.
 *
 * <p><b>Porque está no {@code model}.</b> Guarda só a escolha, pelo nome,
 * e não os objectos das políticas. Assim a camada {@code persistence} grava
 * e repõe a escolha sem conhecer a camada {@code service}, e é a
 * {@code service} que transforma cada nome na política verdadeira.
 *
 * <p>Os nomes das constantes são os que ficam na base, em
 * {@code park_state}, como nos outros enumerados do esquema.
 *
 * <p>O número de preferenciais seguidos guarda-se mesmo com a prioridade
 * estrita, que não o usa: se o administrador voltar à rotação, o valor
 * anterior continua lá.
 *
 * <p>Imutável: mudar de políticas é criar um objecto novo.
 */
public final class ParkPolicies {

    /** As políticas de colocação. */
    public enum Placement {
        /** A primeira baia com espaço, pela ordem da configuração. */
        FIRST_WITH_SPACE,
        /** A baia com menos carros. */
        LEAST_OCCUPIED,
        /** A baia da classe de permanência, com transbordo (ADR-005). */
        BY_STAY_CLASS,
        /** Pela hora de saída declarada (a usada desde 27 Out). */
        BY_DEPARTURE
    }

    /** As políticas de atendimento das filas de espera. */
    public enum Service {
        /** A fila preferencial primeiro, sempre. */
        STRICT_PRIORITY,
        /** k preferenciais seguidos, depois um normal. */
        ROUND_ROBIN
    }

    /** O menor número de preferenciais seguidos na rotação. */
    public static final int MIN_PREFERENTIAL_TURNS = 1;

    /** O maior número de preferenciais seguidos na rotação. */
    public static final int MAX_PREFERENTIAL_TURNS = 10;

    private final Placement placement;
    private final Service service;
    private final int preferentialTurns;

    /**
     * @param placement a política de colocação
     * @param service a política de atendimento
     * @param preferentialTurns quantos preferenciais seguidos antes de um
     *        normal, na rotação; de {@value #MIN_PREFERENTIAL_TURNS} a
     *        {@value #MAX_PREFERENTIAL_TURNS}
     * @throws NullPointerException se {@code placement} ou {@code service}
     *         for {@code null}
     * @throws IllegalArgumentException se {@code preferentialTurns} estiver
     *         fora dos limites
     */
    public ParkPolicies(Placement placement, Service service, int preferentialTurns) {
        this.placement = Objects.requireNonNull(placement, "placement não pode ser null");
        this.service = Objects.requireNonNull(service, "service não pode ser null");
        if (preferentialTurns < MIN_PREFERENTIAL_TURNS || preferentialTurns > MAX_PREFERENTIAL_TURNS) {
            throw new IllegalArgumentException("preferentialTurns tem de estar entre "
                    + MIN_PREFERENTIAL_TURNS + " e " + MAX_PREFERENTIAL_TURNS + ": " + preferentialTurns);
        }
        this.preferentialTurns = preferentialTurns;
    }

    public Placement getPlacement() {
        return placement;
    }

    public Service getService() {
        return service;
    }

    /** @return k, os preferenciais seguidos antes de um normal na rotação */
    public int getPreferentialTurns() {
        return preferentialTurns;
    }

    /**
     * Diz se o atendimento das duas escolhas funciona da mesma maneira: a
     * mesma política e, na rotação, o mesmo k. Com a prioridade estrita, o
     * k não conta.
     *
     * @param other a outra escolha
     * @return {@code true} se o atendimento for o mesmo
     * @throws NullPointerException se {@code other} for {@code null}
     */
    public boolean sameService(ParkPolicies other) {
        if (service != other.service) {
            return false;
        }
        return service == Service.STRICT_PRIORITY || preferentialTurns == other.preferentialTurns;
    }

    /**
     * Diz se as duas escolhas põem o parque a funcionar da mesma maneira:
     * a mesma colocação e o mesmo atendimento ({@link #sameService}).
     *
     * <p>Não é um {@code equals}: as escolhas nunca se guardam em colecções
     * e, com a prioridade estrita, duas escolhas com k diferentes contam como
     * iguais.
     *
     * @param other a outra escolha
     * @return {@code true} se nada mudar no funcionamento do parque
     * @throws NullPointerException se {@code other} for {@code null}
     */
    public boolean sameAs(ParkPolicies other) {
        return placement == other.placement && sameService(other);
    }

    @Override
    public String toString() {
        return placement + "/" + service + "/" + preferentialTurns;
    }
}
