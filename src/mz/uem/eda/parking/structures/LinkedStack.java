package mz.uem.eda.parking.structures;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

import java.util.Iterator;
import java.util.NoSuchElementException;


public class LinkedStack<T> implements Stack<T> {
    private static class Node<T> {
        
        final T value;
        final Node<T> next;
        Node(T value, Node<T> next){
            this.value = value;
            this.next = next;
        }       
    }

    private Node<T> top;
    private int size;

    @Override 
    public void push(T element) {
        top = new Node<>(element, top);
        size++;
    }

    @Override 
    public T pop() {
        if(isEmpty()){
            throw new EmptyStructureException();
        }
        T value = top.value;
        top = top.next;
        size--;
        return value;
    }

    @Override 
    public T peek(){
        if(isEmpty()){
            throw new EmptyStructureException();
        }
        return top.value;
    }

        
    @Override 
    public boolean isFull(){
        return false;
    }

    @Override 
    public int size(){
        return size;
    }

    @Override 
    public boolean isEmpty(){
        return size ==0;
    }

    @Override 
    public Iterator<T> iterator() {
        return new Iterator<T>(){
            
            private Node<T> current =  top;
            
            @Override 
            public boolean hasNext() {
                return current != null;
            } 

            @Override 
            public T next(){
                if (current == null) {
                    if (current == null){
                        throw new NoSuchElementException();
                    }
                }
                T value = current.value;
                current = current.next;
                return value;
            }
        };
    }
}
