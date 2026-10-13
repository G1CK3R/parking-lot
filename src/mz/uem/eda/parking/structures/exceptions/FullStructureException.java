package mz.uem.eda.parking.structures.exceptions;

/**
 * Lançada quando se acrescenta um elemento a uma pilha ou fila que já
 * atingiu a capacidade: {@code push} ou {@code enqueue} numa estrutura
 * cheia. É assim que a camada de serviço fica a saber que uma baia está
 * cheia.
 */
public class FullStructureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Cria a excepção com a mensagem por omissão. */
    public FullStructureException() {
        super("structure is full");
    }

    /**
     * Cria a excepção com uma mensagem própria.
     *
     * @param message a mensagem de detalhe
     */
    public FullStructureException(String message) {
        super(message);
    }
}
