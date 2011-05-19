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
package org.apache.ace.test.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Class containing utility methods concerning network related stuff.
 */
public class NetUtils {

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
    public static boolean waitForURL(URL url, int responseCode, int timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() == responseCode) {
                    return true;
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
}
