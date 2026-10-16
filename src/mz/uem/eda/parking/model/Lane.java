package mz.uem.eda.parking.model;

import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

/**
 * Uma baia: um corredor onde os carros ficam uns atrás dos outros (ADR-001).
 * O tipo vem da topologia (ADR-002): beco sem saída é {@link Kind#STACK},
 * faixa atravessável é {@link Kind#QUEUE}.
 *
 * <p>Guarda as {@link ParkingSession} dos carros que lá estão, numa
 * estrutura de capacidade fixa, porque a profundidade física da baia se
 * conhece à partida (ADR-008): uma {@code ArrayStack} nas baias-pilha e uma
 * {@code CircularQueue} nas baias-fila. Quem usa a baia só vê as interfaces
 * {@link Stack} e {@link Queue}.
 *
 * <p><b>A baia de manobra também é uma {@code Lane}</b>, criada com
 * {@link #buffer(String, int)}: é sempre uma pilha, não tem classe de
 * permanência e {@link #isBuffer()} devolve {@code true}. Não há uma classe
 * {@code BufferLane} à parte, tal como a tabela {@code lanes} não tem uma
 * tabela à parte, só a coluna {@code is_buffer}.
 *
 * <p>Sem lógica de negócio: a baia não escolhe onde se põe um carro, não
 * aplica a regra «empilhar só sobre quem sai depois» e não sabe tirar um
 * carro do meio. Isso é trabalho das camadas {@code service} e
 * {@code service.policy}.
 *
 * <p><b>A interface nunca chama {@link #asStack()} nem {@link #asQueue()}.</b>
 * Recebe dados já preparados pela camada {@code service}.
 */
public class Lane {

    /** Topologia da baia (ADR-002); corresponde à coluna {@code lanes.kind}. */
    public enum Kind {
        /** Beco sem saída: entra e sai pelo mesmo extremo (LIFO). */
        STACK,
        /** Faixa atravessável: entra de um lado e sai do outro (FIFO). */
        QUEUE
    }

    /**
     * Cria uma baia-pilha vazia.
     *
     * @param code o código da baia (por exemplo {@code "L01"}), nunca
     *        {@code null} nem vazio, único no parque
     * @param capacity a profundidade física, pelo menos 1
     * @param stayClass a classe de permanência a que a baia se destina, nunca
     *        {@code null}
     * @return a baia, sem carros
     * @throws NullPointerException se {@code code} ou {@code stayClass} for
     *         {@code null}
     * @throws IllegalArgumentException se {@code code} estiver vazio ou
     *         {@code capacity} for menor que 1
     * @complexity O(c), c = capacidade, para reservar o vector
     */
    public static Lane stack(String code, int capacity, StayClass stayClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Cria uma baia-fila vazia. Nenhuma das 22 baias do dimensionamento
     * actual é fila; o tipo existe para a configuração (RF04).
     *
     * @param code o código da baia, nunca {@code null} nem vazio, único no
     *        parque
     * @param capacity a profundidade física, pelo menos 1
     * @param stayClass a classe de permanência a que a baia se destina, nunca
     *        {@code null}
     * @return a baia, sem carros
     * @throws NullPointerException se {@code code} ou {@code stayClass} for
     *         {@code null}
     * @throws IllegalArgumentException se {@code code} estiver vazio ou
     *         {@code capacity} for menor que 1
     * @complexity O(c), c = capacidade, para reservar o vector
     */
    public static Lane queue(String code, int capacity, StayClass stayClass) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Cria a baia de manobra vazia: uma pilha sem classe de permanência,
     * onde se encostam os carros retirados de cima durante uma saída
     * (ADR-003). Para que nenhum carro seja irrecuperável, a capacidade deve
     * ser igual à profundidade da baia mais funda.
     *
     * @param code o código da baia, nunca {@code null} nem vazio, único no
     *        parque
     * @param capacity a capacidade, pelo menos 1
     * @return a baia de manobra, sem carros
     * @throws NullPointerException se {@code code} for {@code null}
     * @throws IllegalArgumentException se {@code code} estiver vazio ou
     *         {@code capacity} for menor que 1
     * @complexity O(c), c = capacidade, para reservar o vector
     */
    public static Lane buffer(String code, int capacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o código da baia, nunca {@code null} nem vazio */
    public String getCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o tipo da baia; a baia de manobra é sempre {@link Kind#STACK} */
    public Kind getKind() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a profundidade física, o número máximo de carros */
    public int getCapacity() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return a classe de permanência a que a baia se destina, ou
     *         {@code null} na baia de manobra. Com transbordo, a baia pode
     *         ter carros de outra classe; isto diz só a que se destina.
     */
    public StayClass getStayClass() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return {@code true} se esta for a baia de manobra */
    public boolean isBuffer() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return quantos carros estão na baia neste momento
     * @complexity O(1)
     */
    public int size() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return quantos carros ainda cabem: capacidade menos ocupação (RF05)
     * @complexity O(1)
     */
    public int freeSpace() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return {@code true} se a baia não tiver carros
     * @complexity O(1)
     */
    public boolean isEmpty() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return {@code true} se a baia não tiver espaço para mais um carro
     * @complexity O(1)
     */
    public boolean isFull() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Dá acesso à pilha de uma baia-pilha, para a camada {@code service}
     * empilhar e desempilhar. É a própria estrutura da baia, não uma cópia:
     * o que se fizer nela muda a baia.
     *
     * @return a pilha com as sessões, topo = carro mais recente
     * @throws IllegalStateException se a baia for {@link Kind#QUEUE}
     * @complexity O(1)
     */
    public Stack<ParkingSession> asStack() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Dá acesso à fila de uma baia-fila, para a camada {@code service}
     * enfileirar e desenfileirar. É a própria estrutura da baia, não uma
     * cópia: o que se fizer nela muda a baia.
     *
     * @return a fila com as sessões, frente = próximo carro a sair
     * @throws IllegalStateException se a baia for {@link Kind#STACK}
     * @complexity O(1)
     */
    public Queue<ParkingSession> asQueue() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Percurso só de leitura das sessões da baia, pela ordem em que sairiam:
     * do topo para o fundo numa baia-pilha, da frente para trás numa
     * baia-fila. Serve qualquer tipo de baia sem perguntar qual é, e é o que
     * a busca sequencial e a preparação dos ecrãs usam.
     *
     * <p>Não alterar a baia durante o percurso (ver secção 1.1 da
     * Justificação de métodos).
     *
     * @return as sessões, pela ordem de saída
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<ParkingSession> contents() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se a baia ainda não foi
     *         gravada
     */
    public long getId() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Duas baias são iguais se tiverem o mesmo código. */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Coerente com {@link #equals(Object)}: calculado só a partir do código. */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o código e a ocupação, por exemplo {@code "L03 7/10"} */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
