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
package org.apache.ace.agent.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

import org.apache.ace.agent.RetryAfterException;

/**
 * Common utility functions for components that work with server connections.
 */
public class ConnectionUtil {

    /**
     * Check the server response code and throws exceptions if it is not 200.
     * 
     * @param connection The connection to check
     * @throws RetryAfterException If the server response is 503
     * @throws IOException If the server response is other
     */
    public static void checkConnectionResponse(URLConnection connection) throws RetryAfterException, IOException {

        if (connection instanceof HttpURLConnection) {
            int responseCode = ((HttpURLConnection) connection).getResponseCode();
            switch (responseCode) {
                case 200:
                    return;
                case 503:
                    int retry = 30;
                    String header = ((HttpURLConnection) connection).getHeaderField("Retry-After");
                    if (header != null) {
                        try {
                            retry = Integer.parseInt(header);
                        }
                        catch (NumberFormatException e) {
                        }
                    }
                    throw new RetryAfterException(retry);
                default:
                    throw new IOException("Unable to handle server responsecode: " + responseCode);
            }
        }
    }

    private ConnectionUtil() {

    }
}
