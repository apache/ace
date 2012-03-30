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
package org.apache.ace.deployment.service.impl;

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
import java.util.SortedSet;

import org.apache.ace.deployment.Deployment;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Version;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeploymentServiceImplTest {
    
    private static final Version VERSION1 = new Version("1.0.0");
    private static final Version VERSION2 = new Version("2.0.0");
    private static final Version VERSION3 = new Version("3.0.0");
    
    private DeploymentServiceImpl m_service;
    private MockDeployerService m_mockDeployerService;
    private boolean m_correctVersionInstalled;
    private boolean m_installCalled;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_mockDeployerService = new MockDeployerService();
        
        m_correctVersionInstalled = false;
        m_installCalled = false;
        m_service = new DeploymentServiceImpl();
        
        TestUtils.configureObject(m_service, LogService.class);
        TestUtils.configureObject(m_service, EventAdmin.class);
        TestUtils.configureObject(m_service, Identification.class, new Identification() {
            public String getID() {
                return "test";
            }
        });
        TestUtils.configureObject(m_service, Discovery.class, new Discovery() {
            public URL discover() {
                try {
                    return new URL("http://localhost/");
                }
                catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        TestUtils.configureObject(m_service, Deployment.class, m_mockDeployerService);
    }

    @Test(groups = { UNIT })
    public void testGetHighestLocalVersion() {
        prepareMockEnvironment(new Version[] {VERSION1, VERSION2, VERSION3}, null, null);
        Version highestVersion = m_service.getHighestLocalVersion();
        assert highestVersion.equals(VERSION3) : "Highest local version is incorrect, expected " + VERSION3.toString() + " but got " + highestVersion.toString();
    }

    @Test(groups = { UNIT })
    public void testGetRemoteVersionsWithURL() throws MalformedURLException, IOException {
        URL[] urls =  prepareMockEnvironment(null, new Version[] {VERSION1, VERSION2, VERSION3}, null);
        SortedSet<Version> highestVersion = m_service.getRemoteVersions(urls[0]);
        assert !highestVersion.isEmpty() : "Expected versions to return!";
        assert highestVersion.last().equals(VERSION3) : "Highest remote version is incorrect, expected " + VERSION3.toString() + " but got " + highestVersion.toString();
    }

    @Test(groups = { UNIT })
    public void testUpdateWithLatestVersion() throws Exception {
        final URL[] urls=  prepareMockEnvironment(null, new Version[] {VERSION1, VERSION2, VERSION3}, VERSION3);

        TestUtils.configureObject(m_service, Discovery.class, new Discovery() {
            public URL discover() {
                return urls[1];
            }
        });

        m_service.update(VERSION3);
        
        assert m_installCalled : "Install not called?!";
        assert m_correctVersionInstalled : "Wrong version installed?!";
    }

    @Test(groups = { UNIT })
    public void testUpdateWithNonLatestVersion() throws Exception {
        final URL[] urls=  prepareMockEnvironment(null, new Version[] {VERSION1, VERSION2, VERSION3}, VERSION2);

        TestUtils.configureObject(m_service, Discovery.class, new Discovery() {
            public URL discover() {
                return urls[1];
            }
        });

        m_service.update(VERSION2);
        
        assert m_installCalled : "Install not called?!";
        assert m_correctVersionInstalled : "Wrong version installed?!";
    }

    /**
     * Helper method to setup the correct endpoints and deploymentservice based on the appropriate mock classes.
     *
     * @param localVersions The versions that should appear to be installed.
     * @param remoteVersions The versions that should appear to be available remotely.
     * @param expectedInstallVersion The version that is expected to be installed.
     * @return Array of two urls, element [0] is the controlEndpoint, element [1] is the dataEndpoint
     */
    private URL[] prepareMockEnvironment(Version[] localVersions, Version[] remoteVersions, Version expectedInstallVersion) {
        if (localVersions == null) {
            localVersions = new Version[0];
        }
        if (remoteVersions == null) {
            remoteVersions = new Version[0];
        }
        if (expectedInstallVersion == null) {
            expectedInstallVersion = Version.emptyVersion;
        }
        // mock installed versions
        m_mockDeployerService.setList(localVersions);

        // mock versions available remotely through the control channel
        MockURLConnection controlURLConnection = new MockURLConnection();
        controlURLConnection.setVersions(remoteVersions, null);

        // mock version available remotely through the data channel
        MockURLConnection dataURLConnection = new MockURLConnection();
        dataURLConnection.setVersions(new Version[] {expectedInstallVersion}, null);

        m_mockDeployerService.setExpectedInstallVersion(expectedInstallVersion);

        final URL controlEndpoint;
        URL dataEndpoint = null;
        try {
            // create endpoints based on mock classes
            controlEndpoint = new URL(new URL("http://notmalformed"), "", new MockURLStreamHandler(controlURLConnection));
            dataEndpoint = new URL(new URL("http://notmalformed"), "", new MockURLStreamHandler(dataURLConnection));
        }
        catch (MalformedURLException e) {
            throw new RuntimeException(e);
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
    private class MockDeployerService implements Deployment {
        Object[] m_objects = new Object[]{};
        Version m_expectedInstallVersion = Version.emptyVersion;

        public String getName(Object object) throws IllegalArgumentException {
            return "test";
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
    private static class MockURLStreamHandler extends URLStreamHandler {
        private final URLConnection urlConn;

        public MockURLStreamHandler(URLConnection urlConnection) {
            this.urlConn = urlConnection;
        }

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return urlConn;
        }
    }

    /**
     * Mock implementation of <code>URLConnection</code>. Instead of returning an inputstream
     * based on the URL it will return an inputstream based on the versions specified by the
     * new <code>setVersions(Version[] versions)</code> method.
     */
    private static class MockURLConnection extends URLConnection {
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
}
