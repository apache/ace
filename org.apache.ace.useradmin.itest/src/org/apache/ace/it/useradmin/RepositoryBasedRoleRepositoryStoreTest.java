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
import java.net.URL;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.UserAdmin;

public class RepositoryBasedRoleRepositoryStoreTest extends IntegrationTestBase {

    private URL m_host;

    private volatile UserAdmin m_userAdmin;
    private volatile Repository m_repository;

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(Repository.class, "(&(customer=apache)(name=user))").setRequired(true))
        };
    }
    
    public void testAddUser() throws Exception {
        long high = m_repository.getRange().getHigh();
        m_userAdmin.createRole("Piet", Role.USER);
        waitForRepoChange(high);
        
        String repoContentsAsString = getRepoContentsAsString();
        assertTrue(repoContentsAsString.contains("<user name=\"Piet\">"));
    }
    
    public void testDuplicateAddUser() throws Exception {
        Role role = m_userAdmin.createRole("Piet", Role.USER);
        assertEquals("Piet", role.getName());
        Role dup = m_userAdmin.createRole("Piet", Role.USER);
        assertNull(dup);
    }
    
    public void testRemoveUser() throws Exception {
        // Write a new version directly to the repository
        SortedRangeSet range = m_repository.getRange();
        try (InputStream is = new ByteArrayInputStream("<roles><user name=\"Piet\"><properties><test>changed</test></properties></user></roles>".getBytes())){
            m_repository.commit(is, range.getHigh());
        }
        
        Role role = m_userAdmin.getRole("Piet");
        assertEquals("Piet", role.getName());
        
        boolean removeRole = m_userAdmin.removeRole("Piet");
        assertTrue(removeRole);
        
        Role afterRemove = m_userAdmin.getRole("Piet");
        assertNull(afterRemove);
        
        boolean removeRoleAgain = m_userAdmin.removeRole("Piet");
        assertFalse(removeRoleAgain);
    }
    
    public void testUpdateUser() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role piet = m_userAdmin.createRole("Piet", Role.USER);

        high = waitForRepoChange(high);
        
        piet.getProperties().put("test", "property");
        waitForRepoChange(high);
        
        String repoContentsAsString = getRepoContentsAsString();
        assertTrue(repoContentsAsString.contains("<test>property</test>"));
    }
    
    public void testUpdateUserRepoOutOfSync() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role piet = m_userAdmin.createRole("Piet", Role.USER);
        high = waitForRepoChange(high);
        piet.getProperties().put("test", "property");
        waitForRepoChange(high);
        
        // Write a new version directly to the repository
        SortedRangeSet range = m_repository.getRange();
        try (InputStream is = new ByteArrayInputStream("<roles><user name=\"Piet\"><properties><test>changed</test></properties></user></roles>".getBytes())){
            m_repository.commit(is, range.getHigh());
        }
        
        try {
            // Expect that updating properties fails as the user object is out of date
            piet.getProperties().put("this", "fails");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            //expected
        }
        
        Role role = m_userAdmin.getRole("Piet");
        assertEquals("changed", role.getProperties().get("test"));
    }
    
    public void testUpdateUserFetchedBeforeRepoSync() throws Exception {
        long high = m_repository.getRange().getHigh();
        Role piet = m_userAdmin.createRole("Piet", Role.USER);
        high = waitForRepoChange(high);
        piet.getProperties().put("test", "property");
        high = waitForRepoChange(high);
        
        // Write a new version directly to the repository
        SortedRangeSet range = m_repository.getRange();
        try (InputStream is = new ByteArrayInputStream("<roles><user name=\"Piet\"><properties><test>changed</test></properties></user></roles>".getBytes())){
            m_repository.commit(is, range.getHigh());
        }
        
        // try to get a Role just to trigger the RepositoryBasedRoleRepositoryStore to refresh from the repository 
        m_userAdmin.getRole("JustATrigger"); 
        
        try {
            // Expect that updating properties fails as the user was fetched before the repository was refreshed
            piet.getProperties().put("this", "fails");
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
//            expected
        }
        
        Role role = m_userAdmin.getRole("Piet");
        assertEquals("changed", role.getProperties().get("test"));
    }

    private String getRepoContentsAsString() throws IOException {
        String repoContentsAsString;
        try(InputStream repoInputStream = m_repository.checkout(m_repository.getRange().getHigh());
                ByteArrayOutputStream out = new ByteArrayOutputStream()){
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
            Thread.sleep(10l);
            i++;
            if (i > 250){
                fail("Repo didn't update in time");
            }
        } 
        return m_repository.getRange().getHigh();
    }
   
    protected void configureProvisionedServices() throws Exception {
        m_host = new URL("http://localhost:" + TestConstants.PORT);

        configure("org.apache.ace.repository.servlet.RepositoryServlet",
            HttpConstants.ENDPOINT, "/repository", "authentication.enabled", "false");
        
        configureFactory("org.apache.ace.server.repository.factory", 
            "customer", "apache",
            "name", "user", 
            "master", "true",
            "initial", "<roles></roles>"
            );
        
        configure("org.apache.ace.useradmin.repository",
            "repositoryLocation", "http://localhost:" + TestConstants.PORT + "/repository",
            "repositoryCustomer", "apache",
            "repositoryName", "user");

        Utils.waitForWebserver(m_host);
    }

    @Override
    protected void doTearDown() throws Exception {
    }

}
