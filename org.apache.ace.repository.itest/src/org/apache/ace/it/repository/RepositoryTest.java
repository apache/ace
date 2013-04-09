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
package org.apache.ace.it.repository;

import static org.apache.ace.it.repository.Utils.get;
import static org.apache.ace.it.repository.Utils.put;
import static org.apache.ace.it.repository.Utils.query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Integration test for our repositories, and the replication thereof.
 */
public class RepositoryTest extends IntegrationTestBase {

	private volatile ConfigurationAdmin m_configAdmin;
	
    private URL m_host;
    
    public void testBadRequests() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        URL url = new URL(m_host, "replication/query?customer=apache&name=test&filter=test");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int responseCode = connection.getResponseCode();
        assertResponseCode(HttpURLConnection.HTTP_BAD_REQUEST, responseCode);

        url = new URL(m_host, "repository/query?customer=apache&name=test&filter=test");
        connection = (HttpURLConnection) url.openConnection();
        responseCode = connection.getResponseCode();
        assertResponseCode(HttpURLConnection.HTTP_BAD_REQUEST, responseCode);

        removeRepository("testInstance");
    }

    public void testCheckoutAndCommit() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = get(m_host, "repository/checkout", "apache", "test", "1", out);
        assertResponseCode(HttpURLConnection.HTTP_NOT_FOUND, responseCode);

        ByteArrayInputStream input = new ByteArrayInputStream("test".getBytes());
        responseCode = put(m_host, "repository/commit", "apache", "test", "1", input);
        assertResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR, responseCode);

        input.reset();
        responseCode = put(m_host, "repository/commit", "apache", "test", "0", input);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);

        out.reset();
        responseCode = get(m_host, "repository/checkout", "apache", "test", "1", out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);

        removeRepository("testInstance");
    }

    public void testCreation() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = query(m_host, "replication/query", null, null, out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        
        assertEquals("Expected one repository without any versions as query result instead of : " + out.toString(), "apache,test,\n", out.toString());

        addRepository("testInstance2", "apache", "test2", true);

        out.reset();
        responseCode = query(m_host, "replication/query", null, null, out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        assertTrue("Expected two repositories without any versions as query result instead of : " + out.toString(), 
        	"apache,test,\napache,test2,\n".equals(out.toString()) || "apache,test2,\napache,test,\n".equals(out.toString()));

        removeRepository("testInstance2");

        out.reset();
        responseCode = query(m_host, "replication/query", null, null, out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        assertEquals("Expected one repository without any versions as query result instead of : " + out.toString(), "apache,test,\n", out.toString());

        removeRepository("testInstance");

        out.reset();
        responseCode = query(m_host, "replication/query", null, null, out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        assertEquals("Expected one repository without any versions as query result instead of : " + out.toString(), "", out.toString());
    }

    public void testGetAndPut() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = get(m_host, "replication/get", "apache", "test", "1", out);
        assertResponseCode(HttpURLConnection.HTTP_NOT_FOUND, responseCode);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());
        responseCode = put(m_host, "replication/put", "apache", "test", "1", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);

        out.reset();
        responseCode = get(m_host, "replication/get", "apache", "test", "1", out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        
        assertEquals("test", out.toString());

        removeRepository("testInstance");
    }

    public void testInitialContent() throws Exception {
        addRepository("testInstance", "apache", "test", "somecontent", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int responseCode = get(m_host, "repository/checkout", "apache", "test", "1", out);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);

        assertEquals("somecontent", out.toString());

        removeRepository("testInstance");
    }

    public void testMaster() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());

        int responseCode = put(m_host, "replication/put", "apache", "test", "1", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);

        byteArrayInputStream.reset();
        
        responseCode = put(m_host, "repository/commit", "apache", "test", "0", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR, responseCode);

        removeRepository("testInstance");
    }

    protected void before() throws IOException {
		m_host = new URL("http://localhost:" + TestConstants.PORT);

        configure("org.apache.ace.repository.servlet.RepositoryReplicationServlet",
                HttpConstants.ENDPOINT, "/replication", "authentication.enabled", "false");
        configure("org.apache.ace.repository.servlet.RepositoryServlet",
                HttpConstants.ENDPOINT, "/repository", "authentication.enabled", "false");

        Utils.waitForWebserver(m_host);
    }

    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
        };
    }

    protected void tearDown() throws Exception {
        // remove all repositories, in case a test case does not reach it's cleanup section due to an exception
        removeAllRepositories();
    }

    private void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException, InterruptedException, InvalidSyntaxException {
        addRepository(instanceName, customer, name, null, isMaster);
    }

    /* Configure a new repository instance */
    private void addRepository(String instanceName, String customer, String name, String initial, boolean isMaster) throws IOException, InterruptedException, InvalidSyntaxException {
        // Publish configuration for a repository instance
        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, customer);
        props.put(RepositoryConstants.REPOSITORY_NAME, name);
        props.put(RepositoryConstants.REPOSITORY_MASTER, String.valueOf(isMaster));
        if (initial != null) {
            props.put(RepositoryConstants.REPOSITORY_INITIAL_CONTENT, initial);
        }
        props.put("factory.instance.pid", instanceName);
        Configuration config = m_configAdmin.createFactoryConfiguration("org.apache.ace.server.repository.factory", null);

        ServiceTracker tracker = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
        tracker.open();

        config.update(props);

        if (tracker.waitForService(1000) == null) {
            throw new IOException("Did not get notified about new repository becoming available in time.");
        }
        tracker.close();
    }

    private void removeAllRepositories() throws IOException, InvalidSyntaxException, InterruptedException {
        final Configuration[] configs = m_configAdmin.listConfigurations("(factory.instance.pid=*)");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);

            ServiceTracker tracker = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(" + Constants.OBJECTCLASS + "=" + Repository.class.getName() + ")"), null) {
                @Override
                public void removedService(ServiceReference reference, Object service) {
                    super.removedService(reference, service);
                    // config.length times two because the service tracker also sees added events for each instance
                    if (size() == 0) {
                        sem.release();
                    }
                }
            };
            tracker.open();

            for (int i = 0; i < configs.length; i++) {
                configs[i].delete();
            }

            if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new IOException("Not all instances were removed in time.");
            }
            tracker.close();
        }
    }

    private void removeRepository(String instanceName) throws IOException, InterruptedException, InvalidSyntaxException {
        Configuration[] configs = null;
        try {
            configs = m_configAdmin.listConfigurations("(factory.instance.pid=" + instanceName + ")");
        }
        catch (InvalidSyntaxException e) {
            // should not happen
        }
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);
            ServiceTracker tracker = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null) {
                @Override
                public void removedService(ServiceReference reference, Object service) {
                    super.removedService(reference, service);
                    sem.release();
                }
            };
            tracker.open();

            configs[0].delete();

            if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                throw new IOException("Instance did not get removed in time.");
            }
        }
    }

    private void assertResponseCode(int expectedCode, int responseCode) {
    	assertEquals("Unexpected response code;", expectedCode, responseCode);
    }
}
