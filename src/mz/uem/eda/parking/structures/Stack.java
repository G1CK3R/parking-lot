package mz.uem.eda.parking.structures;

public interface Stack<T> extends Iterable<T> {
    void push(T element);
    T pop();
    T peek();
    boolean isEmpty();
    int size();
    boolean isFull();
}
