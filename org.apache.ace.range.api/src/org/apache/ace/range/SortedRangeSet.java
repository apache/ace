/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.StringTokenizer;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Collection that stores a sorted set of ranges and is able to represent them
 * as a string.
 */
@ProviderType
public class SortedRangeSet {
    /**
     * A static set which contains all possible values.
     */
    public final static SortedRangeSet FULL_SET = new SortedRangeSet(0 + "-" + Long.MAX_VALUE) {
        public boolean contains(long number) {
            return true;
        }
    };

    private List<Range> m_ranges = new ArrayList<>();

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
     * Creates a new instance from an array of longs. The array can contain the longs in random order,
     * and duplicates will be filtered out.
     *
     * @param items Array of longs
     */
    public SortedRangeSet(long[] items) {
        Arrays.sort(items);
        Range r = null;
        for (int i = 0; i < items.length; i++) {
            if (r == null) {
                r = new Range(items[i]);
            }
            else {
                if (items[i] == r.getHigh()) {
                    // ignore this duplicate
                }
                else if (items[i] == r.getHigh() + 1) {
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
        Iterator<Range> i = m_ranges.iterator();
        while (i.hasNext()) {
            Range r = i.next();
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
     * 
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

    /**
     * Checks if a number falls within any range in this set.
     *
     * @param number the number to check
     * @return <code>true</code> if the number was inside any range in this set
     */
    public boolean contains(long number) {
        Iterator<Range> i = m_ranges.iterator();
        while (i.hasNext()) {
            Range r = i.next();
            if (r.contains(number)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a number to the set of ranges. Tries to be as smart as possible about it.
     *
     * @param number the number to add
     */
    private void add(long number) {
        ListIterator<Range> i = m_ranges.listIterator();
        while (i.hasNext()) {
            int index = i.nextIndex();
            Range r = i.next();
            if (r.contains(number)) {
                return;
            }
            long low = r.getLow();
            long high = r.getHigh();
            if (number < low) {
                if (number == (low - 1)) {
                    r.setLow(number);
                    return;
                }
                else {
                    Range nr = new Range(number);
                    m_ranges.add(index, nr);
                    return;
                }
            }
            if (number == (high + 1)) {
                r.setHigh(number);
                if (i.hasNext()) {
                    Range nr = (Range) i.next();
                    if (number == nr.getLow() - 1) {
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

    /**
     * Returns an iterator that iterates over all the ranges in this set.
     *
     * @return a range iterator
     */
    public RangeIterator iterator() {
        return new RangeIterator(m_ranges.listIterator(), false);
    }
    
    /**
     * Returns an iterator that iterates over all the ranges in this set in reverse order.
     * 
     * @return a range iterator
     */
    public RangeIterator reverseIterator() {
        return new RangeIterator(m_ranges.listIterator(m_ranges.size()), true);
    }
    
    /**
     * Returns an iterator that iterates over all the <code>Range</code> instances in this set.
     * 
     * @return an iterator of <code>Range</code> objects
     */
    public Iterator rangeIterator() {
        return m_ranges.iterator();
    }

    /**
     * Returns the highest value present in any of the ranges in this <code>SortredRangeSet</code>.
     *
     * @return the highest value present in any of the ranges in this <code>SortredRangeSet</code>
     *     or <code>0</code> if the <code>SortedRangeSet</code> is empty.
     */
    public long getHigh() {
        int size = m_ranges.size();
        if (size > 0) {
            Range range = m_ranges.get(size - 1);
            return range.getHigh();
        }
        else {
            return 0;
        }
    }

    /**
     * Returns the union of this set and the provided set.
     * 
     * @param dest a set to union with ourselves
     * @return the resulting set
     */
    public SortedRangeSet union(SortedRangeSet dest) {
        SortedRangeSet result = new SortedRangeSet();
        RangeIterator i = dest.iterator();
        while (i.hasNext()) {
            long number = i.next();
            result.add(number);
        }
        i = iterator();
        while (i.hasNext()) {
            long number = i.next();
            result.add(number);
        }
        return result;
    }
    
    @Override
    public String toString() {
        return "SortedRangeSet[" + toRepresentation() + "]";
    }
}