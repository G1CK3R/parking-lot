package mz.uem.eda.parking.model;

/**
 * Classe de permanência de uma estadia, declarada à entrada. É o que decide
 * em que baia o carro fica (ADR-004).
 *
 * <p>As constantes estão declaradas <b>pela ordem em que os carros costumam
 * sair</b>: quem é de curta duração sai antes de quem faz a jornada
 * completa, e este antes de quem fica muito tempo. É esta ordem que sustenta
 * a regra «empilhar só sobre quem sai depois» e as regras de transbordo.
 *
 * <p>A conversão de uma duração estimada numa classe <b>não</b> vive aqui:
 * depende de como se obtém a duração à entrada, que ainda está em aberto, e
 * é trabalho da camada {@code service}.
 */
public enum StayClass {

    /** Curta duração: minutos ou poucas horas. Baias de profundidade 5. */
    SHORT,

    /** Jornada completa: entra de manhã, sai ao fim da tarde. Baias de profundidade 10. */
    FULL_DAY,

    /** Permanência longa: dias seguidos (o antigo cliente de avença). Baias de profundidade 10. */
    LONG_TERM;

    /**
     * Indica se um carro desta classe costuma sair antes de um carro da
     * classe {@code other}. Um carro só deve ficar por cima de outro que
     * saia depois dele; ou seja, empilhar {@code a} sobre {@code b} respeita a
     * regra quando {@code a.leavesBefore(b)} ou {@code a == b}.
     *
     * <p>Implementação: compara as posições de declaração das constantes
     * ({@code ordinal()}). Não depender de {@code ordinal()} fora deste
     * método, para que a regra fique num só sítio.
     *
     * @param other a outra classe, nunca {@code null}
     * @return {@code true} se esta classe sair estritamente antes de
     *         {@code other}; {@code false} se for a mesma classe ou sair
     *         depois
     * @throws NullPointerException se {@code other} for {@code null}
     * @complexity O(1)
     */
    public boolean leavesBefore(StayClass other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
