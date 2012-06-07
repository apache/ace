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

import static org.apache.ace.it.Options.jetty;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.provision;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.it.Options.Ace;
import org.apache.ace.it.Options.Felix;
import org.apache.ace.it.Options.Knopflerfish;
import org.apache.ace.it.Options.Osgi;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.impl.constants.RepositoryConstants;
import org.apache.ace.test.constants.TestConstants;
import org.apache.felix.dm.Component;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.WrappedUrlProvisionOption;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

@RunWith(JUnit4TestRunner.class)
public class ConfiguratorTest extends IntegrationTestBase {

    @Configuration
    public Option[] configuration() {
        return options(
            systemProperty("org.osgi.service.http.port").value("" + TestConstants.PORT),
            junitBundles(),
            provision(
                wrappedBundle(maven("org.apache.ace", "org.apache.ace.util")).overwriteManifest(WrappedUrlProvisionOption.OverwriteMode.FULL), // we do this because we need access to some test classes that aren't exported
                Osgi.compendium(),
                jetty(),
                Felix.preferences(),
                Felix.dependencyManager(),
                Felix.configAdmin(),
                Knopflerfish.useradmin(),
                Knopflerfish.log(),
                Ace.authenticationApi(),
                Ace.connectionFactory(),
                Ace.rangeApi(),
                Ace.scheduler(),
                Ace.httplistener(),
                Ace.repositoryApi(),
                Ace.repositoryImpl(),
                Ace.repositoryServlet(),
                Ace.resourceprocessorUseradmin(),
                Ace.configuratorUseradminTask(),
                Ace.deploymentProviderApi()
            )
        );
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

    protected void before() throws IOException {
        configureFactory("org.apache.ace.server.repository.factory",
                RepositoryConstants.REPOSITORY_NAME, "users",
                RepositoryConstants.REPOSITORY_CUSTOMER, "apache",
                RepositoryConstants.REPOSITORY_MASTER, "true");
        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
                "repositoryName", "users",
                "repositoryCustomer", "apache");
        configure("org.apache.ace.scheduler",
                "org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask", "1000");
    }

    private volatile Repository m_repository;
    private volatile UserAdmin m_userAdmin;

    /**
     * Creates a file in the repository, waits for the UserAdmin to have a new user
     * present, and inspects that user.
     */
    @Test
    public void configuratorTest() throws IllegalArgumentException, IOException, InterruptedException {
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
}
