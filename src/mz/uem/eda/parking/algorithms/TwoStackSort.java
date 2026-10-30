package mz.uem.eda.parking.algorithms;

import java.util.Comparator;
 
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Stack;

/**
 * Ordenação com duas pilhas: a pilha ordenada fica crescente do fundo para o
 * topo, e desempilhá-la dá os valores do maior para o menor. Conta as
 * comparações, os elementos devolvidos à entrada e os push.
 *
 * @param <T> o tipo dos elementos
 */
public class TwoStackSort<T> {
    private long comparisons;  //Comparações feitas
    private long returns; // quantidade de elementos devolvidos à entrada
    private long pushes; // total de push nas duas pilhas

    /**
     * Ordena os elementos da pilha de entrada numa pilha nova. A pilha de
     * entrada fica vazia.
     *
     * @param input a pilha a ordenar
     * @param order a ordem dos elementos
     * @return a pilha ordenada, com o maior no topo
     * @throws IllegalArgumentException se algum argumento for null
     * @complexity O(n²) comparações no pior caso, O(n) no melhor (entrada já
     *             na ordem certa); memória extra O(n)
     */
    public Stack<T> sort(Stack<T> input, Comparator<T> order) {
        if(input == null || order == null){
            throw new IllegalArgumentException("input e order não podem ser null");
        }

        comparisons =  0;
        returns = 0;
        pushes = 0;

        Stack<T> sorted =  new LinkedStack<T>();

        while(!input.isEmpty()) {
            T tmp =  input.pop();

            while(!sorted.isEmpty() && isGreater(sorted.peek(), tmp, order)){
                input.push(sorted.pop());
                returns++;
                pushes++;
            }

            sorted.push(tmp);

            pushes++;

        }
        
        return sorted;
    }
    
    private boolean isGreater(T a, T b, Comparator<T> order) {
        comparisons++;
        return order.compare(a, b) > 0;
    }

    /**
     * @return as comparações feitas na última ordenação
     * @complexity O(1)
     */
    public long getComparisons() {
        return comparisons;
    }

    /**
     * @return os elementos devolvidos à entrada na última ordenação
     * @complexity O(1)
     */
    public long getReturns() {
        return returns;
    }

    /**
     * @return os push feitos nas duas pilhas na última ordenação
     * @complexity O(1)
     */
    public long getPushes() {
        return pushes;
    }

    
}
