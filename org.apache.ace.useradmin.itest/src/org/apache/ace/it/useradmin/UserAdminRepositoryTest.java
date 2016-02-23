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
package org.apache.ace.it.useradmin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.UserAdmin;

public class UserAdminRepositoryTest extends IntegrationTestBase {

    private URL m_host;

    private volatile UserAdmin m_userAdmin;
    private volatile Repository m_repository;

    public void testUpdateUserFetchedBeforeRepoSync() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role user1 = m_userAdmin.createRole("user1", Role.USER);
        high = waitForRepoChange(high);
        user1.getProperties().put("test", "property");
        high = waitForRepoChange(high);

        // Write a new version directly to the repository
        long latest = m_repository.getRange().getHigh();
        // Use *remote* repository...
        URL remoteURL = new URL("http://localhost:" + TestConstants.PORT + "/repository/commit?customer=apache&name=user&version=" + latest);
        HttpURLConnection conn = (HttpURLConnection) remoteURL.openConnection();
        try {
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(8192);
            conn.setRequestProperty("Content-Type", "application/octet-stream");

            try (OutputStream os = conn.getOutputStream()) {
                os.write("<roles><user name=\"user1\"><properties><test>changed</test></properties></user></roles>".getBytes());
            }
            assertEquals(200, conn.getResponseCode());
        }
        finally {
            NetUtils.closeConnection(conn);
        }

        try {
            // Expect that updating properties fails as the user was fetched before the repository was refreshed
            user1.getProperties().put("this", "fails");
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException e) {
            // expected
        }

        Role role = m_userAdmin.getRole("user1");
        assertEquals("changed", role.getProperties().get("test"));
    }

    public void testAddUser() throws Exception {
        long high = m_repository.getRange().getHigh();
        m_userAdmin.createRole("user1", Role.USER);
        waitForRepoChange(high);

        String repoContentsAsString = getRepoContentsAsString();
        assertTrue(repoContentsAsString.contains("<user name=\"user1\">"));
    }

    public void testDuplicateAddUser() throws Exception {
        Role role = m_userAdmin.createRole("user1", Role.USER);
        assertEquals("user1", role.getName());
        Role dup = m_userAdmin.createRole("user1", Role.USER);
        assertNull(dup);
    }

    public void testRemoveUser() throws Exception {
        // Write a new version directly to the repository
        SortedRangeSet range = m_repository.getRange();
        try (InputStream is = new ByteArrayInputStream("<roles><user name=\"user1\"><properties><test>changed</test></properties></user></roles>".getBytes())) {
            m_repository.commit(is, range.getHigh());
        }

        Role role = m_userAdmin.getRole("user1");
        assertEquals("user1", role.getName());

        boolean removeRole = m_userAdmin.removeRole("user1");
        assertTrue(removeRole);

        Role afterRemove = m_userAdmin.getRole("user1");
        assertNull(afterRemove);

        boolean removeRoleAgain = m_userAdmin.removeRole("user1");
        assertFalse(removeRoleAgain);
    }

    public void testUpdateUser() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role user1 = m_userAdmin.createRole("user1", Role.USER);

        high = waitForRepoChange(high);

        user1.getProperties().put("test", "property");
        waitForRepoChange(high);

        String repoContentsAsString = getRepoContentsAsString();
        assertTrue(repoContentsAsString.contains("<test>property</test>"));
    }

    public void testUpdateUserRepoOutOfSync() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role user1 = m_userAdmin.createRole("user1", Role.USER);
        high = waitForRepoChange(high);
        user1.getProperties().put("test", "property");
        waitForRepoChange(high);

        // Write a new version directly to the repository
        SortedRangeSet range = m_repository.getRange();
        try (InputStream is = new ByteArrayInputStream("<roles><user name=\"user1\"><properties><test>changed</test></properties></user></roles>".getBytes())) {
            m_repository.commit(is, range.getHigh());
        }

        try {
            // Expect that updating properties fails as the user object is out of date
            user1.getProperties().put("this", "fails");
            fail("Expected IllegalStateException");
        }
        catch (IllegalStateException e) {
            // expected
        }

        Role role = m_userAdmin.getRole("user1");
        assertEquals("changed", role.getProperties().get("test"));
    }

    protected void configureProvisionedServices() throws Exception {
        m_host = new URL("http://localhost:" + TestConstants.PORT);

        configureFactory("org.apache.ace.server.repository.factory",
            "customer", "apache",
            "name", "user",
            "master", "true",
            "initial", "<roles></roles>");

        configure("org.apache.ace.useradmin.repository",
            "repositoryLocation", "http://localhost:" + TestConstants.PORT + "/repository",
            "repositoryCustomer", "apache",
            "repositoryName", "user");

        configure("org.apache.ace.http.context", "authentication.enabled", "false");

        Utils.waitForWebserver(m_host);
    }

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(Repository.class, "(&(customer=apache)(name=user)(!(remote=*)))").setRequired(true))
        };
    }

    private String getRepoContentsAsString() throws IOException {
        String repoContentsAsString;
        try (InputStream repoInputStream = m_repository.checkout(m_repository.getRange().getHigh());
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = repoInputStream.read(buf)) >= 0) {
                out.write(buf, 0, bytesRead);
            }
            repoContentsAsString = out.toString();
        }
        return repoContentsAsString;
    }

    private long waitForRepoChange(long high) throws IOException, InterruptedException {
        int i = 0;
        while (m_repository.getRange().getHigh() <= high) {
            TimeUnit.MILLISECONDS.sleep(50L);
            i++;
            if (i > 25) {
                fail("Repo didn't update in time");
            }
        }
        return m_repository.getRange().getHigh();
    }
}
