package net.luminis.liq.server.log.task;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import net.luminis.liq.log.LogDescriptor;
import net.luminis.liq.repository.RangeIterator;
import net.luminis.liq.repository.SortedRangeSet;

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
