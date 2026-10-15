package mz.uem.eda.parking.algorithms;

import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Radix sort com dez filas, uma por algarismo decimal.
 *
 * <p>Cada volta olha para um algarismo, das unidades para cima: põe cada
 * elemento na fila do seu algarismo e depois esvazia as filas, da 0 à 9, de
 * volta para a fila de entrada. Como a fila mantém a ordem de chegada, os
 * empates conservam a ordem da volta anterior; é isso que torna a ordenação
 * correcta, e também estável.
 *
 * <p>Uso: {@code QueueRadixSort.sort(numeros, new IntegerKey());}
 */
public final class QueueRadixSort {

    private QueueRadixSort() {

    }
    /**
     * Ordena a fila por ordem crescente de chave, no próprio lugar e de forma
     * estável (elementos com a mesma chave mantêm a ordem em que estavam).
     *
     * @param queue a fila a ordenar
     * @param key   dá o número de cada elemento; nunca negativo
     * @throws IllegalArgumentException se alguma chave for negativa (a fila
     *                                  fica intacta)
     * @complexity O(d · n), d = algarismos da maior chave
     */

    public static <T> void  sort(Queue<T> queue, SortKey<T> key) {
        int max = 0;
        for(T element : queue){
            int k = key.of(element);
            if(k<0) {
                throw new IllegalArgumentException();
            }

            max = Math.max(max, k);
        }

        @SuppressWarnings("unchecked") // o Java não cria vectores genéricos

        Queue<T>[] buckets = new Queue[10];
        for(int d = 0; d < 10; d++) {
            buckets[d] = new LinkedQueue<>();
        }

        for(long exp = 1; max/exp > 0; exp*=10) {
            while (!queue.isEmpty()) {
                T element = queue.dequeue();
                int digit = (int) (key.of(element) / exp % 10);
                buckets[digit].enqueue(element);
            }

            for(Queue<T> bucket : buckets) { //Recolher da 0 à 9
                while(!bucket.isEmpty()){
                    queue.enqueue(bucket.dequeue());
                }
            }
        }
    }
}
