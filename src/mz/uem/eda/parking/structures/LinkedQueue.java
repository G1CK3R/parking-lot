package mz.uem.eda.parking.structures;

import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.NoSuchElementException;


import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

public class LinkedQueue<T> implements Queue<T> {
    private static class Node<T> {
        final T value;
        Node<T> next;
 
        Node(T value) {
            this.value = value;
        }
    }
 
    private Node<T> head;
    private Node<T> tail;
    private int size;

    @Override
    public void enqueue(T element) {
        if (element == null) {
            throw new NullPointerException("element must not be null");
        }
        Node<T> node = new Node<>(element);
        if (tail == null) {
            head = node;            // first element is both front and back
        } else {
            tail.next = node;
        }
        tail = node;
        size++;
    }

    @Override 
    public T dequeue() {
        if(isEmpty()) {
            throw new EmptyStackException();
        }
        T value = head.value;
        head =  head.next;
        if(head == null) {
            tail = null;
        }
        size--;
        return value;
    }

    @Override 
    public T peek(){
        if(isEmpty()) {
            throw new EmptyStructureException();
        }
        return head.value;
    }

    @Override 
    public boolean isEmpty(){
        return size == 0;
    }

    @Override 
    public int size() {
        return size;
    }

    @Override 
    public boolean isFull() {
        return false;
    }

    @Override
    public Iterator<T> iterator() {
            return new Iterator<T>() {
                private Node<T> current = head;
    
                @Override
                public boolean hasNext() {
                    return current != null;
                }
    
                @Override
                public T next() {
                    if (current == null) {
                        throw new NoSuchElementException();
                    }
                    T value = current.value;
                    current = current.next;
                    return value;
                }
            };
        }
}
