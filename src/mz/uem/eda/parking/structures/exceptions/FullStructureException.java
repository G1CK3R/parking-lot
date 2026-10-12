package mz.uem.eda.parking.structures.exceptions;

/**
 * Thrown when an element is added to a stack or queue that has reached its
 * capacity: {@code push} or {@code enqueue} on a full structure. This is how
 * the service layer learns that a lane is full.
 */
public class FullStructureException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FullStructureException() {
        super("structure is full");
    }

    public FullStructureException(String message) {
        super(message);
    }
}