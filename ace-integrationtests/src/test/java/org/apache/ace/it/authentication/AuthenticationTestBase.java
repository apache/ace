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

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Provides a common base class for all authentication integration tests.
 */
public class AuthenticationTestBase extends IntegrationTestBase {

    /**
     * Imports a single user into the user repository.
     * 
     * @param userName the name of the user to import;
     * @param password the password of the user to import.
     * @throws Exception in case of exceptions during the import.
     */
    protected final void importSingleUser(Repository userRepository, String userName, String password) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream((
            "<roles>" +
                "<user name=\"" + userName + "\">" +
                "<properties><username>" + userName + "</username></properties>" +
                "<credentials><password type=\"String\">" + password + "</password></credentials>" +
                "</user>" +
            "</roles>").getBytes());

        assertTrue("Committing test user data failed!", userRepository.commit(bis, userRepository.getRange().getHigh()));
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means the specified status code is returned when accessing the URL.
     *
     * @param url HTTP URL that should be tested for availability.
     * @param responseCode The response code to be expected on the specified URL when it is available.
     * @param timeout Amount of milliseconds to keep trying to access the URL.
     * @return True if the response of the URL has the specified status code within the specified timeout delay, false otherwise.
     * @throws IllegalArgumentException If the specified URL does not use the HTTP protocol.
     */
    protected final boolean waitForURL(ConnectionFactory connectionFactory, URL url, int responseCode, int timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            try {
                URLConnection connection = connectionFactory.createConnection(url);

                connection.connect();

                if (connection instanceof HttpURLConnection) {
                    int respCode = ((HttpURLConnection) connection).getResponseCode();
                    if (respCode == responseCode) {
                        return true;
                    }
                    else {
                        System.err.println("Got response code " + respCode + " for " + url);
                    }
                }
            } catch (ClassCastException cce) {
                throw new IllegalArgumentException("Expected url to be an HTTP url, not: " + url.toString(), cce);
            }
            catch (IOException ioe) {
                // retry
            }
            try {
                Thread.sleep(100);
            }
            catch (InterruptedException ie) {
                return false;
            }
        }
        return false;
    }

    /**
     * Waits until a user with a given name appears in the user admin.
     * 
     * @param userAdmin the user admin to use;
     * @param userName the name of the user to wait for.
     * @throws Exception in case of exceptions during the wait.
     */
    protected final void waitForUser(UserAdmin userAdmin, String userName) throws Exception {
        int count = 0;
        while ((userAdmin.getRole(userName) == null) && (count++ < 60)) {
            Thread.sleep(100);
        }
        assertTrue("Failed to obtain user from userAdmin!", count != 60);
    }
}
