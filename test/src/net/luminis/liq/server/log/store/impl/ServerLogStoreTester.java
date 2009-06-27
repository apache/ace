package net.luminis.liq.server.log.store.impl;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import net.luminis.liq.log.AuditEvent;
import net.luminis.liq.log.LogDescriptor;
import net.luminis.liq.log.LogEvent;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.service.event.EventAdmin;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ServerLogStoreTester {
    private LogStoreImpl m_logStore;
    private File m_dir;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_dir = File.createTempFile("logstore", "txt");
        m_dir.delete();
        m_dir.mkdirs();
        m_logStore = new LogStoreImpl(m_dir, "log");
        TestUtils.configureObject(m_logStore, EventAdmin.class);
        m_logStore.start();
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws IOException {
        delete(m_dir);
    }

    @SuppressWarnings("serial")
    @Test(groups = { UNIT })
    public void testLog() throws IOException {
        List<LogDescriptor> ranges = m_logStore.getDescriptors();
        assert ranges.isEmpty() : "New store should have no ranges.";
        List<LogEvent> events = new ArrayList<LogEvent>();
        for (String gateway : new String[] { "g1", "g2", "g3" }) {
            for (long log : new long[] { 1, 2, 3, 5 }) {
                for (long id : new long[] { 1, 2, 3, 20 }) {
                    events.add(new LogEvent(gateway, log, id, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, new Properties() {
                        {
                            put("test", "bar");
                        }
                    }));
                }
            }
        }
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 3 * 4 : "Incorrect amount of ranges returned from store";
        List<LogEvent> stored = new ArrayList<LogEvent>();
        for (LogDescriptor range : m_logStore.getDescriptors()) {
            for (LogDescriptor range2 : m_logStore.getDescriptors(range.getGatewayID())) {
                stored.addAll(m_logStore.get(m_logStore.getDescriptor(range2.getGatewayID(), range2.getLogID())));
            }
        }

        Set<String> in = new HashSet<String>();
        for (LogEvent event : events)  {
            in.add(event.toRepresentation());
        }
        Set<String> out = new HashSet<String>();
        for (LogEvent event : stored) {
            out.add(event.toRepresentation());
        }
        assert in.equals(out) : "Stored events differ from the added.";
    }

    /**
     * See ATL-1537
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testLogWithSpecialCharacters() throws IOException {
        String gatewayID = "myga\0teway";
        LogEvent event = new LogEvent(gatewayID, 1, 1, System.currentTimeMillis(), AuditEvent.FRAMEWORK_STARTED, new Properties());
        List<LogEvent> events = new ArrayList<LogEvent>();
        events.add(event);
        m_logStore.put(events);
        assert m_logStore.getDescriptors().size() == 1 : "Incorrect amount of ranges returned from store: expected 1, found " + m_logStore.getDescriptors().size();
        assert m_logStore.getDescriptors(gatewayID).size() == 1 : "We expect to find a single event: expected 1, found " + m_logStore.getDescriptors(gatewayID).size();
    }

    private void delete(File root) {
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                delete(child);
            }
        }
        root.delete();
    }

}
