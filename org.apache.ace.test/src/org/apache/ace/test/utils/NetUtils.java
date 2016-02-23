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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

/**
 * Class containing utility methods concerning network related stuff.
 */
public class NetUtils {
    public static final int DEFAULT_WAIT_TIMEOUT = 2500;

    /**
     * Closes the given URL connection and ensures that its state is properly flushed.
     * 
     * @param connection
     *            the URL connection to close, may be <code>null</code>.
     */
    public static void closeConnection(URLConnection connection) {
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection conn = (HttpURLConnection) connection;
            try {
                if (conn.getErrorStream() != null) {
                    flush(conn.getErrorStream());
                }
                if (conn.getInputStream() != null) {
                    flush(conn.getInputStream());
                }
            }
            catch (IOException exception) {
                // Ignore... Not much we can do about this here...
            }
            finally {
                conn.disconnect();
            }
        }
    }

    /**
     * Flushes the given input stream by reading its contents until an end-of-file marker is found.
     * 
     * @param is
     *            the input stream to flush, cannot be <code>null</code>.
     * @throws IOException
     *             in case of I/O problems reading from the given input stream.
     */
    public static void flush(InputStream is) throws IOException {
        final byte[] buf = new byte[4096];
        int read = 0;
        do {
            read = is.read(buf);
        }
        while (read > 0);
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means a status code of "200" is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @return <code>true</code> if the response of the URL has the specified status code within the specified timeout
     *         delay, <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(String url) throws MalformedURLException {
        return waitForURL(new URL(url));
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means the specified status code is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @param responseCode
     *            The response code to be expected on the specified URL when it is available.
     * @return True if the response of the URL has the specified status code within the specified timeout delay, false
     *         otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(String url, int responseCode) throws MalformedURLException {
        return waitForURL(new URL(url), responseCode);
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
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(String url, int responseCode, int timeout) throws MalformedURLException {
        return waitForURL(new URL(url), responseCode, timeout);
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means a status code of "200" is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @return <code>true</code> if the response of the URL has the specified status code within the specified timeout
     *         delay, <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(URL url) {
        return waitForURL(url, HttpURLConnection.HTTP_OK, DEFAULT_WAIT_TIMEOUT);
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means the specified status code is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @param responseCode
     *            The response code to be expected on the specified URL when it is available.
     * @return True if the response of the URL has the specified status code within the specified timeout delay, false
     *         otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(URL url, int responseCode) {
        return waitForURL(url, responseCode, DEFAULT_WAIT_TIMEOUT);
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
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL(URL url, int responseCode, int timeout) {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            URLConnection connection = null;
            try {
                connection = url.openConnection();
                ((HttpURLConnection) connection).setRequestMethod("HEAD");
                connection.setAllowUserInteraction(false);
                connection.setUseCaches(false);

                int rc = ((HttpURLConnection) connection).getResponseCode();
                if (rc == responseCode) {
                    return true;
                }
                System.out.printf("Waiting for URL %s: %d (want %d)%n", connection.getURL(), rc, responseCode);
            }
            catch (ClassCastException cce) {
                throw new IllegalArgumentException("Expected url to be an HTTP url, not: " + connection.getURL(), cce);
            }
            catch (IOException ioe) {
                // retry
            }
            finally {
                closeConnection(connection);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(250);
            }
            catch (InterruptedException ie) {
                return false;
            }
        }
        return false;
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means a status code of 404 is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @return <code>true</code> if the response of the URL has the specified status code within the specified timeout
     *         delay, <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL_NotFound(String url) throws MalformedURLException {
        return waitForURL_NotFound(new URL(url));
    }

    /**
     * Waits for a HTTP URL to become 'available', will retry every 100 milliseconds until it is available or timeout
     * has been exceeded. Available in this context means a status code of 404 is returned when accessing the URL.
     * 
     * @param url
     *            HTTP URL that should be tested for availability.
     * @return <code>true</code> if the response of the URL has the specified status code within the specified timeout
     *         delay, <code>false</code> otherwise.
     * @throws IllegalArgumentException
     *             If the specified URL does not use the HTTP protocol.
     */
    public static boolean waitForURL_NotFound(URL url) {
        return waitForURL(url, HttpURLConnection.HTTP_NOT_FOUND, DEFAULT_WAIT_TIMEOUT);
    }
}
