package net.luminis.liq.repository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

/**
 * Collection that stores a sorted set of ranges and is able to represent them
 * as a string.
 */
public class SortedRangeSet {
    /**
     * A static set which contains all possible values.
     */
    public final static SortedRangeSet FULL_SET = new SortedRangeSet(0 + "-" + Long.MAX_VALUE) {
        public boolean contains(long number) {
            return true;
        }
    };
    
    private List m_ranges = new ArrayList();
    
    /**
     * Creates a new instance from a string representation.
     * 
     * @param representation The string representation of a <code>SortedRangeSet</code>.
     * @throws NumberFormatException If the string representation does not contain a valid <code>SortedRangeSet</code>.
     */
    public SortedRangeSet(String representation) {
        StringTokenizer st = new StringTokenizer(representation, ",");
        while (st.hasMoreTokens()) {
            m_ranges.add(new Range(st.nextToken()));
        }
    }
    
    /**
     * Creates a new instance from an array of longs.
     * 
     * @param items Array of longs
     */
    public SortedRangeSet(long[] items) {
        // TODO: deal with items not being in ascending order
        Range r = null;
        for (int i = 0; i < items.length; i++) {
            if (r == null) {
                r = new Range(items[i]);
            }
            else {
                if (items[i] == r.getHigh() + 1) {
                    r.setHigh(items[i]);
                }
                else {
                    m_ranges.add(r);
                    r = new Range(items[i]);
                }
            }
        }
        if (r != null) {
            m_ranges.add(r);
        }
    }
    
    private SortedRangeSet() {
    }
    
    /**
     * Retrieve a string representation of the <code>SortedRangeSet</code>.
     * 
     * @return A string representation of the <code>SortedRangeSet</code>.
     */
    public String toRepresentation() {
        StringBuffer result = new StringBuffer();
        Iterator i = m_ranges.iterator();
        while (i.hasNext()) {
            Range r = (Range) i.next();
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(r.toRepresentation());
        }
        return result.toString();
    }
    
    /**
     * Creates the difference between this set and <code>dest</code>, by (in set notation)<br>
     * <code>result = dest \ this</code>,<br>
     * that is, if <code>dest = {1, 2}</code> and <code>this = {2, 3}</code>, then
     * <code>result = {1, 2} \ {2, 3} = {1}</code>
     * @param dest The set from which this set should be 'set-minussed'.
     * @return The resulting set after the diff.
     */
    public SortedRangeSet diffDest(SortedRangeSet dest) {
        SortedRangeSet result = new SortedRangeSet();
        RangeIterator i = dest.iterator();
        while (i.hasNext()) {
            long number = i.next();
            if (!contains(number)) {
                result.add(number);
            }
        }
        return result;
    }
    
    public boolean contains(long number) {
        Iterator i = m_ranges.iterator();
        while (i.hasNext()) {
            Range r = (Range) i.next();
            if (r.contains(number)) {
                return true;
            }
        }
        return false;
    }
    
    private void add(long number) {
        ListIterator i = m_ranges.listIterator();
        while (i.hasNext()) {
            int index = i.nextIndex();
            Range r = (Range) i.next();
            if (r.contains(number)) {
                return;
            }
            long low = r.getLow();
            long high = r.getHigh();
            if (number < low) {
                if (number == low - 1) {
                    r.setLow(number);
                    return;
                }
                else {
                    Range nr = new Range(number);
                    m_ranges.add(index, nr);
                    return;
                }
            }
            if (number == high + 1) {
                r.setHigh(number);
                if (i.hasNext()) {
                    Range nr = (Range) i.next();
                    if (number == low - 1) {
                        r.setHigh(nr.getHigh());
                        i.remove();
                    }
                }
                return;
            }
        }
        Range nr = new Range(number);
        m_ranges.add(nr);
    }
    
    public RangeIterator iterator() {
        return new RangeIterator(m_ranges.iterator());
    }
    
    /**
     * Returns the highest value present in any of the ranges in this <code>SortredRangeSet</code>.
     * 
     * @return The highest value present in any of the ranges in this <code>SortredRangeSet</code> or <code>0</code> if the <code>SortedRangeSet</code> is empty.
     */
    public long getHigh() {
        int size = m_ranges.size();
        if (size > 0) {
            Range range = (Range) m_ranges.get(size - 1);
            return range.getHigh();
        }
        else {
            return 0;
        }
    }
}
