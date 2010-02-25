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
package org.apache.ace.test.useradminconfigurator;

import static org.apache.ace.test.utils.TestUtils.INTEGRATION;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.ace.repository.Repository;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class ConfiguratorTest {

    public static Object m_instance;

    private volatile Repository m_repository;
    private volatile UserAdmin m_userAdmin;

    public ConfiguratorTest() {
        synchronized (ConfiguratorTest.class) {
            if (m_instance == null) {
                m_instance = this;
            }
        }
    }

    @Factory
    public Object[] createInstances() {
        synchronized (ConfiguratorTest.class) {
            return new Object[] { m_instance };
        }
    }

    /**
     * Creates a file in the repository, and waits for the UserAdmin to have a new user
     * present, and inspects that user.
     */
    @Test(groups = { INTEGRATION })
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

        assert m_repository.commit(bis, m_repository.getRange().getHigh()) : "Committing test user data failed.";

        User user = (User) m_userAdmin.getRole("TestUser");
        int count = 0;
        while ((user == null) && (count < 60)) {
            Thread.sleep(250);
            user = (User) m_userAdmin.getRole("TestUser");
            count++;
        }
        if (user == null) {
            assert false : "Even after fifteen seconds, our user is not present.";
        }

        boolean foundPassword = false;
        boolean foundCertificate = false;
        count = 0;
        while (!foundPassword & !foundCertificate && (count < 20)) {
            // Note: there is a window between the creation of the user and the setting of the properties.
            Thread.sleep(50);
            foundPassword = user.hasCredential("password", "swordfish");
            foundCertificate = user.hasCredential("certificate", new byte[] {'4', '2'});
        }

        assert foundPassword : "A second after our user becoming available, there is no password.";
        assert foundCertificate : "A second after our user becoming available, there is no certificate.";
    }
}
