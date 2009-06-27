package net.luminis.liq.gateway.log.task;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.gateway.log.store.LogStore;
import net.luminis.liq.identification.Identification;
import net.luminis.liq.log.LogDescriptor;
import net.luminis.liq.log.LogEvent;
import net.luminis.liq.repository.SortedRangeSet;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LogSyncTaskTest {

    private LogSyncTask m_task;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_task = new LogSyncTask("testlog");
        TestUtils.configureObject(m_task, LogService.class);
        TestUtils.configureObject(m_task, Identification.class);
        TestUtils.configureObject(m_task, Discovery.class);
        TestUtils.configureObject(m_task, LogStore.class);
    }

    @Test(groups = { UNIT })
    public synchronized void getRange() throws Exception {
        final LogDescriptor range = new LogDescriptor("gwID", 1, new SortedRangeSet("1-10"));
        m_task.getDescriptor(new InputStream() {
            int m_count = 0;
            byte[] m_bytes = (range.toRepresentation() + "\n").getBytes();
            @Override
            public int read() throws IOException {
                if (m_count < m_bytes.length) {
                    byte b = m_bytes[m_count];
                    m_count++;
                    return b;
                } else {
                    return -1;
                }
            }
        });
    }

    @Test(groups = { UNIT })
    public synchronized void synchronizeLog() throws Exception {
        final LogDescriptor range = new LogDescriptor("gwID", 1, new SortedRangeSet(new long[] {0}));
        final LogEvent event = new LogEvent("gwID", 1, 1, 1, 1, new Properties());
        final List<LogEvent> events = new ArrayList<LogEvent>();
        events.add(event);

        InputStream input = new InputStream() {
            byte[] bytes = range.toRepresentation().getBytes();
            int count = 0;
            @Override
            public int read() throws IOException {
                if (count < bytes.length) {
                    byte b = bytes[count];
                    count++;
                    return b;
                } else {
                    return -1;
                }
            }
        };
        TestUtils.configureObject(m_task, LogStore.class, new LogStore() {
            public List<?> get(long logID, long from, long to) throws IOException {
                return events;
            }
            public long getHighestID(long logID) throws IOException {
                return event.getID();
            }
            public List<?> get(long logID) throws IOException { return null; }
            public long[] getLogIDs() throws IOException { return null; }
            @SuppressWarnings("unchecked")
            public LogEvent put(int type, Dictionary props) throws IOException { return null; }
        });
        MockConnection connection = new MockConnection(new URL("http://mock"));
        m_task.synchronizeLog(1, input, connection);
        String expectedString = event.toRepresentation() + "\n";
        String actualString = connection.getString();

        assert actualString.equals(expectedString) : "We expected " + expectedString + " but received " + actualString;
    }

    private class MockConnection extends Connection {

        private MockOutputStream m_output;

        public MockConnection(URL url) throws IOException {
            super(url);
            m_output = new MockOutputStream();
        }

        public String getString() {
            return m_output.getString();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return m_output;
        }


    }

    private class MockOutputStream extends OutputStream {
        byte[] bytes = new byte[8*1024];
        int count = 0;
        @Override
        public void write(int arg0) throws IOException {
            bytes[count] = (byte) arg0;
            count++;
        }

        public String getString() {
            return new String(bytes, 0, count);
        }
    }

}
