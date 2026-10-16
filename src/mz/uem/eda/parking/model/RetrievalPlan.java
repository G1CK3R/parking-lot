package mz.uem.eda.parking.model;

import java.util.Iterator;

/**
 * O plano para tirar um carro de uma baia: a sequência completa de
 * movimentos, calculada de uma vez pela camada {@code service} antes de se
 * mexer em qualquer carro (ADR-014). Numa baia-pilha, com o carro à
 * profundidade k: k manobras para a baia de manobra, a saída do carro, e k
 * manobras de volta, pela ordem inversa.
 *
 * <p>Por dentro é uma {@code Queue<Move>}: os movimentos saem pela ordem em
 * que foram acrescentados, que é a ordem de execução. A estrutura aparece
 * aqui sem ser forçada, porque reproduzir um plano é consumir uma fila. A
 * fila interna deve ser uma {@code LinkedQueue}, porque o número de
 * movimentos depende do tipo de baia.
 *
 * <p>A interface consome o plano com {@link #hasMoves()} e
 * {@link #nextMove()} e nunca vê a fila. O percurso de {@link #iterator()} é
 * só de leitura, para imprimir o plano na consola ou num teste.
 *
 * <p>Não é o plano que move os carros: a camada {@code service} executa os
 * mesmos passos sobre as estruturas reais; o plano descreve-os.
 */
public class RetrievalPlan implements Iterable<Move> {

    /**
     * Cria um plano vazio.
     *
     * @param target a sessão do carro a tirar, nunca {@code null}
     * @param depth a profundidade do carro na baia, contada a partir do topo
     *        (0 = está no topo), 0 ou mais
     * @throws NullPointerException se {@code target} for {@code null}
     * @throws IllegalArgumentException se {@code depth} for negativo
     */
    public RetrievalPlan(ParkingSession target, int depth) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Acrescenta um movimento ao fim do plano. Os movimentos têm de chegar
     * pela ordem: o primeiro com {@code getSequence() == 1}, o seguinte com
     * 2, e assim por diante. Se o movimento for uma manobra
     * ({@link Move#isManoeuvre()}), conta para {@link #getManoeuvreCount()}.
     *
     * @param move o movimento, nunca {@code null}
     * @throws NullPointerException se {@code move} for {@code null}
     * @throws IllegalArgumentException se a ordem do movimento não for
     *         {@code getTotalMoves() + 1}
     * @complexity O(1)
     */
    public void addMove(Move move) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Retira e devolve o próximo movimento a reproduzir.
     *
     * @return o movimento mais antigo ainda por reproduzir
     * @throws IllegalStateException se já não houver movimentos
     * @complexity O(1)
     */
    public Move nextMove() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return {@code true} se ainda houver movimentos por reproduzir
     * @complexity O(1)
     */
    public boolean hasMoves() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return quantos movimentos ainda estão por reproduzir
     * @complexity O(1)
     */
    public int remainingMoves() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return quantos movimentos foram acrescentados ao todo; não diminui
     *         com {@link #nextMove()}
     * @complexity O(1)
     */
    public int getTotalMoves() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Quantas manobras o plano custa: os movimentos de outros carros, sem
     * contar a saída do carro pretendido. Numa baia-pilha é 2k. É o número
     * que vai para {@link ParkingSession#close(java.time.LocalDateTime, int)}
     * e para as métricas, e o que a porta de 1 de Novembro verifica. Não
     * diminui com {@link #nextMove()}.
     *
     * @return o número de manobras acrescentadas
     * @complexity O(1)
     */
    public int getManoeuvreCount() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a sessão do carro a tirar */
    public ParkingSession getTarget() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a profundidade do carro quando o plano foi calculado */
    public int getDepth() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Percurso só de leitura dos movimentos ainda por reproduzir, pela ordem
     * de execução. Não reproduz nada: o plano fica igual. {@code remove()}
     * lança {@code UnsupportedOperationException}.
     *
     * @return um iterador novo sobre os movimentos restantes
     * @complexity O(1) para obter; O(n) para percorrer
     */
    @Override
    public Iterator<Move> iterator() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
