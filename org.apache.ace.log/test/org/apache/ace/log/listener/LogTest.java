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
package org.apache.ace.log.listener;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.log.Log;
import org.apache.ace.log.listener.MockLog.LogEntry;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LogTest {

    private LogProxy m_logProxy;

    private Log m_mockLog;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_logProxy = new LogProxy();
        m_mockLog = new MockLog();
    }

    /**
     * Test whether logging to the cache and setting a new Log causes the log entries to be flushed to this new Log.
     */
    @Test()
    public void testLogCacheFlush() throws Exception {
        assert ((MockLog) m_mockLog).getLogEntries().size() == 0 : "MockLog is not empty on start of test";

        Dictionary<String, Object> props = new Hashtable<>();
        String test = "test";
        String value = "value";
        props.put(test, value);
        m_logProxy.log(1, props);

        assert ((MockLog) m_mockLog).getLogEntries().size() == 0 : "MockLog is not empty, but should be as the log should be in the cache";

        m_logProxy.setLog(m_mockLog);

        assert ((MockLog) m_mockLog).getLogEntries().size() == 1 : "Log should contain 1 entry";
        assert ((MockLog.LogEntry) ((MockLog) m_mockLog).getLogEntries().get(0)).getProperties().get(test).equals(value) : "The property should be 'test:value'";
    }

    /**
     * Test whether after unsetting the Log, no new log entries are added, but that they are added to the cache instead
     * (test the latter by flushing the cache).
     */
    @Test()
    public void testUnsettingLog() throws Exception {
        assert ((MockLog) m_mockLog).getLogEntries().size() == 0 : "MockLog is not empty on start of test";
        m_logProxy.setLog(m_mockLog);

        Dictionary<String, Object> props = new Hashtable<>();
        props.put("test", "value");
        m_logProxy.log(1, props);

        assert ((MockLog) m_mockLog).getLogEntries().size() == 1 : "MockLog should have 1 log entry";

        m_logProxy.setLog(null);

        Dictionary<String, Object> props2 = new Hashtable<>();
        props2.put("test2", "value2");
        m_logProxy.log(2, props2);

        assert ((MockLog) m_mockLog).getLogEntries().size() == 1 : "MockLog should still have 1 log entry";

        m_logProxy.setLog(m_mockLog);

        assert ((MockLog) m_mockLog).getLogEntries().size() == 2 : "MockLog should have 2 log entries";
    }

    /**
     * Basic functionality of the ListenerImpl is covered, the rest of the situations will probably be covered by
     * integration tests. Note: test the deployment event INSTALL only when a BundleContext is available
     */
    @SuppressWarnings("unchecked")
    @Test()
    public void testEventConverting() throws Exception {
        ListenerImpl listeners = new ListenerImpl(null, m_logProxy);
        listeners.startInternal();
        m_logProxy.setLog(m_mockLog);

        final String symbolicName = "org.apache.ace.auditlog.listener.testbundle.a";
        final long bundleId = 123;
        final String bundleVersion = "1.2.3";
        final String bundleLocation = "/home/apache/ace/testbundlea.jar";

        Bundle testBundleA = TestUtils.createMockObjectAdapter(Bundle.class, new Object() {
            @SuppressWarnings("all")
            public long getBundleId() {
                return bundleId;
            }

            @SuppressWarnings("all")
            public String getSymbolicName() {
                return symbolicName;
            }

            @SuppressWarnings("all")
            public Dictionary getHeaders() {
                Dictionary dict = new Properties();
                dict.put(Constants.BUNDLE_VERSION, bundleVersion);
                return dict;
            }

            @SuppressWarnings("all")
            public String getLocation() {
                return bundleLocation;
            }
        });

        BundleEvent bundleEvent = new BundleEvent(BundleEvent.INSTALLED, testBundleA);
        FrameworkEvent frameworkEvent = new FrameworkEvent(FrameworkEvent.INFO, testBundleA, new IllegalStateException());

        listeners.bundleChanged(bundleEvent);
        listeners.frameworkEvent(frameworkEvent);
        listeners.stopInternal();

        List<LogEntry> logEntries = ((MockLog) m_mockLog).getLogEntries();
        assert logEntries.size() == 2 : "2 log entries should be logged";

        LogEntry bundleEntry = (LogEntry) logEntries.get(0);
        assert bundleEntry.getType() == AuditEvent.BUNDLE_INSTALLED : "state BUNDLE_INSTALLED (" + AuditEvent.BUNDLE_INSTALLED + ") should be in log but '" + bundleEntry.getType() + "' is in log instead";
        Dictionary<String, ?> bundleProps = bundleEntry.getProperties();
        assert bundleProps.size() == 4 : "4 properties should be stored, but found: " + bundleProps.size();
        assert bundleProps.get(AuditEvent.KEY_ID).equals(Long.toString(bundleId)) : "id should be " + bundleId + " but is: " + bundleProps.get(AuditEvent.KEY_ID);
        assert bundleProps.get(AuditEvent.KEY_NAME).equals(symbolicName) : "symbolicName should be " + symbolicName + " but is " + bundleProps.get(AuditEvent.KEY_NAME);
        assert bundleProps.get(AuditEvent.KEY_VERSION).equals(bundleVersion) : "version should be " + bundleVersion + " but is " + bundleProps.get(AuditEvent.KEY_VERSION);
        assert bundleProps.get(AuditEvent.KEY_LOCATION).equals(bundleLocation) : "location should be " + bundleLocation + " but is " + bundleProps.get(AuditEvent.KEY_LOCATION);

        LogEntry frameworkEntry = (LogEntry) logEntries.get(1);
        assert frameworkEntry.getType() == AuditEvent.FRAMEWORK_INFO : "state FRAMEWORK_INFO (" + AuditEvent.FRAMEWORK_INFO + ") should be in log but '" + frameworkEntry.getType() + "' is in log instead";
        Dictionary<String, ?> frameworkProps = frameworkEntry.getProperties();
        assert frameworkProps.size() == 2 : "2 properties should be stored, but found: " + frameworkProps.size();
        assert frameworkProps.get(AuditEvent.KEY_ID).equals(Long.toString(bundleId)) : "id should be " + bundleId + " but is: " + frameworkProps.get(AuditEvent.KEY_ID);
        assert frameworkProps.get(AuditEvent.KEY_TYPE).equals(IllegalStateException.class.getName()) : "exceptionType should be " + IllegalStateException.class.getName() + " but is: " + frameworkProps.get(AuditEvent.KEY_TYPE);
    }
}
