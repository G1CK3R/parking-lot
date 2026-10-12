package mz.uem.eda.parking.structures;

import java.util.NoSuchElementException;

import mz.uem.eda.parking.structures.exceptions.EmptyStructureException;

import java.util.Iterator;


public class ArrayStack<T> implements Stack<T> {
    private final T[] elements;
    private int size;

    @SuppressWarnings("unchecked")
    public ArrayStack(int capacity){
        if(capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1: "+ capacity);
        }
        this.elements = (T[]) new Object[capacity];
    }

    @Override 
    public void push(T element){
        if( element == null) {
            throw new NullPointerException("Element must not be null");
        }
        if(isFull()) {
            throw new EmptyStructureException();    
        }
        elements[size++] = element;
    }

    @Override
    public T peek() {
        if (isEmpty()) {
            throw new EmptyStructureException();
        }
        return elements[size - 1];
    }
    @Override 
    public T pop(){
        if(isEmpty()){
            throw new EmptyStructureException();
        }    
        T top  =  elements[--size];
        elements[size] = null;
        return top;
    }

     @Override 
    public boolean isEmpty(){
        return size==0;
    }

    @Override 
    public boolean isFull() {
        return size == elements.length;
    }

    @Override 
    public int size(){
        return this.size;
    }

    @Override 
    public Iterator<T> iterator() {
        return new Iterator<T>(){
            private int next = size -1;

            @Override 
            public boolean hasNext(){
                return next>=0;
            }

            @Override 
            public T next() {
                if(!hasNext()) {
                    throw new NoSuchElementException();
                }

                return elements[next--];
            }
        };
    }

   

}
