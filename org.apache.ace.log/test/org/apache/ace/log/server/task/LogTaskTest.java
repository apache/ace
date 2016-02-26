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
package org.apache.ace.log.server.task;

import static org.testng.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.log.server.task.LogSyncTask;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.testng.annotations.Test;

public class LogTaskTest {

    private static class MockLogSyncTask extends LogSyncTask {
        public List<Descriptor> m_calledWith = new ArrayList<>();

        public MockLogSyncTask(String endpoint, String name) {
            super(endpoint, name, LogSyncTask.Mode.PUSH, LogSyncTask.Mode.NONE);
        }

        public void clear() {
            m_calledWith.clear();
        }

        public int size() {
            return m_calledWith.size();
        }

        @Override
        protected void writeDescriptor(Descriptor descriptor, Writer writer) throws IOException {
            m_calledWith.add(descriptor);
        }
    }

    /**
     * This test tests both delta computation and push behavior.
     */
    @Test()
    public void testDeltaComputation() throws IOException {
        // TODO: Test the new LogDescriptor.
        List<Descriptor> src = new ArrayList<>();
        List<Descriptor> dest = new ArrayList<>();

        String targetID = "targetID";
        MockLogSyncTask task = new MockLogSyncTask("mocklog", "mocklog");
        Writer mockWriter = new StringWriter();

        // compare two empty lists
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertTrue(task.m_calledWith.isEmpty(), "Delta of two empty lists should be empty");

        // add something to the source
        src.add(new Descriptor(targetID, 1, new SortedRangeSet("1-5")));
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertEquals(task.size(), 1, "Incorrect delta");

        task.clear();

        // add an overlapping destination
        dest.add(new Descriptor(targetID, 1, new SortedRangeSet("1-3")));
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertEquals(task.size(), 1, "Incorrect delta");

        RangeIterator i = task.m_calledWith.get(0).getRangeSet().iterator();
        assertEquals(i.next(), 4, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 5, "Illegal value in SortedRangeSet");
        assertFalse(i.hasNext(), "Illegal value in SortedRangeSet");

        task.clear();

        // add a non-overlapping destination
        dest.add(new Descriptor(targetID, 2, new SortedRangeSet("50-100")));
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertEquals(task.size(), 1, "Incorrect delta");

        i = task.m_calledWith.get(0).getRangeSet().iterator();
        assertEquals(i.next(), 4, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 5, "Illegal value in SortedRangeSet");
        assertFalse(i.hasNext(), "Illegal value in SortedRangeSet");

        task.clear();

        // add non-overlapping source
        src.add(new Descriptor(targetID, 2, new SortedRangeSet("1-49")));
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertEquals(task.size(), 2, "Incorrect delta");

        task.clear();

        // add a source with gaps
        src.add(new Descriptor(targetID, 3, new SortedRangeSet("1-10")));
        dest.add(new Descriptor(targetID, 3, new SortedRangeSet("3,5-8")));
        task.writeDelta(task.calculateDelta(src, dest), mockWriter);

        assertEquals(task.size(), 3, "Incorrect delta");
        for (Descriptor l : task.m_calledWith) {
            if (l.getStoreID() == 3) {
                i = l.getRangeSet().iterator();
            }
        }
        assertEquals(i.next(), 1, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 2, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 4, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 9, "Illegal value in SortedRangeSet");
        assertEquals(i.next(), 10, "Illegal value in SortedRangeSet");
        assertFalse(i.hasNext(), "Illegal value in SortedRangeSet");
    }
}
