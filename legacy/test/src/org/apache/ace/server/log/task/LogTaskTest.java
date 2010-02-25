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
package org.apache.ace.server.log.task;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.ace.log.LogDescriptor;
import org.apache.ace.repository.RangeIterator;
import org.apache.ace.repository.SortedRangeSet;
import org.testng.annotations.Test;

public class LogTaskTest {

    private class MockLogSyncTask extends LogSyncTask {
        public List<LogDescriptor> m_calledWith = new ArrayList<LogDescriptor>();

        public MockLogSyncTask(String endpoint, String name) {
            super(endpoint, name);
        }

        @Override
        protected void writeLogDescriptor(LogDescriptor descriptor, Writer writer) throws IOException {
            m_calledWith.add(descriptor);
        }

    }

    /**
     * This test tests both delta computation and push behavior.
     */
    @Test(groups = { UNIT })
    public void testDeltaComputation() throws IOException {
        // TODO: Test the new LogDescriptor.
        List<LogDescriptor> src = new ArrayList<LogDescriptor>();
        List<LogDescriptor> dest = new ArrayList<LogDescriptor>();
        MockLogSyncTask task = new MockLogSyncTask("mocklog", "mocklog");
        // compare two empty lists
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.isEmpty() : "Delta of two empty lists should be empty";
        // add something to the source
        src.add(new LogDescriptor("gwid", 1, new SortedRangeSet("1-5")));
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.size() == 1 : "Delta should be 1 instead of: " + task.m_calledWith.size();
        task.m_calledWith.clear();
        // add an overlapping destination
        dest.add(new LogDescriptor("gwid", 1, new SortedRangeSet("1-3")));
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.size() == 1 : "Delta should be 1 instead of: " + task.m_calledWith.size();
        RangeIterator i = task.m_calledWith.get(0).getRangeSet().iterator();
        assert i.next() == 4 : "Illegal value in SortedRangeSet";
        assert i.next() == 5 : "Illegal value in SortedRangeSet";
        assert !i.hasNext() : "Illegal value in SortedRangeSet";
        task.m_calledWith.clear();
        // add a non-overlapping destination
        dest.add(new LogDescriptor("gwid", 2, new SortedRangeSet("50-100")));
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.size() == 1 : "Delta should be 1 instead of: " + task.m_calledWith.size();
        i = task.m_calledWith.get(0).getRangeSet().iterator();
        assert i.next() == 4 : "Illegal value in SortedRangeSet";
        assert i.next() == 5 : "Illegal value in SortedRangeSet";
        assert !i.hasNext() : "Illegal value in SortedRangeSet";
        task.m_calledWith.clear();
        // add non-overlapping source
        src.add(new LogDescriptor("gwid", 2, new SortedRangeSet("1-49")));
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.size() == 2 : "Delta should be 2 instead of: " + task.m_calledWith.size();
        task.m_calledWith.clear();
        // add a source with gaps
        src.add(new LogDescriptor("gwid", 3, new SortedRangeSet("1-10")));
        dest.add(new LogDescriptor("gwid", 3, new SortedRangeSet("3,5-8")));
        task.writeDelta(task.calculateDelta(src, dest), null);
        assert task.m_calledWith.size() == 3 : "Delta should be 3 instead of: " + task.m_calledWith.size();
        for (LogDescriptor l : task.m_calledWith) {
            if (l.getLogID() == 3) {
                i = l.getRangeSet().iterator();
            }
        }
        assert i.next() == 1 : "Illegal value in SortedRangeSet";
        assert i.next() == 2 : "Illegal value in SortedRangeSet";
        assert i.next() == 4 : "Illegal value in SortedRangeSet";
        assert i.next() == 9 : "Illegal value in SortedRangeSet";
        assert i.next() == 10 : "Illegal value in SortedRangeSet";
        assert !i.hasNext() : "Illegal value in SortedRangeSet";
    }
}
