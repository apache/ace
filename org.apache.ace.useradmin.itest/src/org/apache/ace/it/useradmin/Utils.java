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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.ace.test.utils.NetUtils;

final class Utils {

    private static final int COPY_BUFFER_SIZE = 4096;
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";

    static void closeSilently(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (IOException exception) {
                // Ignore...
            }
        }
    }

    static void closeSilently(HttpURLConnection resource) {
        NetUtils.closeConnection(resource);
    }

    /* copy in to out */
    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }

    static void flushStream(InputStream is) {
        byte[] buf = new byte[COPY_BUFFER_SIZE];
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

    static int get(URL host, String endpoint, String customer, String name, String version, OutputStream out) throws IOException {
        int responseCode;

        URL url = new URL(host, endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);

        InputStream input = null;
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            responseCode = connection.getResponseCode();
            input = connection.getInputStream();

            copy(input, out);
            out.flush();
        }
        catch (IOException e) {
            responseCode = handleIOException(connection);
        }
        finally {
            closeSilently(input);
            closeSilently(connection);
        }

        return responseCode;
    }

    /**
     * @see http://docs.oracle.com/javase/6/docs/technotes/guides/net/http-keepalive.html
     */
    static int handleIOException(HttpURLConnection conn) {
        int respCode = -1;
        try {
            respCode = conn.getResponseCode();
            flushStream(conn.getErrorStream());
        }
        catch (IOException ex) {
            // deal with the exception
        }
        return respCode;
    }

    static int put(URL host, String endpoint, String customer, String name, String version, InputStream in) throws IOException {
        URL url = new URL(host, endpoint + "?customer=" + customer + "&name=" + name + "&version=" + version);

        int responseCode;
        HttpURLConnection connection = null;
        OutputStream out = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            // ACE-294: enable streaming mode causing only small amounts of memory to be
            // used for this commit. Otherwise, the entire input stream is cached into
            // memory prior to sending it to the server...
            connection.setChunkedStreamingMode(8192);
            connection.setRequestProperty("Content-Type", MIME_APPLICATION_OCTET_STREAM);
            out = connection.getOutputStream();

            copy(in, out);
            out.flush();

            responseCode = connection.getResponseCode();
            flushStream(connection.getInputStream());
        }
        catch (IOException e) {
            responseCode = handleIOException(connection);
        }
        finally {
            closeSilently(in);
            closeSilently(out);
            closeSilently(connection);
        }

        return responseCode;
    }

    static int query(URL host, String endpoint, String customer, String name, OutputStream out) throws IOException {
        String f1 = (customer == null) ? null : "customer=" + customer;
        String f2 = (name == null) ? null : "name=" + name;
        String filter = ((f1 == null) ? "?" : "?" + f1 + "&") + ((f2 == null) ? "" : f2);
        URL url = new URL(host, endpoint + filter);

        int responseCode;
        HttpURLConnection connection = null;
        InputStream input = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            responseCode = connection.getResponseCode();
            input = connection.getInputStream();

            copy(input, out);
            out.flush();
        }
        catch (IOException e) {
            responseCode = handleIOException(connection);
        }
        finally {
            closeSilently(input);
            closeSilently(out);
            closeSilently(connection);
        }

        return responseCode;
    }

    static void waitForWebserver(URL host) throws IOException {
        int retries = 1, rc = -1;
        IOException ioe = null;
        HttpURLConnection conn = null;
        while (retries++ < 10) {
            try {
                conn = (HttpURLConnection) host.openConnection();

                rc = conn.getResponseCode();
                if (rc >= 0) {
                    return;
                }
            }
            catch (ConnectException e) {
                ioe = e;
                try {
                    Thread.sleep(retries * 50);
                }
                catch (InterruptedException ie) {
                    // We're asked to stop...
                    return;
                }
            }
            catch (IOException e) {
                rc = handleIOException(conn);
            }
            finally {
                if (conn != null) {
                    conn.disconnect();
                }
                conn = null;
            }
        }
        if (ioe != null) {
            throw ioe;
        }
    }
}
