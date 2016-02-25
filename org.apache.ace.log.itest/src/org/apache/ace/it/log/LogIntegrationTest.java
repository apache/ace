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
package org.apache.ace.it.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.amdatu.scheduling.Job;
import org.apache.ace.discovery.DiscoveryConstants;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.identification.IdentificationConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.log.Log;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.osgi.service.http.HttpService;

/**
 * Integration tests for the audit log. Both a server and a target are setup
 * on the same machine. The audit log is run and we check if it is indeed
 * replicated to the server.
 */
public class LogIntegrationTest extends IntegrationTestBase {
	private static final String AUDITLOG = "/auditlog";

    public static final String HOST = "localhost";
    public static final String TARGET_ID = "target-id";

    private volatile Log m_auditLog;
    private volatile LogStore m_serverStore;

    private volatile Job m_auditLogSyncTask;
    
    public void testLog() throws Exception {
    	// XXX there appears to be a dependency between both these test-methods!!!
        doTestReplication();
        doTestServlet();
    }

    @Override
	protected void configureProvisionedServices() throws Exception {
        configure(DiscoveryConstants.DISCOVERY_PID,
                DiscoveryConstants.DISCOVERY_URL_KEY, "http://" + HOST + ":" + TestConstants.PORT);
        configure(IdentificationConstants.IDENTIFICATION_PID,
                IdentificationConstants.IDENTIFICATION_TARGETID_KEY, TARGET_ID);

        configureFactory("org.apache.ace.target.log.store.factory",
                "name", "auditlog");
        configureFactory("org.apache.ace.target.log.factory",
                "name", "auditlog");
        configureFactory("org.apache.ace.target.log.sync.factory",
                "name", "auditlog");

        configure("org.apache.ace.log.server.store.filebased", 
                "MaxEvents", "0");
        
        configureFactory("org.apache.ace.log.server.servlet.factory",
                "name", "auditlog", "endpoint", AUDITLOG);
        configureFactory("org.apache.ace.log.server.store.factory",
                "name", "auditlog");

        configure("org.apache.ace.http.context", "authentication.enabled", "false");
    }

    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(Log.class, "(name=auditlog)").setRequired(true))
                .add(createServiceDependency().setService(LogStore.class, "(name=auditlog)").setRequired(true))
                .add(createServiceDependency().setService(Job.class, "(taskName=auditlog)").setRequired(true))
        };
    }

    private void doTestReplication() throws Exception {
        // now log another event
        Properties props = new Properties();
        props.put("one", "value1");
        props.put("two", "value2");
        m_auditLog.log(12345, props);

        boolean found = false;
        long startTime = System.currentTimeMillis();
        while ((!found) && (System.currentTimeMillis() - startTime < 5000)) {
            // synchronize again
            m_auditLogSyncTask.execute();

            // get and evaluate results (note that there is some concurrency that might interfere with this test)
            List<Descriptor> ranges2 = m_serverStore.getDescriptors();
            if (ranges2.size() > 0) {
            	assertEquals("We should still have audit log events for one target on the server, but found " + ranges2.size(), 1, ranges2.size()); 
                Descriptor range = ranges2.get(0);
                List<Event> events = m_serverStore.get(range);
                if (events.size() > 1) {
                	assertTrue("We should have a couple of events, at least more than the one we added ourselves.", events.size() > 1);
                    for (Event event : events) {
                        if (event.getType() == 12345) {
                            assertEquals("We could not retrieve a property of our audit log event.", "value2", event.getProperties().get("two"));
                            found = true;
                            break;
                        }
                    }
                }
            }

            // wait if we have not found anything yet
            if (!found) {
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
        assertTrue("We could not retrieve our audit log event (after 5 seconds).", found);
    }

    private void doTestServlet() throws Exception {
        // prepare the store
        List<Event> events = new ArrayList<>();
        events.add(new Event("42", 1, 1, 1, 1));
        events.add(new Event("47", 1, 1, 1, 1));
        events.add(new Event("47", 2, 1, 1, 1));
        events.add(new Event("47", 2, 2, 1, 1));
        m_serverStore.put(events);

        List<String> result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/query");
        assertTrue("We expect at least two logs on the server.", result.size() > 1);

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=42");
        assertEquals("Target 42 has a single audit log event.", 1, result.size());

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=47");
        assertEquals("Target 47 has 3 audit log events.", 3, result.size());

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=47&logid=1");
        assertEquals("Target 47, logid 1 has 1 audit log event.", 1, result.size());

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=47&logid=2");
        assertEquals("Target 47, logid 2 has 2 audit log events.", 2, result.size());

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=47&logid=2&range=1");
        assertEquals("Target 47, logid 2, range 1 has 1 audit log event.", 1, result.size());

        result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/receive?tid=47&logid=2&range=3,5");
        assertEquals("Target 47, logid 2, range 3,5 has 0 audit log event.", 0, result.size());
    }
}
