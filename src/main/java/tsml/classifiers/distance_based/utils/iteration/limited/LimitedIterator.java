package tsml.classifiers.distance_based.utils.iteration.limited;

import tsml.classifiers.distance_based.utils.collections.DefaultIterator;
import weka.core.OptionHandler;

import java.util.Enumeration;
import java.util.Iterator;

/**
 * Purpose: traverse a list up to a point.
 *
 * Contributors: goastler
 *
 * @param <A>
 */
public class LimitedIterator<A>
    implements DefaultIterator<A>,
               OptionHandler {

    public LimitedIterator() {}

    public LimitedIterator(Iterator<A> iterator) {
        setIterator(iterator);
    }

    public LimitedIterator(Iterator<A> iterator, int limit) {
        setIterator(iterator);
        setLimit(limit);
    }

    public LimitedIterator(int limit, Iterator<A> iterator) {
        this(iterator, limit);
    }

    public LimitedIterator(int limit) {
        setLimit(limit);
    }

    protected int limit = -1;

    public int getLimit() {
        return limit;
    }

    public void setLimit(final int limit) {
        this.limit = limit;
    }

    protected int count = 0;
    protected Iterator<A> iterator;

    public Iterator<A> getIterator() {
        return iterator;
    }

    public void setIterator(final Iterator<A> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return (count < limit || limit < 0) && iterator.hasNext();
    }

    @Override
    public A next() {
        count++;
        return iterator.next();
    }

    public void resetCount() {
        count = 0;
    }

    @Override
    public void remove() {
        iterator.remove();
    }

    @Override
    public Enumeration listOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOptions(final String[] options) throws
                                                   Exception { // todo

    }

    @Override
    public String[] getOptions() {
        return new String[] {
            "-l",
            String.valueOf(limit)
        }; // todo
    }

    // todo pass through other iterator funcs
}
