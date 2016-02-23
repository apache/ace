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
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Integration test for our repositories, and the replication thereof.
 */
public class RepositoryTest extends IntegrationTestBase {
    private static final String REPOSITORY_FACTORY_PID = "org.apache.ace.server.repository.factory";

    private static final String REPOSITORY_NAME = "name";
    private static final String REPOSITORY_CUSTOMER = "customer";
    private static final String REPOSITORY_MASTER = "master";
    private static final String REPOSITORY_INITIAL_CONTENT = "initial";
    private static final String REPOSITORY_BASE_DIR = "basedir";
    private static final String REPOSITORY_FILE_EXTENSION = "fileextension";

    private URL m_host;

    public void testBadRequests() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        URL url = new URL(m_host, "replication/query?customer=apache&name=test&filter=test");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            int responseCode = connection.getResponseCode();
            assertResponseCode(HttpURLConnection.HTTP_BAD_REQUEST, responseCode);
        } finally {
            NetUtils.closeConnection(connection);
        }

        url = new URL(m_host, "repository/query?customer=apache&name=test&filter=test");
        
        connection = (HttpURLConnection) url.openConnection();
        try {
            int responseCode = connection.getResponseCode();
            assertResponseCode(HttpURLConnection.HTTP_BAD_REQUEST, responseCode);
        } finally {
            NetUtils.closeConnection(connection);
        }

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

    public void testGetAndPutWithCustomBasedirAndFileExtenions() throws Exception {

        File tmpFile = File.createTempFile("repo", "");
        tmpFile.delete();
        tmpFile.mkdir();
        addRepository("testInstance", "apache", "test", tmpFile.getAbsolutePath(), ".gz", null, true);

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

    /**
     * ACE-425
     */
    public void testCannotAddDuplicateRepository() throws Exception {
        String customer = "apache";
        String name = "testRepo";

        int initialSize = countServices(Repository.class);

        // Publish configuration for a repository instance
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(REPOSITORY_CUSTOMER, customer);
        props.put(REPOSITORY_NAME, name);
        props.put(REPOSITORY_BASE_DIR, "");
        props.put(REPOSITORY_FILE_EXTENSION, "");
        props.put(REPOSITORY_MASTER, "true");
        props.put("factory.instance.pid", "instance1");

        Configuration config1 = createFactoryConfiguration(REPOSITORY_FACTORY_PID);
        config1.update(props); // should succeed.

        Thread.sleep(500);

        assertEquals(initialSize + 1, countServices(Repository.class));

        props.put(REPOSITORY_FILE_EXTENSION, ".gz");
        config1.update(props); // still should succeed.

        Thread.sleep(500);

        // Not updated...
        assertEquals(initialSize + 1, countServices(Repository.class));

        Configuration config2 = createFactoryConfiguration(REPOSITORY_FACTORY_PID);
        props.put("factory.instance.pid", "instance2");
        config2.update(props); // should fail.

        Thread.sleep(500);

        // Not updated...
        assertEquals(initialSize + 1, countServices(Repository.class));
    }

    public void testInitialContent() throws Exception {
        addRepository("testInstance", "apache", "test", null, null, "somecontent", true);

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
        assertResponseCode(HttpURLConnection.HTTP_NOT_ACCEPTABLE, responseCode);

        removeRepository("testInstance");
    }
    
    public void testCommitUnchangedContents() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());

        int responseCode = put(m_host, "repository/commit", "apache", "test", "0", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        
        byteArrayInputStream.reset();
        responseCode = put(m_host, "repository/commit", "apache", "test", "1", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_NOT_MODIFIED, responseCode);

        removeRepository("testInstance");
    }
    
    public void testCommitExistingVersion() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());

        int responseCode = put(m_host, "repository/commit", "apache", "test", "0", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_OK, responseCode);
        
        byteArrayInputStream = new ByteArrayInputStream("testje".getBytes());
        responseCode = put(m_host, "repository/commit", "apache", "test", "0", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR, responseCode);

        removeRepository("testInstance");
    }
    
    public void testCommitIllegalVersion() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());

        int responseCode = put(m_host, "repository/commit", "apache", "test", "-1", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_BAD_REQUEST, responseCode);
        
        removeRepository("testInstance");
    }
    
    public void testCommitToSlave() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());

        int responseCode = put(m_host, "repository/commit", "apache", "test", "-1", byteArrayInputStream);
        assertResponseCode(HttpURLConnection.HTTP_NOT_ACCEPTABLE, responseCode);
        
        removeRepository("testInstance");
    }

    protected void configureProvisionedServices() throws IOException {
        m_host = new URL("http://localhost:" + TestConstants.PORT);

        configure("org.apache.ace.http.context", "authentication.enabled", "false");

        Utils.waitForWebserver(m_host);
    }

    @Override
    protected void doTearDown() throws Exception {
        // remove all repositories, in case a test case does not reach it's cleanup section due to an exception
        removeAllRepositories();
    }

    private void addRepository(String instanceName, String customer, String name, boolean isMaster) throws IOException, InterruptedException, InvalidSyntaxException {
        addRepository(instanceName, customer, name, null, null, null, isMaster);
    }

    /* Configure a new repository instance */
    private void addRepository(String instanceName, String customer, String name, String basedir, String fileextension, String initial, boolean isMaster) throws IOException, InterruptedException, InvalidSyntaxException {
        ServiceTracker<?, ?> tracker = new ServiceTracker<>(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
        tracker.open();

        // Publish configuration for a repository instance
        Dictionary<String, Object> props = new Hashtable<>();
        props.put(REPOSITORY_CUSTOMER, customer);
        props.put(REPOSITORY_NAME, name);
        props.put(REPOSITORY_BASE_DIR, basedir == null ? "" : basedir);
        props.put(REPOSITORY_FILE_EXTENSION, fileextension == null ? "" : fileextension);
        props.put(REPOSITORY_MASTER, String.valueOf(isMaster));
        if (initial != null) {
            props.put(REPOSITORY_INITIAL_CONTENT, initial);
        }
        props.put("factory.instance.pid", instanceName);
        Configuration config = createFactoryConfiguration(REPOSITORY_FACTORY_PID);
        config.update(props);

        try {
            if (tracker.waitForService(1000) == null) {
                throw new IOException("Did not get notified about new repository becoming available in time.");
            }
        }
        finally {
            tracker.close();
        }
    }

    private void removeAllRepositories() throws IOException, InvalidSyntaxException, InterruptedException {
        final Configuration[] configs = listConfigurations("(factory.instance.pid=*)");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);

            ServiceTracker<Repository, Repository> tracker = new ServiceTracker<Repository, Repository>(m_bundleContext, Repository.class, null) {
                @Override
                public void removedService(ServiceReference<Repository> reference, Repository service) {
                    super.removedService(reference, service);
                    // config.length times two because the service tracker also sees added events for each instance
                    if (size() == 0) {
                        sem.release();
                    }
                }
            };
            tracker.open();

            try {
                for (int i = 0; i < configs.length; i++) {
                    configs[i].delete();
                }
                if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                    throw new IOException("Not all instances were removed in time.");
                }
            }
            finally {
                tracker.close();
            }
        }
    }

    private void removeRepository(String instanceName) throws IOException, InterruptedException, InvalidSyntaxException {
        Configuration[] configs = listConfigurations("(factory.instance.pid=" + instanceName + ")");
        if ((configs != null) && (configs.length > 0)) {
            final Semaphore sem = new Semaphore(0);
            ServiceTracker<Object, Object> tracker = new ServiceTracker<Object, Object>(m_bundleContext, m_bundleContext.createFilter("(factory.instance.pid=" + instanceName + ")"), null) {
                @Override
                public void removedService(ServiceReference<Object> reference, Object service) {
                    super.removedService(reference, service);
                    sem.release();
                }
            };
            tracker.open();

            try {
                configs[0].delete();

                if (!sem.tryAcquire(1, TimeUnit.SECONDS)) {
                    throw new IOException("Instance did not get removed in time.");
                }
            }
            finally {
                tracker.close();
            }
        }
    }

    private void assertResponseCode(int expectedCode, int responseCode) {
        assertEquals("Unexpected response code;", expectedCode, responseCode);
    }
}
