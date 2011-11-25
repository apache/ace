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
package org.apache.ace.deployment.task;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import org.apache.ace.deployment.Deployment;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.gateway.log.store.LogStore;
import org.apache.ace.identification.Identification;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeploymentUpdateTaskTest {

    private DeploymentUpdateTask m_deploymentTask;
    private MockDeploymentService m_mockDeploymentService;

    private static final Version m_version1 = new Version("1.0.0");
    private static final Version m_version2 = new Version("2.0.0");
    private static final Version m_version3 = new Version("3.0.0");

    boolean m_correctVersionInstalled;
    boolean m_installCalled;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_mockDeploymentService = new MockDeploymentService();
        m_correctVersionInstalled = false;
        m_installCalled = false;
        m_deploymentTask = new DeploymentUpdateTask();
        TestUtils.configureObject(m_deploymentTask, LogService.class);
        TestUtils.configureObject(m_deploymentTask, Identification.class);
        TestUtils.configureObject(m_deploymentTask, Discovery.class);
        TestUtils.configureObject(m_deploymentTask, Deployment.class, m_mockDeploymentService);
    }

    @Test(groups = { UNIT })
    public synchronized void testGetHighestLocalVersion() {
        prepareMockEnvironment(new Version[] {m_version1, m_version2, m_version3}, null, null, null);
        Version highestVersion = m_deploymentTask.getHighestLocalVersion();
        assert highestVersion.equals(m_version3) : "Highest local version is incorrect, expected " + m_version3.toString() + " but got " + highestVersion.toString();
    }

    @Test(groups = { UNIT })
    public synchronized void testGetHighestRemoteVersion() throws MalformedURLException, IOException {
        URL[] urls = prepareMockEnvironment(null, new Version[] {m_version1, m_version2, m_version3}, null, null);
        Version highestVersion = m_deploymentTask.getHighestRemoteVersion(urls[0]);
        assert highestVersion.equals(m_version3) : "Highest remote version is incorrect, expected " + m_version3.toString() + " but got " + highestVersion.toString();
    }

    /**
     * Helper method to setup the correct endpoints and deploymentservice based on the appropriate mock classes.
     *
     * @param localVersions The versions that should appear to be installed.
     * @param remoteVersions The versions that should appear to be available remotely.
     * @param expectedInstallVersion The version that is expected to be installed.
     * @param malformedVersion Optional malformed version to be added to the remote versions.
     *
     * @return Array of two urls, element [0] is the controlEndpoint, element [1] is the dataEndpoint
     */
    private URL[] prepareMockEnvironment(Version[] localVersions, Version[] remoteVersions, Version expectedInstallVersion, String malformedVersion) {
        if (localVersions == null) {
            localVersions = new Version[]{};
        }
        if (remoteVersions == null) {
            remoteVersions = new Version[]{};
        }
        if (expectedInstallVersion == null) {
            expectedInstallVersion = Version.emptyVersion;
        }
        // mock installed versions
        m_mockDeploymentService.setList(localVersions);

        // mock versions available remotely through the control channel
        MockURLConnection controlURLConnection = new MockURLConnection();
        controlURLConnection.setVersions(remoteVersions, malformedVersion);

        // mock version available remotely through the data channel
        MockURLConnection dataURLConnection = new MockURLConnection();
        dataURLConnection.setVersions(new Version[] {expectedInstallVersion}, null);
        m_mockDeploymentService.setExpectedInstallVersion(expectedInstallVersion);

        URL controlEndpoint = null;
        URL dataEndpoint = null;
        try {
            // create endpoints based on mock classes
            controlEndpoint = new URL(new URL("http://notmalformed"), "", new MockURLStreamHandler(controlURLConnection));
            dataEndpoint = new URL(new URL("http://notmalformed"), "", new MockURLStreamHandler(dataURLConnection));
        }
        catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return new URL[] {controlEndpoint, dataEndpoint};
    }
    /**
     * Mock implementation of <code>DeploymentService</code> that expects Version objects.
     * The Version objects that are 'installed' can be mocked with the new <code>setList(Version[] objects)</code>
     * method. The <code>install(Inputstream is)</code> method only checks if the call was expected by verifying the
     * version matches the version set by the <code>setExpectedInstallVersion(Version v)</code> method. If so a boolean
     * in the outer class is set to true.
     */
    private class MockDeploymentService implements Deployment {
        Object[] m_objects = new Object[]{};
        Version m_expectedInstallVersion = Version.emptyVersion;

        public String getName(Object object) throws IllegalArgumentException {
            return ((Version) object).toString();
        }

        public void setExpectedInstallVersion(Version version) {
            m_expectedInstallVersion = version;
        }

        public Version getVersion(Object object) throws IllegalArgumentException {
            return (Version) object;
        }

        public Object install(InputStream inputStream) throws Exception {
            m_installCalled = true;
            BufferedReader bufReader = new BufferedReader(new InputStreamReader(inputStream));
            String versionString = bufReader.readLine();
            if (m_expectedInstallVersion.equals(new Version(versionString))) {
                m_correctVersionInstalled = true;
            }
            return new Version(versionString);
        }

        public Object[] list() {
            return m_objects;
        }

        public void setList(Version[] objects) {
            m_objects = objects;
        }

    }

    /**
     * Mock implementation of <code>URLStreamHandler</code>. Will return the <code>URLConnection</code>
     * supplied in the constructor instead when <code>openConnection(URL url)</code> is called.
     */
    private class MockURLStreamHandler extends URLStreamHandler {

        private final URLConnection m_urlConnection;

        public MockURLStreamHandler(URLConnection urlConnection) {
            m_urlConnection = urlConnection;
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return m_urlConnection;
        }
    }

    /**
     * Mock implementation of <code>URLConnection</code>. Instead of returning an inputstream
     * based on the URL it will return an inputstream based on the versions specified by the
     * new <code>setVersions(Version[] versions)</code> method.
     */
    private class MockURLConnection extends URLConnection {
        private ByteArrayInputStream m_inputStream = new ByteArrayInputStream(new byte[]{});

        protected MockURLConnection() {
            super(null);
        }

        @Override
        public void connect() throws IOException {
            // do nothing
        }

        @Override
        public InputStream getInputStream() {
            return m_inputStream;
        }

        public void setVersions(Version[] versions, String malformedVersion) {
            String versionsString = "";
            for(int i = 0; i < versions.length; i++) {
                versionsString += versions[i] + "\n";
            }
            if (malformedVersion != null) {
                versionsString += malformedVersion + "\n";
            }
            byte[] bytes = versionsString.getBytes();
            m_inputStream = new ByteArrayInputStream(bytes);
        }

    }

    public URL discover() {
        return null;
    }

    public Deployment getDeployment() {
        return TestUtils.createMockObjectAdapter(Deployment.class, m_mockDeploymentService);
    }

    public String getID() {
        return null;
    }

    public LogService getLog() {
        return TestUtils.createNullObject(LogService.class);
    }

    public LogStore getAuditStore() {
        return null;
    }

}
