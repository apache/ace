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
package org.apache.ace.it.useradminconfigurator;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.felix.dm.Component;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

public class ConfiguratorTest extends IntegrationTestBase {

    private volatile Repository m_repository;
    private volatile UserAdmin m_userAdmin;

    /**
     * Creates a file in the repository, waits for the UserAdmin to have a new user
     * present, and inspects that user.
     */
    public void testConfigurator() throws IllegalArgumentException, IOException, InterruptedException {
        ByteArrayInputStream bis = new ByteArrayInputStream((
            "<roles>" +
            "    <user name=\"TestUser\">" +
            "    <properties>" +
            "        <email>testUser@apache.org</email>" +
            "    </properties>" +
            "    <credentials>" +
            "        <password type=\"String\">swordfish</password>" +
            "        <certificate type=\"byte[]\">42</certificate>" +
            "    </credentials>" +
            "    </user>" +
            "</roles>").getBytes());

        assertTrue("Committing test user data failed.", m_repository.commit(bis, m_repository.getRange().getHigh()));

        User user = (User) m_userAdmin.getRole("TestUser");
        int count = 0;
        while ((user == null) && (count < 60)) {
            Thread.sleep(250);
            user = (User) m_userAdmin.getRole("TestUser");
            count++;
        }

        assertNotNull("Even after fifteen seconds, our user is not present.", user);

        boolean foundPassword = false;
        boolean foundCertificate = false;
        count = 0;
        while (!foundPassword & !foundCertificate && (count < 20)) {
            // Note: there is a window between the creation of the user and the setting of the properties.
            Thread.sleep(50);
            foundPassword = user.hasCredential("password", "swordfish");
            foundCertificate = user.hasCredential("certificate", new byte[] {'4', '2'});
        }

        assertTrue("A second after our user becoming available, there is no (correct) password.", foundPassword);
        assertTrue("A second after our user becoming available, there is no (correct) certificate.", foundCertificate);
    }

    @Override
	protected void before() throws Exception {
        configureFactory("org.apache.ace.server.repository.factory",
                RepositoryConstants.REPOSITORY_NAME, "users",
                RepositoryConstants.REPOSITORY_CUSTOMER, "apache",
                RepositoryConstants.REPOSITORY_MASTER, "true");
        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
                "repositoryName", "users",
                "repositoryCustomer", "apache");
        configure("org.apache.ace.scheduler",
                "org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", "1000");
        configure("org.apache.ace.repository.servlet.RepositoryServlet",
        		"org.apache.ace.server.servlet.endpoint", "/repository",
        		"authentication.enabled", "false");
        configure("org.apache.ace.repository.servlet.RepositoryReplicationServlet",
        		"org.apache.ace.server.servlet.endpoint", "/replication",
        		"authentication.enabled", "false");
    }

    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency()
                    .setService(UserAdmin.class)
                    .setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=apache))")
                    .setRequired(true))
        };
    }
}
