package mz.uem.eda.parking.model;

import java.util.Objects;

import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

/**
 * O plano para tirar um carro de uma baia: a sequência completa de
 * movimentos, registada pela camada {@code service} enquanto executa a
 * saída nas pilhas reais (ADR-014). Com o carro à profundidade k: k
 * manobras para a baia de manobra, a saída do carro, e k manobras de volta,
 * pela ordem inversa.
 *
 * <p>Por dentro é uma fila de {@link Move}: os movimentos saem pela ordem em
 * que foram acrescentados, que é a ordem em que aconteceram. A fila aparece
 * aqui por necessidade do problema, porque reproduzir um plano é consumir os
 * movimentos pela ordem em que foram registados (FIFO). É uma
 * {@link LinkedQueue}, porque o número de movimentos depende da
 * profundidade do carro.
 *
 * <p>A interface consome o plano com {@link #hasMoves()} e
 * {@link #nextMove()}, um movimento por tique do temporizador, e nunca vê a
 * fila. O percurso de {@link #moves()} é só de leitura, para imprimir o
 * plano na consola ou num teste.
 *
 * <p>Não é o plano que move os carros: quando a interface o recebe, as
 * pilhas já estão no estado final. O plano só descreve o que aconteceu,
 * para ser mostrado.
 */

public class RetrievalPlan {

    private final ParkingSession target;
    private final int depth;
    private final Queue<Move> moves;

    /** Movimentos acrescentados ao todo; não diminui com {@link #nextMove()}. */
    private int totalMoves;

    /** Manobras acrescentadas (movimentos entre duas baias); não diminui com {@link #nextMove()}. */
    private int manoeuvreCount;

    public RetrievalPlan(ParkingSession target, int depth ) {
        Objects.requireNonNull(target, "target must not be null");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative: " + depth);
        }
        this.target = target;
        this.depth = depth;
        this.moves = new LinkedQueue<>();
    }

    
     public void addMove(Move move) {
        Objects.requireNonNull(move, "move não pode ser null");
        if(move.getSequence() != totalMoves + 1){
            throw new IllegalArgumentException("Movimento esperado: " + (totalMoves +1)
                + "mas teve: "  + move.getSequence());
        }
        moves.enqueue(move);
        totalMoves++;
        if (move.isManoeuvre()) {
            manoeuvreCount++;
        }
    }
    /**
     * Retira e devolve o próximo movimento a reproduzir.
     *
     * @return o movimento mais antigo ainda por reproduzir
     * @throws EmptyStructureException se já não houver movimentos
     * @complexity O(1)
     */
    public Move nextMove() {
        return moves.dequeue();
    }

    public boolean hasMove() {
        return !moves.isEmpty();
    }

    public int remainingMoves() {
        return moves.size();
    }

     /**
     * @return quantos movimentos foram acrescentados ao todo; não diminui
     *         com {@link #nextMove()}
     * @complexity O(1)
     */
    public int getTotalMoves() {
        return totalMoves;
    }

    public int getManoeuvreCount() {
        return manoeuvreCount;
    }

    public ParkingSession getTarget() {
        return target;
    }

    public int getDepth() {
        return depth;
    }

     /**
     * Percurso só de leitura dos movimentos ainda por reproduzir, pela ordem
     * em que aconteceram. Não reproduz nada: o plano fica igual.
     *
     * <p>Não chamar {@link #addMove} nem {@link #nextMove} durante o percurso
     * (ver secção 1.1 da Justificação de métodos).
     *
     * @return os movimentos restantes
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<Move> moves() {
        return moves;
    }
}
