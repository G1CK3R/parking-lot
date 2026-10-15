package mz.uem.eda.parking.algorithms;

import java.util.Objects;
import java.util.function.Predicate;

import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

/**
 * Busca sequencial numa pilha ou numa fila, sem a alterar. Usada para
 * encontrar um carro pela matrícula ou pelo número do talão numa baia ou na
 * fila de espera.
 *
 * <p>A busca usa o percurso só de leitura que {@link Stack} e {@link Queue}
 * expõem através de {@link Iterable}: a partir do topo de uma pilha, ou da
 * frente de uma fila. Nunca faz {@code pop} nem {@code dequeue}, por isso
 * procurar um carro não move nenhum carro.
 *
 * <p>O resultado é uma posição, não o elemento, porque é a posição que quem
 * chama precisa: numa pilha é a profundidade k do carro, o número de carros
 * por cima dele, e chegar até ele custa 2k movimentos pela baia de manobra;
 * numa fila é o número de elementos à frente dele.
 *
 * <p>O custo linear é assumido de propósito: com 200 lugares, o pior caso
 * são 200 comparações. Não usamos tabelas de dispoersão por restrição.
 */
public final class SequentialSearch {

    private SequentialSearch() {
        // só métodos estáticos
    }

    /**
     * Devolve a posição do primeiro elemento que satisfaz {@code match},
     * contada a partir do topo de uma pilha ou da frente de uma fila, que é
     * a posição 0. A estrutura fica inalterada.
     *
     * <p>Não fazer {@code push}, {@code pop}, {@code enqueue} nem
     * {@code dequeue} sobre {@code source} enquanto a busca corre, nem
     * dentro de {@code match}.
     *
     * @param <T> tipo dos elementos
     * @param source a pilha ou fila onde procurar, nunca {@code null}; pode
     *        estar vazia
     * @param match a condição que o elemento procurado satisfaz, nunca
     *        {@code null}
     * @return a posição do primeiro elemento que satisfaz a condição, ou -1
     *         se nenhum a satisfizer ou se {@code source} estiver vazia
     * @throws NullPointerException se {@code source} ou {@code match} for
     *         {@code null}
     * @complexity O(n) comparações no pior caso (elemento no fundo ou
     *             ausente), O(1) no melhor caso (elemento no topo); memória
     *             extra O(1)
     */
    public static <T> int indexOf(Iterable<T> source, Predicate<? super T> match) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(match, "match must not be null");
        int i = 0;
        for (T element : source) {
            if (match.test(element)) {
                return i;
            }
            i++;
        }
        return -1;
    }
}
