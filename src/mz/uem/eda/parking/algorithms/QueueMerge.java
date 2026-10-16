package mz.uem.eda.parking.algorithms;

import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Fusão de duas filas já ordenadas numa só fila ordenada.
 *
 * <p>Enquanto as duas filas têm elementos, compara as duas frentes e passa
 * a menor para o resultado. Quando uma fica vazia, o que sobra da outra já
 * está ordenado e passa todo, sem comparar.
 *
 * <p>Uso: {@code Queue<Integer> juntos = QueueMerge.merge(a, b, new IntegerKey());}
 */

public final class QueueMerge {

    private QueueMerge(){}

     /**
     * Junta duas filas ordenadas por ordem crescente de chave. Em caso de
     * empate sai primeiro o elemento de {@code first}, por isso a fusão é
     * estável.
     *
     * <p>As duas filas de entrada ficam vazias: os elementos passam para o
     * resultado, não são copiados.
     *
     * <p>As filas têm de estar ordenadas pela mesma chave; isso não é
     * verificado, e se não estiverem o resultado sai fora de ordem.
     *
     * @param first  primeira fila ordenada; ganha os empates
     * @param second segunda fila ordenada
     * @param key    dá o número de cada elemento
     * @return uma fila nova com todos os elementos, ordenada
     * @complexity O(n + m), n e m = tamanhos das duas filas
     */

    public static <T> Queue<T> merge(Queue<T> first, Queue<T> second, SortKey<T> key){
        Queue<T> result = new LinkedQueue<>();


        while(!first.isEmpty() && !second.isEmpty()) {
            if (key.of(first.peek()) <= key.of(second.peek())) {
                result.enqueue(first.dequeue());
            } else {
                result.enqueue(second.dequeue());
            }
        }


        //Só uma das stacks tem elementos, ou nenhuma: passa o resto, já ordenado.
        while(!first.isEmpty()) {
            result.enqueue(first.dequeue());
        }
        while (!second.isEmpty()) {
            result.enqueue(second.dequeue());
        }
        return result;
    }
}
