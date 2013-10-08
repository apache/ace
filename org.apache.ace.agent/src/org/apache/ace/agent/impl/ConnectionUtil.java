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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;

import org.apache.ace.agent.RetryAfterException;

/**
 * Common utility functions for components that work with server connections.
 */
class ConnectionUtil {
    /**
     * The HTTP header indicating the 'backoff' time to use. See section 14.37 of HTTP1.1 spec (RFC2616).
     */
    public static final String HTTP_RETRY_AFTER = "Retry-After";
    /**
     * Default backoff time, in seconds.
     */
    public static final int DEFAULT_RETRY_TIME = 30;

    /** Default buffer size for use in stream-copying, in bytes. */
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024;

    /**
     * Check the server response code and throws exceptions if it is not 200.
     * 
     * @param connection
     *            The connection to check
     * @throws RetryAfterException
     *             If the server response is 503 indicating it is not (yet) available. The backoff time (= minimum time
     *             to wait) is included in this exception;
     * @throws IOException
     *             If the server response is any other code than 200 or 503.
     */
    public static void checkConnectionResponse(URLConnection connection) throws RetryAfterException, IOException {
        int responseCode = getResponseCode(connection);
        switch (responseCode) {
            case 200:
            case 206:
                return;
            case 503:
                int retry = ((HttpURLConnection) connection).getHeaderFieldInt(HTTP_RETRY_AFTER, DEFAULT_RETRY_TIME);
                throw new RetryAfterException(retry);
            default:
                throw new IOException("Unable to handle server responsecode: " + responseCode + ", for " + connection.getURL());
        }
    }

    /**
     * Closes a given URL connection, if necessary.
     * 
     * @param connection
     *            the URL connection to close, can be <code>null</code> in which case this method does nothing.
     * @return always <code>null</code>, for easy chaining.
     */
    public static URLConnection close(URLConnection connection) {
        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).disconnect();
        }
        return null;
    }

    /**
     * @param closeable
     * @return always <code>null</code>, for easy chaining.
     */
    public static Closeable closeSilently(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        }
        catch (IOException exception) {
            // Ignore...
        }
        return null;
    }

    /**
     * @see http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
     */
    public static int handleIOException(URLConnection conn) {
        int respCode = -1;
        if (!(conn instanceof HttpURLConnection)) {
            return respCode;
        }

        try {
            respCode = ((HttpURLConnection) conn).getResponseCode();
            flushStream(((HttpURLConnection) conn).getErrorStream());
        }
        catch (IOException ex) {
            // deal with the exception
        }
        return respCode;
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        copy(is, os, DEFAULT_BUFFER_SIZE);
    }

    public static void copy(InputStream is, OutputStream os, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];

        int bytes;
        while ((bytes = is.read(buffer)) != -1) {
            os.write(buffer, 0, bytes);
        }
    }

    public static void skip(URLConnection connection, long count) {
        try {
            InputStream is = connection.getInputStream();
            while (count-- > 0) {
                is.read();
            }
        }
        catch (IOException ignored) {
            // Ignored...
        }
    }

    /**
     * Returns the response code for the given URL connection (assuming this connection represents a HTTP(S) URL).
     * 
     * @param connection
     *            the URL connection to get the response code for, can be <code>null</code>.
     * @return the response code for the given connection, or <code>-1</code> if it could not be determined.
     */
    private static int getResponseCode(URLConnection connection) {
        try {
            if (connection instanceof HttpURLConnection) {
                return ((HttpURLConnection) connection).getResponseCode();
            }
            return -1;
        }
        catch (IOException exception) {
            return handleIOException(connection);
        }
    }

    static void flushStream(InputStream is) {
        byte[] buf = new byte[4096];
        try {
            while (is.read(buf) > 0) {
                // Ignore...
            }
        }
        catch (IOException ex) {
            // deal with the exception
        }
        finally {
            closeSilently(is);
        }
    }

    private ConnectionUtil() {
        // Nop
    }
}
