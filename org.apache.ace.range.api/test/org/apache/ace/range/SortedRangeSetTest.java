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

import java.util.Iterator;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SortedRangeSetTest {
    @Test()
    public void manipulateSimpleRanges() {
        Range r1 = new Range("5");
        assert r1.getLow() == 5 : "Lowest value should be 5";
        assert r1.getHigh() == 5 : "Highest value should be 5";
        assert r1.contains(5) : "Range should contain 5";
        assert !r1.contains(4) : "Range should not contain 4";
        assert !r1.contains(6) : "Range should not contain 6";
        assert "5".equals(r1.toRepresentation()) : "Representation should be 5";
        Range r2 = new Range("2-6");
        assert r2.getLow() == 2 : "Lowest value should be 2";
        assert r2.getHigh() == 6 : "Highest value should be 6";
        assert r2.contains(6) : "Range should contain 6";
        assert !r2.contains(7) : "Range should not contain 7";
        assert !r2.contains(0) : "Range should not contain 0";
        assert "2-6".equals(r2.toRepresentation()) : "Representation should be 2-6";
        Range r3 = new Range(5);
        assert r3.getLow() == 5 : "Lowest value should be 5";
        assert r3.getHigh() == 5 : "Highest value should be 5";
        Range r4 = new Range(6, 8);
        assert r4.getLow() == 6 : "Lowest value should be 6";
        assert r4.getHigh() == 8 : "Highest value should be 8";
        Range r5 = new Range(5);
        r5.setLow(8);
        assert r5.getHigh() == 8 : "Highest value should be 8";
        r5.setHigh(2);
        assert r5.getLow() == 2 : "Lowest value should be 2";
    }

    @Test()
    public void manipulateSortedRangeSets() {
        SortedRangeSet s1 = new SortedRangeSet("1,3,5-8");
        RangeIterator ri1 = s1.iterator();
        assert ri1.next() == 1 : "Illegal value in range iterator";
        assert ri1.next() == 3 : "Illegal value in range iterator";
        assert ri1.next() == 5 : "Illegal value in range iterator";
        assert ri1.next() == 6 : "Illegal value in range iterator";
        assert ri1.next() == 7 : "Illegal value in range iterator";
        assert ri1.next() == 8 : "Illegal value in range iterator";
        assert !ri1.hasNext() : "There should not be more values in the iterator";
        assert new SortedRangeSet("1-20").diffDest(new SortedRangeSet("5-25")).toRepresentation().equals("21-25") : "Result of diff should be 21-25";
        assert new SortedRangeSet(new long[] { 1, 3, 5, 7, 9 }).diffDest(new SortedRangeSet("1-10")).toRepresentation().equals("2,4,6,8,10") : "Result of diff should be 2,4,6,8,10";
        assert new SortedRangeSet("1-5,8,12").diffDest(new SortedRangeSet("1-5,7,9,12,20")).toRepresentation().equals("7,9,20") : "Result of diff should be 7,9,20";
        assert new SortedRangeSet("1").union(new SortedRangeSet("2")).toRepresentation().equals("1-2") : "Result of union should be 1-2";
        assert new SortedRangeSet("1-4").union(new SortedRangeSet("6-9")).toRepresentation().equals("1-4,6-9") : "Result of union should be 1-4,6-9";
        Assert.assertEquals(new SortedRangeSet("1-3").union(new SortedRangeSet("4")).toRepresentation(), "1-4", "Result of union failed.");
        Assert.assertEquals(new SortedRangeSet("5-10").union(new SortedRangeSet("4")).toRepresentation(), "4-10", "Result of union failed.");
        Assert.assertEquals(new SortedRangeSet("1-3,5-10").union(new SortedRangeSet("4")).toRepresentation(), "1-10", "Result of union failed.");
        Assert.assertEquals(new SortedRangeSet("1-5,8,12").union(new SortedRangeSet("4-8,9-11")).toRepresentation(), "1-12", "Result of union failed.");
    }

    @Test()
    public void validateRangeIterators() {
        SortedRangeSet srs1 = new SortedRangeSet("1-10");
        Iterator i1 = srs1.rangeIterator();
        assert i1.hasNext() : "We should have one Range instance in our iterator.";
        assert ((Range) i1.next()).toRepresentation().equals("1-10");
        assert !i1.hasNext() : "There should be only one instance in our iterator.";
        SortedRangeSet srs2 = new SortedRangeSet("1-5,8,10-15");
        Iterator i2 = srs2.rangeIterator();
        assert i2.hasNext() && i2.next() instanceof Range
            && i2.hasNext() && i2.next() instanceof Range
            && i2.hasNext() && i2.next() instanceof Range
            && !i2.hasNext() : "There should be exactly three Range instances in our iterator.";
        SortedRangeSet srs3 = new SortedRangeSet("");
        assert !srs3.iterator().hasNext() : "Iterator should be empty.";
    }

    @Test()
    public void validateReverseRangeIterators() throws Exception {
        SortedRangeSet srs1 = new SortedRangeSet("1-10");
        RangeIterator i1 = srs1.reverseIterator();
        for (long i = 10; i > 0; i--) {
            Assert.assertEquals(i1.next(), i);
        }
        Assert.assertFalse(i1.hasNext(), "We should have iterated over all elements of our simple range.");
        SortedRangeSet srs2 = new SortedRangeSet("1-5,8,10-15");
        RangeIterator i2 = srs2.reverseIterator();
        long[] i2s = { 15, 14, 13, 12, 11, 10, 8, 5, 4, 3, 2, 1 };
        for (int i = 0; i < i2s.length; i++) {
            Assert.assertEquals(i2.next(), i2s[i]);
        }
        Assert.assertFalse(i2.hasNext(), "We should have iterated over all elements of our complex range.");

        SortedRangeSet srs3 = new SortedRangeSet("");
        assert !srs3.reverseIterator().hasNext() : "Iterator should be empty.";
    }

    @Test()
    public void validateSortedRangeSetArrayConstructor() throws Exception {
        long[] a1 = new long[] { 1, 2, 3, 5, 10 };
        SortedRangeSet s1 = new SortedRangeSet(a1);
        RangeIterator i1 = s1.iterator();
        for (int i = 0; i < a1.length; i++) {
            Assert.assertEquals(i1.next(), a1[i]);
        }
        Assert.assertEquals(s1.toRepresentation(), "1-3,5,10");
        Assert.assertFalse(i1.hasNext(), "We should have iterated over all elements of our range.");

        long[] a2 = new long[] { 10, 2, 3, 5, 10, 2, 1, 5, 1 };
        long[] a2s = new long[] { 1, 2, 3, 5, 10 };
        SortedRangeSet s2 = new SortedRangeSet(a2);
        RangeIterator i2 = s2.iterator();
        for (int i = 0; i < a2s.length; i++) {
            Assert.assertEquals(i2.next(), a2s[i]);
        }
        Assert.assertEquals(s2.toRepresentation(), "1-3,5,10");
        Assert.assertFalse(i2.hasNext(), "We should have iterated over all elements of our range.");
        Assert.assertEquals((new SortedRangeSet(new long[] {})).toRepresentation(), (new SortedRangeSet(new long[] {})).toRepresentation());
        Assert.assertEquals((new SortedRangeSet(new long[] { 1 })).toRepresentation(), (new SortedRangeSet(new long[] { 1, 1, 1 })).toRepresentation());
        Assert.assertEquals((new SortedRangeSet(new long[] { 3, 2, 1 })).toRepresentation(), (new SortedRangeSet(new long[] { 1, 2, 3, 2, 1 })).toRepresentation());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidRange() {
        new SortedRangeSet("8-5");
    }
}
