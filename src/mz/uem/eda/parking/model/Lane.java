package mz.uem.eda.parking.model;

import java.util.Objects;

import mz.uem.eda.parking.structures.ArrayStack;
import mz.uem.eda.parking.structures.Stack;

public class Lane {

    private final String code;
    private final int capacity;
    private final StayClass stayClass;
    private final boolean buffer;

    private final Stack<ParkingSession>  stack;

    private long id;

  
    private Lane(String code, int capacity, StayClass stayClass, boolean buffer) {
        Objects.requireNonNull(code, "code Não pode ser null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code não pode estár em branco");
        }
        if (!buffer) {
            Objects.requireNonNull(stayClass, "stayClass não pode ser null");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1: " + capacity);
        }
        this.code = code;
        this.capacity = capacity;
        this.stayClass = stayClass;
        this.buffer = buffer;
        this.stack = new ArrayStack<>(capacity);
    }
    
      public static Lane parking(String code, int capacity, StayClass stayClass) {
        return new Lane(code, capacity, stayClass, false);
    }
    
    public static Lane buffer(String code, int capacity) {
        return new Lane(code, capacity, null, true);
    }

    /** @return o código da baia, nunca {@code null} nem vazio */
    public String getCode() {
        return code;
    }

    /** @return a profundidade física, o número máximo de carros */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @return a classe de permanência a que a baia se destina, ou
     *         {@code null} na baia de manobra. Com transbordo, a baia pode
     *         ter carros de outra classe; isto diz só a que se destina.
     */
    public StayClass getStayClass() {
        return stayClass;
    }

    /** @return {@code true} se esta for a baia de manobra */
    public boolean isBuffer() {
        return buffer;
    }

    /**
     * @return quantos carros estão na baia neste momento
     * @complexity O(1)
     */
    public int size() {
        return stack.size();
    }

    /**
     * @return quantos carros ainda cabem: capacidade menos ocupação (RF05)
     * @complexity O(1)
     */
    public int freeSpace() {
        return capacity - stack.size();
    }

    /**
     * @return {@code true} se a baia não tiver carros
     * @complexity O(1)
     */
    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /**
     * @return {@code true} se a baia não tiver espaço para mais um carro
     * @complexity O(1)
     */
    public boolean isFull() {
        return stack.isFull();
    }

    /**
     * Dá acesso à pilha da baia, para a camada {@code service} empilhar e
     * desempilhar. É a própria estrutura da baia, não uma cópia: o que se
     * fizer nela muda a baia.
     *
     * @return a pilha com as sessões, topo = carro mais recente
     * @complexity O(1)
     */
    public Stack<ParkingSession> asStack() {
        return stack;
    }

    /**
     * Percurso só de leitura das sessões da baia, do topo para o fundo, que
     * é a ordem em que sairiam. É o que a busca sequencial e a preparação
     * dos ecrãs usam: quem recebe um {@code Iterable} só consegue percorrer,
     * não empilhar nem desempilhar.
     *
     * <p>Não alterar a baia durante o percurso (ver secção 1.1 da
     * Justificação de métodos).
     *
     * @return as sessões, do topo para o fundo
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<ParkingSession> contents() {
        return stack;
    }

    /**
     * @return o identificador na base de dados, ou 0 se a baia ainda não foi
     *         gravada
     */
    public long getId() {
        return id;
    }

    public void setId(long newId) {
         if (newId <= 0) {
            throw new IllegalArgumentException("id tem de ser positivo: " + newId);
        }
        if (id != 0) {
            throw new IllegalStateException("Já definido: " + id);
        }
        id = newId;
    }

    @Override 
    public boolean equals(Object other) {
         if(this == other) {
            return true;
         }
         if(!(other instanceof Lane)) {
            return false;
         }
         return code.equals(((Lane) other).code);
    }


    @Override 
    public String toString() {
        return code + " " + stack.size() + "/" + capacity;
    }
 
}
