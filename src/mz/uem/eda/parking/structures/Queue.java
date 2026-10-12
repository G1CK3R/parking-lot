package mz.uem.eda.parking.structures;

public interface Queue<T> extends Iterable<T> {
    void enqueue(T element); //Let's use FullSructureException iffull
    T dequeue(); //EmptyStructureException ifEmpty
    T peek();  //EmptyStructureException ifEmpty
    boolean isEmpty();
    int size();
    boolean isFull();
    
}
