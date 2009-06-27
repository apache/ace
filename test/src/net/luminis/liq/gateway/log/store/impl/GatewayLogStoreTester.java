package net.luminis.liq.gateway.log.store.impl;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.luminis.liq.gateway.log.store.LogStore;
import net.luminis.liq.identification.Identification;
import net.luminis.liq.log.AuditEvent;
import net.luminis.liq.log.LogEvent;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GatewayLogStoreTester {
    private LogStoreImpl m_logStore;
    private File m_dir = null;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_dir  = File.createTempFile(LogStore.class.getName(), null);
        m_dir.delete();
        m_logStore = new LogStoreImpl(m_dir);
        TestUtils.configureObject(m_logStore, LogService.class);
        TestUtils.configureObject(m_logStore, Identification.class, TestUtils.createMockObjectAdapter(Identification.class, new Object() {
            @SuppressWarnings("unused")
            public String getID() {
                return "test";
            }
        }));
        m_logStore.start();
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws IOException {
        m_logStore.stop();
        delete(m_dir);
        m_logStore = null;
    }

    @SuppressWarnings({ "serial", "unchecked" })
    @Test(groups = {UNIT})
    public void testLog() throws IOException {
        long[] ids = m_logStore.getLogIDs();
        assert ids.length == 1 : "New store should have only one id";
        List<String> events = new ArrayList<String>();
        events.add(m_logStore.put(AuditEvent.FRAMEWORK_STARTED, new Properties() {{put("test", "test");}}).toRepresentation());
        events.add(m_logStore.put(AuditEvent.BUNDLE_INSTALLED, new Properties() {{put("test", "test");}}).toRepresentation());
        events.add(m_logStore.put(AuditEvent.DEPLOYMENTADMIN_COMPLETE, new Properties() {{put("test", "test");}}).toRepresentation());
        ids = m_logStore.getLogIDs();
        assert ids.length == 1 : "Error free store should have only one id";
        long highest = m_logStore.getHighestID(ids[0]);
        assert  highest == 3 : "Store with 3 entries should have 3 as highest id but was: " + highest;
        List<String> result = new ArrayList<String>();
        for (LogEvent event : (List<LogEvent>) m_logStore.get(ids[0])) {
            result.add(event.toRepresentation());
        }
        assert result.equals(events) : "Events " + events + " should equal full log " + result;
        result = new ArrayList<String>();
        for (LogEvent event : (List<LogEvent>) m_logStore.get(ids[0], 1, highest)) {
            result.add(event.toRepresentation());
        }
        assert result.equals(events) : "Events " + events + " should equal full log " + result;
    }

    @Test(groups = {UNIT}, expectedExceptions = {IOException.class})
    public void testExceptionHandling() throws IOException {
        m_logStore.handleException(m_logStore.getLog(4711), new IOException("test"));
    }

    private static void delete(File target) {
        if (target.isDirectory()) {
            for (File child : target.listFiles()) {
                delete(child);
            }
        }
        target.delete();
    }
}
