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
package org.apache.ace.repository.ext.impl;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * This class works as a local interface for a remote repository by handling the network communication.
 */
@ConsumerType
public class RemoteRepository implements Repository {
    private static final String COMMAND_QUERY = "/query";
    private static final String COMMAND_CHECKOUT = "/checkout";
    private static final String COMMAND_COMMIT = "/commit";

    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";

    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final URL m_url;
    private final String m_customer;
    private final String m_name;

    private volatile ConnectionFactory m_connectionFactory;

    /**
     * Creates a remote repository that connects to a given location with a given customer- and repository name.
     * 
     * @param url
     *            The location of the repository.
     * @param customer
     *            The customer name to use.
     * @param name
     *            The repository name to use.
     */
    public RemoteRepository(URL url, String customer, String name) {
        if (url == null || customer == null || name == null) {
            throw new IllegalArgumentException("None of the parameters can be null!");
        }

        m_url = url;
        m_customer = customer;
        m_name = name;
    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }

        URL url = buildCommand(m_url, COMMAND_CHECKOUT, version);

        HttpURLConnection connection = (HttpURLConnection) m_connectionFactory.createConnection(url);

        int rc = connection.getResponseCode();
        if (rc == HttpServletResponse.SC_NOT_FOUND) {
            closeQuietly(connection);
            throw new IllegalArgumentException("Requested version not found in remote repository. (" + connection.getResponseMessage() + ") for " + url.toExternalForm());
        }
        else if (rc != HttpServletResponse.SC_OK) {
            closeQuietly(connection);
            throw new IOException("Connection error: " + connection.getResponseMessage() + " for " + url.toExternalForm());
        }

        return connection.getInputStream();
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        URL url = buildCommand(m_url, COMMAND_COMMIT, fromVersion);
        HttpURLConnection connection = (HttpURLConnection) m_connectionFactory.createConnection(url);

        // ACE-294: enable streaming mode causing only small amounts of memory to be
        // used for this commit. Otherwise, the entire input stream is cached into
        // memory prior to sending it to the server...
        connection.setChunkedStreamingMode(8192);
        connection.setRequestProperty("Content-Type", MIME_APPLICATION_OCTET_STREAM);
        connection.setDoOutput(true);

        OutputStream out = connection.getOutputStream();
        try {
            copy(data, out);
        }
        finally {
            closeQuietly(out);
        }
        try {
            // causes the stream the be flushed and the server response to be obtained...
            switch (connection.getResponseCode()) {
                case HttpServletResponse.SC_OK:
                    return true;
                case HttpServletResponse.SC_NOT_MODIFIED:
                    return false;
                case HttpServletResponse.SC_BAD_REQUEST: 
                    throw new IllegalArgumentException(connection.getResponseMessage());
                case HttpServletResponse.SC_NOT_ACCEPTABLE:
                    throw new IllegalStateException(connection.getResponseMessage());
                case HttpServletResponse.SC_INTERNAL_SERVER_ERROR:
                default:
                    throw new IOException(connection.getResponseMessage());
            }
        }
        finally {
            closeQuietly(connection);
        }
    }

    public SortedRangeSet getRange() throws IOException {
        URL url = buildCommand(m_url, COMMAND_QUERY, 0);

        HttpURLConnection connection = (HttpURLConnection) m_connectionFactory.createConnection(url);

        try {
            if (connection.getResponseCode() == HttpServletResponse.SC_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        throw new IOException("Repository not found: customer=" + m_customer + ", name=" + m_name + " for " + url.toExternalForm());
                    }

                    String representation = line.substring(line.lastIndexOf(','));
                    return new SortedRangeSet(representation);
                }
                finally {
                    reader.close();
                }
            }

            throw new IOException("Connection error: " + connection.getResponseMessage() + " for " + url.toExternalForm());
        }
        finally {
            closeQuietly(connection);
        }
    }

    /**
     * Helper method which copies the contents of an input stream to an output stream.
     * 
     * @param in
     *            The input stream.
     * @param out
     *            The output stream.
     * @throws java.io.IOException
     *             Thrown when one of the streams is closed unexpectedly.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }
    }

    /**
     * Builds a command string to use in the request to the server, based on the parameters this object was created
     * with. The version is only mandatory for <code>CHECKOUT</code> and <code>COMMIT</code>.
     * 
     * @param command
     *            A command string, use the <code>COMMAND_</code> constants in this file.
     * @param version
     *            A version statement.
     * @return The command string.
     */
    private URL buildCommand(URL url, String command, long version) {
        StringBuilder params = new StringBuilder();

        if (m_customer != null) {
            if (params.length() != 0) {
                params.append("&");
            }
            params.append("customer=").append(m_customer);
        }
        if (m_name != null) {
            if (params.length() != 0) {
                params.append("&");
            }
            params.append("name=").append(m_name);
        }
        if (command != COMMAND_QUERY) {
            if (params.length() != 0) {
                params.append("&");
            }
            params.append("version=").append(version);
        }

        StringBuilder newURL = new StringBuilder();
        newURL.append(url.toExternalForm());
        newURL.append(command);
        if (params.length() > 0) {
            newURL.append("?").append(params);
        }

        try {
            return new URL(newURL.toString());
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not create URL: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RemoteRepository[" + m_url + "," + m_customer + "," + m_name + "]";
    }

    /**
     * Safely closes a given HTTP URL connection.
     * 
     * @param resource
     *            the resource to close, can be <code>null</code>.
     */
    private void closeQuietly(HttpURLConnection resource) {
        if (resource != null) {
            resource.disconnect();
        }
    }

    /**
     * Safely closes a given resource, ignoring any I/O exceptions that might occur by this.
     * 
     * @param resource
     *            the resource to close, can be <code>null</code>.
     */
    private void closeQuietly(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IOException e) {
            // Ignored...
        }
    }
}
