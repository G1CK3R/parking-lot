package mz.uem.eda.parking.structures.exceptions;

/**
 * Lançada quando se pede um elemento a uma pilha ou fila que não tem
 * nenhum: {@code pop}, {@code dequeue} ou {@code peek} numa estrutura vazia.
 */
public class EmptyStructureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Cria a excepção com a mensagem por omissão. */
    public EmptyStructureException() {
        super("structure is empty");
    }

    /**
     * Cria a excepção com uma mensagem própria.
     *
     * @param message a mensagem de detalhe
     */
    public EmptyStructureException(String message) {
        super(message);
    }
}
