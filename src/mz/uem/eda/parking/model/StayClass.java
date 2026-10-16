package mz.uem.eda.parking.model;

import java.util.Objects;
/**
 * Classe de permanência de uma estadia, declarada à entrada. É o que decide
 * em que baia o carro fica (ADR-004).
 *
 * <p>Cada constante tem uma <b>ordem de saída</b> explícita: quem é de curta
 * duração sai antes de quem faz a jornada completa, e este antes de quem
 * fica muito tempo. É esta ordem que sustenta a regra «empilhar só sobre
 * quem sai depois» e as regras de transbordo.
 *
 * <p>A ordem está num campo próprio, e não na posição de declaração
 * ({@code ordinal()}), para que reordenar as constantes no ficheiro não mude
 * a regra sem ninguém dar por isso.
 *
 * <p>A conversão de uma duração estimada numa classe <b>não</b> vive aqui:
 * depende de como se obtém a duração à entrada, que ainda está em aberto, e
 * é trabalho da camada {@code service}.
 */

public enum StayClass {

    /** Curta duração: minutos ou poucas horas. Baias de profundidade 5. */
    SHORT(1),

    /** Jornada completa: entra de manhã, sai ao fim da tarde. Baias de profundidade 10. */
    FULL_DAY(2),

    /** Permanência longa: dias seguidos (o antigo cliente de avença). Baias de profundidade 10. */
    LONG_TERM(3);

    private final int exitOrder;

    StayClass(int exitOrder) {
        this.exitOrder = exitOrder;
    }


    public boolean leavesBefore(StayClass other) {
        Objects.requireNonNull(other, "other must not be null");
        return this.exitOrder < other.exitOrder;
    }
}
