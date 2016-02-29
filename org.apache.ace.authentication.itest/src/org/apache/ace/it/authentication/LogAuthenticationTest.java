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
package org.apache.ace.it.authentication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.amdatu.scheduling.Job;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.DiscoveryConstants;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.identification.IdentificationConstants;
import org.apache.ace.log.Log;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Integration tests for the audit log. Both a server and a target are setup on the same machine. The audit log is run
 * and we check if it is indeed replicated to the server.
 */
public class LogAuthenticationTest extends AuthenticationTestBase {
    private static final String AUDITLOG_ENDPOINT = "/auditlog";

    private static final String HOST = "localhost";
    private static final String TARGET_ID = "target-id";

    private String m_configurationPID;

    private volatile Log m_auditLog;
    private volatile LogStore m_serverStore;
    private volatile Job m_auditLogSyncTask;
    private volatile Repository m_userRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogReaderService m_logReader;

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(LogReaderService.class).setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                    .setRequired(true))
                .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                .add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(Log.class, "(name=auditlog)").setRequired(true))
                .add(createServiceDependency().setService(LogStore.class, "(name=auditlog)").setRequired(true))
                .add(createServiceDependency().setService(Job.class, "(taskName=auditlog)").setRequired(true))
        };
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        try {
            String baseURL = "http://" + HOST + ":" + TestConstants.PORT;
            URL testURL = new URL(baseURL.concat(AUDITLOG_ENDPOINT));
            assertTrue("Failed to access auditlog in time!", waitForURL(m_connectionFactory, testURL, 403));

            String userName = "d";
            String password = "f";

            importSingleUser(m_userRepository, userName, password);
            waitForUser(m_userAdmin, userName);

            m_configurationPID = configureFactory("org.apache.ace.connectionfactory",
                "authentication.baseURL", baseURL.concat(AUDITLOG_ENDPOINT),
                "authentication.type", "basic",
                "authentication.user.name", userName,
                "authentication.user.password", password);

            assertTrue("Failed to access auditlog in time!", waitForURL(m_connectionFactory, testURL, 200));
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        String baseURL = "http://" + HOST + ":" + TestConstants.PORT;

        getService(SessionFactory.class).createSession("test-session-ID", null);

        configureFactory("org.apache.ace.server.repository.factory",
            RepositoryConstants.REPOSITORY_NAME, "users",
            RepositoryConstants.REPOSITORY_CUSTOMER, "apache",
            RepositoryConstants.REPOSITORY_MASTER, "true");

        configure("org.apache.ace.useradmin.repository",
            "repositoryCustomer", "apache",
            "repositoryName", "users");

        configure("org.apache.ace.log.server.store.filebased", "MaxEvents", "0");

        configure(DiscoveryConstants.DISCOVERY_PID,
            DiscoveryConstants.DISCOVERY_URL_KEY, baseURL);
        configure(IdentificationConstants.IDENTIFICATION_PID,
            IdentificationConstants.IDENTIFICATION_TARGETID_KEY, TARGET_ID);

        configureFactory("org.apache.ace.target.log.store.factory",
            "name", "auditlog");
        configureFactory("org.apache.ace.target.log.factory",
            "name", "auditlog");
        configureFactory("org.apache.ace.target.log.sync.factory",
            "name", "auditlog",
            "syncInterval", "1000");
        configureFactory("org.apache.ace.log.server.servlet.factory",
            "name", "auditlog", "endpoint", AUDITLOG_ENDPOINT);
        configureFactory("org.apache.ace.log.server.store.factory",
            "name", "auditlog");

        configure("org.apache.ace.http.context", "authentication.enabled", "true");
    }

    @Override
    protected List<String> getResponse(String request) throws IOException {
        List<String> result = new ArrayList<>();

        URLConnection conn = m_connectionFactory.createConnection(new URL(request));
        try (InputStreamReader in = new InputStreamReader(conn.getInputStream()); BufferedReader reader = new BufferedReader(in)) {
            String line;
            do {
                line = reader.readLine();
                if (line != null) {
                    result.add(line);
                }
            }
            while (line != null);
        }
        finally {
            NetUtils.closeConnection(conn);
        }
        return result;
    }

    @Override
    protected void doTearDown() throws Exception {
        // Remove the configuration to start without any configured authentication...
        getConfiguration(m_configurationPID).delete();
    }

    /**
     * Tests that accessing the log servlet with authentication works when given the right credentials.
     */
    public void testAccessLogServletWithCorrectCredentialsOk() throws Exception {
        try {
            String tid1 = "42";
            String tid2 = "47";

            // prepare the store
            List<Event> events = new ArrayList<>();
            events.add(new Event(tid1, 1, 1, 1, 1));
            events.add(new Event(tid2, 1, 1, 1, 1));
            m_serverStore.put(events);

            List<String> result = getResponse("http://localhost:" + TestConstants.PORT + "/auditlog/query");
            assertTrue("We expect at least two logs on the server.", result.size() > 1);
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    /**
     * Tests that the log synchronization works when the log servlet has authentication enabled.
     */
    public void testLogSynchronizationOk() throws Exception {
        try {
            final int type = 12345;

            // now log another event
            Properties props = new Properties();
            props.put("one", "value1");
            props.put("two", "value2");
            m_auditLog.log(type, props);

            boolean found = false;

            long startTime = System.currentTimeMillis();
            long waitTime = 15000; // milliseconds

            while (!found && ((System.currentTimeMillis() - startTime) < waitTime)) {
                // synchronize again
                m_auditLogSyncTask.execute();

                // get and evaluate results (note that there is some concurrency that might interfere with this test)
                List<Descriptor> ranges2 = m_serverStore.getDescriptors();
                if (ranges2.isEmpty()) {
                    continue;
                }

                for (Descriptor descriptor : ranges2) {
                    List<Event> events = m_serverStore.get(descriptor);
                    for (Event event : events) {
                        if (event.getType() == type) {
                            Map<String, String> properties = event.getProperties();
                            assertEquals("value1", properties.get("one"));
                            assertEquals("value2", properties.get("two"));
                            found = true;
                            break;
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
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }
}
