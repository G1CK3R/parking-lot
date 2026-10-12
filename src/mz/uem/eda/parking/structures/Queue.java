package mz.uem.eda.parking.structures;

public interface Queue<T> extends Iterable<T> {
    void enqueue(T element); //Aqui vamos usar FullStructureException
    T dequeue(); //EmptyStructureException se vazia
    T peek();  //EmptyStructureException se vazia
    boolean isEmpty();
    int size();
    boolean isFull();
    
}
