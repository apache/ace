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
package org.apache.ace.repository;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import org.testng.annotations.Test;

public class SortedRangeSetTest {
    @Test(groups = { UNIT })
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
        Range r4 = new Range(6,8);
        assert r4.getLow() == 6 : "Lowest value should be 6";
        assert r4.getHigh() == 8 : "Highest value should be 8";
        Range r5 = new Range(5);
        r5.setLow(8);
        assert r5.getHigh() == 8 : "Highest value should be 8";
        r5.setHigh(2);
        assert r5.getLow() == 2 : "Lowest value should be 2";
    }

    @Test(groups = { UNIT })
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
        assert new SortedRangeSet(new long[] {1,3,5,7,9}).diffDest(new SortedRangeSet("1-10")).toRepresentation().equals("2,4,6,8,10") : "Result of diff should be 2,4,6,8,10";
        assert new SortedRangeSet("1-5,8,12").diffDest(new SortedRangeSet("1-5,7,9,12,20")).toRepresentation().equals("7,9,20") : "Result of diff should be 7,9,20";
    }

    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public void invalidRange() {
        new SortedRangeSet("8-5");
    }
}
