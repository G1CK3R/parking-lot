package mz.uem.eda.parking.structures.exceptions;

/**
 * Thrown when an element is requested from a stack or queue that holds none:
 * {@code pop}, {@code dequeue} or {@code peek} on an empty structure.
 */
public class EmptyStructureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmptyStructureException() {
        super("structure is empty");
    }

    public EmptyStructureException(String message) {
        super(message);
    }
}