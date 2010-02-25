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
package org.apache.ace.test.log;

import static org.apache.ace.test.utils.TestUtils.INTEGRATION;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.ace.log.Log;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

/**
 * Integration tests for the audit log. Both a server and a gateway are setup
 * on the same machine. The audit log is run and we check if it is indeed
 * replicated to the server.
 */
public class LogIntegrationTest {
    private volatile Log m_auditLog;
    private volatile LogStore m_serverStore;
    private volatile Runnable m_auditLogSyncTask;
    private static Object instance;

    public LogIntegrationTest() {
        synchronized (LogIntegrationTest.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }

    @Factory
    public Object[] createInstance() {
        synchronized (LogIntegrationTest.class) {
            return new Object[] { instance };
        }
    }

    @Test(groups = { INTEGRATION })
    public void runTests() throws Exception {
        startupAndTestReplication();
        testServlet();
    }

    public void startupAndTestReplication() throws Exception {
        // now log another event
        Properties props = new Properties();
        props.put("one", "value1");
        props.put("two", "value2");
        m_auditLog.log(12345, props);

        boolean found = false;
        long startTime = System.currentTimeMillis();
        while ((!found) && (System.currentTimeMillis() - startTime < 5000)) {
            // synchronize again
            m_auditLogSyncTask.run();

            // get and evaluate results (note that there is some concurrency that might interfere with this test)
            List<LogDescriptor> ranges2 = m_serverStore.getDescriptors();
            if (ranges2.size() > 0) {
//              assert ranges2.size() == 1 : "We should still have audit log events for one gateway on the server, but found " + ranges2.size();
                LogDescriptor range = ranges2.get(0);
                List<LogEvent> events = m_serverStore.get(range);
                if (events.size() > 1) {
//                  assert events.size() > 1 : "We should have a couple of events, at least more than the one we added ourselves.";
                    for (LogEvent event : events) {
                        if (event.getType() == 12345) {
                            assert event.getProperties().get("two").equals("value2") : "We could not retrieve a property of our audit log event.";
                            found = true;
                            break;
                        }
                    }
                }
            }

            // wait if we have not found anything yet
            if (!found) {
                try { TimeUnit.MILLISECONDS.sleep(100); } catch (InterruptedException ie) {}
            }
        }
        assert found : "We could not retrieve our audit log event (after 5 seconds).";
    }

    public void testServlet() throws Exception {
        // prepare the store
        List<LogEvent> events = new ArrayList<LogEvent>();
        events.add(new LogEvent("42", 1, 1, 1, 1, new Properties()));
        events.add(new LogEvent("47", 1, 1, 1, 1, new Properties()));
        events.add(new LogEvent("47", 2, 1, 1, 1, new Properties()));
        events.add(new LogEvent("47", 2, 2, 1, 1, new Properties()));
        m_serverStore.put(events);

        List<String> result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/query");
        assert result.size() > 1 : "We expect at least two logs on the server.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=42");
        assert result.size() == 1 : "Gateway 42 has a single audit log event.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=47");
        assert result.size() == 3 : "Gateway 47 has 3 audit log events.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=47&logid=1");
        assert result.size() == 1 : "Gateway 47, logid 1 has 1 audit log event.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=47&logid=2");
        assert result.size() == 2 : "Gateway 47, logid 2 has 2 audit log events.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=47&logid=2&range=1");
        assert result.size() == 1 : "Gateway 47, logid 2, range 1 has 1 audit log event.";

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?gwid=47&logid=2&range=3,5");
        assert result.size() == 0 : "Gateway 47, logid 2, range 3,5 has 0 audit log event.";
    }

    private List<String> getResponse(String request) throws IOException {
        List<String> result = new ArrayList<String>();
        InputStream in = null;
        try {
            in = new URL(request).openConnection().getInputStream();
            byte[] response = new byte[in.available()];
            in.read(response);

            StringBuilder element = new StringBuilder();
            for (byte b : response) {
                switch(b) {
                    case '\n' :
                        result.add(element.toString());
                        element = new StringBuilder();
                        break;
                    default :
                        element.append(b);
                }
            }
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // no problem.
            }
        }
        return result;
    }
}
