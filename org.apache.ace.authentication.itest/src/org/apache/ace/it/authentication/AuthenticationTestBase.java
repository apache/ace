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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.utils.NetUtils;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.useradmin.UserAdmin;

import junit.framework.Assert;

/**
 * Provides a common base class for all authentication integration tests.
 */
public abstract class AuthenticationTestBase extends IntegrationTestBase {

    protected static void printLog(LogReaderService logReader) {
        Enumeration<?> e = logReader.getLog();
        System.out.println("Log:");
        while (e.hasMoreElements()) {
            LogEntry entry = (LogEntry) e.nextElement();
            System.out.println(" * " + (new Date(entry.getTime())) + " - " + entry.getMessage() + " - " + entry.getBundle().getBundleId());
            if (entry.getException() != null) {
                entry.getException().printStackTrace();
            }
        }
    }

    /**
     * Imports a single user into the user repository.
     * 
     * @param userName
     *            the name of the user to import;
     * @param password
     *            the password of the user to import.
     * @throws Exception
     *             in case of exceptions during the import.
     */
    protected final void importSingleUser(Repository userRepository, String userName, String password) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(("<roles>" +
            "<user name=\"" + userName + "\">" +
            "<credentials><password>" + password + "</password></credentials>" +
            "</user>" +
            "</roles>").getBytes());

        Assert.assertTrue("Committing test user data failed!", userRepository.commit(bis, userRepository.getRange().getHigh()));
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means the specified status code is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @param responseCode
     *            The response code to be expected on the specified URL when it is available.
     * @param timeout
     *            Amount of milliseconds to keep trying to access the URL.
     * @return True if the response of the URL has the specified status code within the specified timeout delay, false
     *         otherwise.
     * @throws IOException
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    protected final boolean waitForURL(ConnectionFactory connectionFactory, URL url, int responseCode) throws IOException {
        URLConnection conn = null;

        int tries = 4;
        while (tries-- > 0) {
            conn = connectionFactory.createConnection(url);
            try {
                boolean result = ((HttpURLConnection) conn).getResponseCode() == responseCode;
                if (result) {
                    return true;
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(250);
                }
                catch (InterruptedException exception) {
                    return false;
                }
            }
            finally {
                NetUtils.closeConnection(conn);
            }
        }
        return false;
    }

    /**
     * Waits until a user with a given name appears in the user admin.
     * 
     * @param userAdmin
     *            the user admin to use;
     * @param userName
     *            the name of the user to wait for.
     * @throws Exception
     *             in case of exceptions during the wait.
     */
    protected final void waitForUser(UserAdmin userAdmin, String userName) throws Exception {
        int count = 0;
        while ((userAdmin.getRole(userName) == null) && (++count < 60)) {
            Thread.sleep(100);
        }
        Assert.assertTrue("Failed to obtain user from userAdmin!", count != 60);
    }
}
