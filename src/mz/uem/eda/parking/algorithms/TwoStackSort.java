package mz.uem.eda.parking.algorithms;

import java.util.Comparator;
 
import mz.uem.eda.parking.structures.LinkedStack;
import mz.uem.eda.parking.structures.Stack;

public class TwoStackSort<T> {
    private long comparisons;  //Comparações feitas
    private long returns; // quantidade de elementos devolvidos à entrada 
    private long pushes; // total de push nas duas pilhas

    public Stack<T> sort(Stack<T> input, Comparator<T> order) {
        if(input == null || order == null){
            throw new IllegalArgumentException("input e order não podem ser null");
        }

        comparisons =  0;
        returns = 0;
        pushes = 0;

        Stack<T> sorted =  new LinkedStack<T>();

        while(!input.isEmpty()) {
            T tmp =  input.pop();

            while(!sorted.isEmpty() && isGreater(sorted.peek(), tmp, order)){
                input.push(sorted.pop());
                returns++;
                pushes++;
            }

            sorted.push(tmp);

            pushes++;

        }
        
        return sorted;
    }
    
    private boolean isGreater(T a, T b, Comparator<T> order) {
        comparisons++;
        return order.compare(a, b) > 0;
    }

    public long getComparisons() {
        return comparisons;
    }

    public long getReturns() {
        return returns;
    }

    public long getPushes() {
        return pushes;
    }

    
}
