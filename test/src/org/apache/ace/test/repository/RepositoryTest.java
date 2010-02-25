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
package org.apache.ace.test.repository;

import static org.apache.ace.test.utils.TestUtils.INTEGRATION;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class RepositoryTest {

    private static Object m_instance;

    private static final int COPY_BUFFER_SIZE = 4096;
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final String HOST = "http://localhost:" + TestConstants.PORT;

    private volatile ConfigurationAdmin m_configAdmin;  // Injected by dependency manager
    private volatile BundleContext m_context;           // Injected by dependency manager

    public RepositoryTest() {
        synchronized (RepositoryTest.class) {
            if (m_instance == null) {
                m_instance = this;
            }
        }
    }

    @Factory
    public Object[] createInstances() {
        synchronized (RepositoryTest.class) {
            return new Object[] { m_instance };
        }
    }

    protected void start() throws IOException {
        // configure the (replication)repository servlets
        setProperty("org.apache.ace.repository.servlet.RepositoryReplicationServlet", new Object[][] { { HttpConstants.ENDPOINT, "/replication" } });
        setProperty("org.apache.ace.repository.servlet.RepositoryServlet", new Object[][] { { HttpConstants.ENDPOINT, "/repository" } });
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
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

        ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(factory.instance.pid=" + instanceName + ")"), null);
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

            ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(" + Constants.OBJECTCLASS + "=" + Repository.class.getName() + ")"), null) {
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
            ServiceTracker tracker = new ServiceTracker(m_context, m_context.createFilter("(factory.instance.pid=" + instanceName + ")"), null) {
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

    /* Configure properties for the specified service PID */
    @SuppressWarnings("unchecked")
    private void setProperty(String pid, Object[][] props) throws IOException {
        Configuration configuration = m_configAdmin.getConfiguration(pid, null);
        Dictionary dictionary = configuration.getProperties();
        if (dictionary == null) {
            dictionary = new Hashtable();
        }
        for (Object[] pair : props) {
            dictionary.put(pair[0], pair[1]);
        }
        configuration.update(dictionary);
    }

    @Test(groups = { INTEGRATION })
    public void testCreation() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = query(HOST, "replication/query", null, null, out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;
        assert "apache,test,\n".equals(out.toString()) : "Expected one repository without any versions as query result instead of : " + out.toString();

        addRepository("testInstance2", "apache", "test2", true);

        out.reset();
        responseCode = query(HOST, "replication/query", null, null, out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;
        assert "apache,test,\napache,test2,\n".equals(out.toString()) ||
               "apache,test2,\napache,test,\n".equals(out.toString()) : "Expected two repositories without any versions as query result instead of : " + out.toString();

        removeRepository("testInstance2");

        out.reset();
        responseCode = query(HOST, "replication/query", null, null, out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;
        assert "apache,test,\n".equals(out.toString()) : "Expected one repository without any versions as query result instead of : " + out.toString();

        removeRepository("testInstance");

        out.reset();
        responseCode = query(HOST, "replication/query", null, null, out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;
        assert "".equals(out.toString()) : "Expected one repository without any versions as query result instead of : " + out.toString();
    }

    @Test(groups = { INTEGRATION })
    public void testGetAndPut() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = get(HOST, "replication/get", "apache", "test", "1", out);
        assert responseCode == HttpURLConnection.HTTP_NOT_FOUND : "Expected responsecode " + HttpURLConnection.HTTP_NOT_FOUND + " instead of " + responseCode;

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());
        responseCode = put("replication/put", HOST, "apache", "test", "1", byteArrayInputStream);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;

        out.reset();
        responseCode = get(HOST, "replication/get", "apache", "test", "1", out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;
        assert "test".equals(out.toString()) : "Expected 'test' as a result of the get operation, not: " + out.toString();

        removeRepository("testInstance");
    }

    @Test(groups = { INTEGRATION })
    public void testCheckoutAndCommit() throws Exception {
        addRepository("testInstance", "apache", "test", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = get(HOST, "repository/checkout", "apache", "test", "1", out);
        assert responseCode == HttpURLConnection.HTTP_NOT_FOUND : "Expected responsecode " + HttpURLConnection.HTTP_NOT_FOUND + " instead of " + responseCode;

        ByteArrayInputStream input = new ByteArrayInputStream("test".getBytes());
        responseCode = put("repository/commit", HOST, "apache", "test", "1", input);
        assert responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR : "Expected responsecode " + HttpURLConnection.HTTP_INTERNAL_ERROR + " instead of " + responseCode;

        input.reset();
        responseCode = put("repository/commit", HOST, "apache", "test", "0", input);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;

        out.reset();
        responseCode = get(HOST, "repository/checkout", "apache", "test", "1", out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;

        removeRepository("testInstance");
    }

    @Test(groups = { INTEGRATION })
    public void testMaster() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("test".getBytes());
        int responseCode = put("replication/put", HOST, "apache", "test", "1", byteArrayInputStream);
        assert responseCode == HttpURLConnection.HTTP_OK;

        byteArrayInputStream.reset();
        responseCode = put("repository/commit", HOST, "apache", "test", "0", byteArrayInputStream);
        assert responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;

        removeRepository("testInstance");
    }

    @Test(groups = { INTEGRATION })
    public void testBadRequests() throws Exception {
        addRepository("testInstance", "apache", "test", false);

        URL url = new URL(new URL(HOST), "replication/query?customer=apache&name=test&filter=test");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int responseCode = connection.getResponseCode();
        assert responseCode == HttpURLConnection.HTTP_BAD_REQUEST : "Expected responsecode " + HttpURLConnection.HTTP_BAD_REQUEST + " instead of " + responseCode;

        url = new URL(new URL(HOST), "repository/query?customer=apache&name=test&filter=test");
        connection = (HttpURLConnection) url.openConnection();
        responseCode = connection.getResponseCode();
        assert responseCode == HttpURLConnection.HTTP_BAD_REQUEST : "Expected responsecode " + HttpURLConnection.HTTP_BAD_REQUEST + " instead of " + responseCode;

        removeRepository("testInstance");
    }

    @Test(groups = { INTEGRATION })
    public void testInitialContent() throws Exception {
        addRepository("testInstance", "apache", "test", "somecontent", true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int responseCode = get(HOST, "repository/checkout", "apache", "test", "1", out);
        assert responseCode == HttpURLConnection.HTTP_OK : "Expected responsecode " + HttpURLConnection.HTTP_OK + " instead of " + responseCode;

        assert Arrays.equals(out.toByteArray(), "somecontent".getBytes());

        removeRepository("testInstance");
    }

    private static int query(String host, String endpoint, String customer, String name, OutputStream out) throws MalformedURLException, IOException {
        String f1 = (customer == null) ? null : "customer=" + customer;
        String f2 = (name == null) ? null : "name=" + name;
        String filter = ((f1 == null) ? "?" : "?" + f1 + "&") + ((f2 == null) ? "" : f2);
        URL url = new URL(new URL(host), endpoint + filter);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream input = connection.getInputStream();
            copy(input, out);
            out.flush();
        }
        return responseCode;
    }

    private static int get(String host, String endpoint, String customer, String name, String version, OutputStream out) throws MalformedURLException, IOException, FileNotFoundException {
        URL url = new URL(new URL(host), endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream input = connection.getInputStream();
            copy(input, out);
            out.flush();
        }
        return responseCode;
    }

    private static int put(String endpoint, String host, String customer, String name, String version, InputStream in) throws MalformedURLException, IOException, FileNotFoundException {
        URL url = new URL(new URL(host), endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", MIME_APPLICATION_OCTET_STREAM);
        OutputStream out = connection.getOutputStream();
        copy(in, out);
        out.flush();
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            InputStream is = (InputStream) connection.getContent();
            is.close();
        }
        return responseCode;
    }

    /* copy in to out */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }
}
