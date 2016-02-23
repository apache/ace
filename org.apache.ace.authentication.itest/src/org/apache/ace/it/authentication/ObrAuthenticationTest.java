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

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.obr.storage.OBRFileStoreConstants;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Version;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a test case in which the OBR has authentication enabled, and the rest of ACE has to remain function
 * correctly.
 */
public class ObrAuthenticationTest extends AuthenticationTestBase {

    private volatile String m_endpoint;
    private volatile File m_storeLocation;
    private volatile String m_authConfigPID;

    /* Injected by dependency manager */
    private volatile ArtifactRepository m_artifactRepository;
    private volatile Repository m_userRepository;
    private volatile UserAdmin m_userAdmin;
    private volatile ConfigurationAdmin m_configAdmin;
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogReaderService m_logReader;

    private URL m_obrURL;

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(LogReaderService.class).setRequired(true))
                .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
                .add(createServiceDependency().setService(ConnectionFactory.class).setRequired(true))
                .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                    .setRequired(true))
        };
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        m_endpoint = "/obr";
        String tmpDir = System.getProperty("java.io.tmpdir");
        m_storeLocation = new File(tmpDir, "store");
        m_storeLocation.delete();
        m_storeLocation.mkdirs();

        final String fileLocation = m_storeLocation.getAbsolutePath();
        getService(SessionFactory.class).createSession("test-session-ID", null);

        configureFactory("org.apache.ace.server.repository.factory",
            RepositoryConstants.REPOSITORY_NAME, "users",
            RepositoryConstants.REPOSITORY_CUSTOMER, "apache",
            RepositoryConstants.REPOSITORY_MASTER, "true");

        configure("org.apache.ace.useradmin.repository",
            "repositoryLocation", "http://localhost:" + TestConstants.PORT + "/repository",
            "repositoryCustomer", "apache",
            "repositoryName", "users");

        configure("org.apache.ace.log.server.store.filebased", "MaxEvents", "0");

        m_obrURL = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/");

        configure("org.apache.ace.client.repository", "obrlocation", m_obrURL.toExternalForm());

        configure("org.apache.ace.obr.storage.file", OBRFileStoreConstants.FILE_LOCATION_KEY, fileLocation);

        configure("org.apache.ace.http.context", "authentication.enabled", "true");
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        try {
            String userName = "d";
            String password = "f";
            importSingleUser(m_userRepository, userName, password);
            waitForUser(m_userAdmin, userName);

            URL testURL = new URL(m_obrURL, "index.xml");

            assertTrue("Failed to access OBR in time!", waitForURL(m_connectionFactory, testURL, HttpURLConnection.HTTP_FORBIDDEN));

            m_authConfigPID = configureFactory("org.apache.ace.connectionfactory",
                "authentication.baseURL", m_obrURL.toExternalForm(),
                "authentication.type", "basic",
                "authentication.user.name", userName,
                "authentication.user.password", password);

            assertTrue("Failed to access OBR in time!", waitForURL(m_connectionFactory, testURL, HttpURLConnection.HTTP_OK));
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    @Override
    public void doTearDown() throws Exception {
        if (m_authConfigPID != null) {
            Configuration configuration = getConfiguration(m_authConfigPID);
            if (configuration != null) {
                configuration.delete();
            }
        }
        FileUtils.removeDirectoryWithContent(m_storeLocation);
    }

    /**
     * Test that we can retrieve the 'index.xml' from the OBR.
     */
    public void testAccessObrRepositoryWithCredentialsOk() throws Exception {
        URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/index.xml");
        URLConnection conn = null;
        try {
            conn = m_connectionFactory.createConnection(url);
            assertNotNull(conn);
            Object content = conn.getContent();
            assertNotNull(content);
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        } finally {
            NetUtils.closeConnection(conn);
        }
    }

    /**
     * Test that we cannot retrieve the 'index.xml' from the OBR without any credentials.
     */
    public void testAccessObrRepositoryWithoutCredentialsFail() throws Exception {
        try {
            URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/index.xml");

            // do NOT use connection factory as it will supply the credentials for us...
            URLConnection conn = url.openConnection();
            assertNotNull(conn);

            // we expect a 403 for this URL...
            assertTrue(NetUtils.waitForURL(url, HttpURLConnection.HTTP_FORBIDDEN));

            try {
                // ...causing all other methods on URLConnection to fail...
                conn.getContent(); // should fail!
                fail("IOException expected!");
            }
            catch (IOException exception) {
                // Ok; ignored...
            }
            finally {
                NetUtils.closeConnection(conn);
            }
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    /**
     * Test that we cannot retrieve the 'index.xml' from the OBR with incorrect credentials.
     */
    public void testAccessObrRepositoryWithWrongCredentialsFail() throws Exception {
        try {
            org.osgi.service.cm.Configuration configuration = m_configAdmin.getConfiguration(m_authConfigPID);
            assertNotNull(configuration);

            // Simulate incorrect credentials by updating the config of the connection factory...
            configuration.getProperties().put("authentication.user.name", "foo");

            configuration.update();

            URL url = new URL("http://localhost:" + TestConstants.PORT + m_endpoint + "/index.xml");

            // do NOT use connection factory as it will supply the credentials for us...
            URLConnection conn = url.openConnection();
            assertNotNull(conn);

            // we expect a 403 for this URL...
            assertTrue(NetUtils.waitForURL(url, HttpURLConnection.HTTP_FORBIDDEN));

            try {
                // ...causing all other methods on URLConnection to fail...
                conn.getContent(); // should fail!
                fail("IOException expected!");
            }
            catch (IOException exception) {
                // Ok; ignored...
            }
            finally {
                NetUtils.closeConnection(conn);
            }
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    /**
     * Test that an import of an artifact through the API of ACE works, making sure they can access an authenticated OBR
     * as well.
     */
    public void testImportArtifactWithCredentialsOk() throws Exception {
        try {
            // Use a valid JAR file, without a Bundle-SymbolicName header.
            File temp = FileUtils.createEmptyBundle("org.apache.ace.test1", new Version(1, 0, 0));
            temp.deleteOnExit();

            m_artifactRepository.importArtifact(temp.toURI().toURL(), true /* upload */);

            assertEquals(1, m_artifactRepository.get().size());
            assertTrue(m_artifactRepository.getResourceProcessors().isEmpty());

            // Create a JAR file which looks like a resource processor supplying bundle.
            temp = FileUtils.createEmptyBundle("org.apache.ace.test2", new Version(1, 0, 0), 
                BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "someProcessor",
                BundleHelper.KEY_VERSION, "1.0.0.processor");

            m_artifactRepository.importArtifact(temp.toURI().toURL(), true);

            assertEquals(1, m_artifactRepository.get().size());
            assertEquals(1, m_artifactRepository.getResourceProcessors().size());
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }

    /**
     * Test that an import of an artifact through the API of ACE works, making sure they can access an authenticated OBR
     * as well.
     */
    public void testImportArtifactWithoutCredentialsFail() throws Exception {
        try {
            org.osgi.service.cm.Configuration configuration = m_configAdmin.getConfiguration(m_authConfigPID);
            assertNotNull(configuration);

            // Delete the credentials for the OBR-URL, thereby simulating wrong credentials for the OBR...
            configuration.delete();

            // Use a valid JAR file, with a Bundle-SymbolicName header.
            File temp = FileUtils.createEmptyBundle("org.apache.ace.test3", new Version(1, 0, 0));
            temp.deleteOnExit();

            try {
                m_artifactRepository.importArtifact(temp.toURI().toURL(), true /* upload */); // should fail!
                fail("IOException expected!");
            }
            catch (IOException exception) {
                // Ok; expected...
            }
        }
        catch (Exception e) {
            printLog(m_logReader);
            throw e;
        }
    }
}
