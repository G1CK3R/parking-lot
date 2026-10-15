package mz.uem.eda.parking.algorithms;

public class IntegerKey implements SortKey<Integer> {
    @Override 
    public int of(Integer element) {
        return element;
    }
}
