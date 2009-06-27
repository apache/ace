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
package org.apache.ace.repository.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.SortedRangeSet;

/**
 * This class works as a local interface for a remote repository by handling the network
 * communication.
 */
public class RemoteRepository implements Repository {
    private static final String COMMAND_QUERY = "/query";
    private static final String COMMAND_CHECKOUT = "/checkout";
    private static final String COMMAND_COMMIT = "/commit";
    private static final String MIME_APPLICATION_OCTET_STREAM = "application/octet-stream";
    private static final int COPY_BUFFER_SIZE = 4096;

    private final URL m_url;
    private final String m_customer;
    private final String m_name;
    private final String m_filter;


    RemoteRepository(URL url, String customer, String name, String filter) {
        m_url = url;
        m_customer = customer;
        m_name = name;
        m_filter = filter;
    }

    /**
     * Creates a remote repository that connects to a given location with a given customer-
     * and repository name.
     * @param url The location of the repository.
     * @param customer The customer name to use.
     * @param name The repository name to use.
     */
    public RemoteRepository(URL url, String customer, String name) {
        this(url, customer, name, null);
    }

    /**
     * Creates a remote repository that connects to a given location with a given filter.
     * @param url The location of the repository.
     * @param filter An LDAP filter string to select the repository.
     */
    public RemoteRepository(URL url, String filter) {
        this(url, null, null, filter);
    }


    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }

        URL url = buildCommand(m_url, COMMAND_CHECKOUT, version);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpServletResponse.SC_NOT_FOUND) {
            throw new IllegalArgumentException("Requested version not found in remote repository. (" + connection.getResponseMessage() + ")");
        }
        if (connection.getResponseCode() != HttpServletResponse.SC_OK) {
            throw new IOException("Connection error: " + connection.getResponseMessage());
        }

        return connection.getInputStream();
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        URL url = buildCommand(m_url, COMMAND_COMMIT, fromVersion);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", MIME_APPLICATION_OCTET_STREAM);

        OutputStream out = connection.getOutputStream();
        copy(data, out);
        out.flush();
        out.close();
        return connection.getResponseCode() == HttpServletResponse.SC_OK;
    }

    public SortedRangeSet getRange() throws IOException {
        URL url = buildCommand(m_url, COMMAND_QUERY, 0);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection.getResponseCode() == HttpServletResponse.SC_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line = reader.readLine();
            if (line == null) {
                throw new IOException("Repository not found: customer=" + m_customer + ", name=" + m_name);
            }
            String representation = line.substring(line.lastIndexOf(','));
            reader.close();
            return new SortedRangeSet(representation);
        }
        throw new IOException("Connection error: " + connection.getResponseMessage());
    }

    /**
     * Helper method which copies the contents of an input stream to an output stream.
     * @param in The input stream.
     * @param out The output stream.
     * @throws IOException Thrown when one of the streams is closed unexpectedly.
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
     * Builds a command string to use in the request to the server, based on the parameters
     * this object was created with. The version is only mandatory for <code>CHECKOUT</code>
     * and <code>COMMIT</code>.
     * @param command A command string, use the <code>COMMAND_</code> constants in this file.
     * @param version A version statement.
     * @return The command string.
     */
    private URL buildCommand(URL url, String command, long version) {
        StringBuffer result = new StringBuffer();

        if (m_filter != null) {
            result.append(m_filter);
        }
        else {

            if (m_customer != null) {
                if (result.length() != 0) {
                    result.append("&");
                }
                result.append("customer=").append(m_customer);
            }
            if (m_name != null) {
                if (result.length() != 0) {
                    result.append("&");
                }
                result.append("name=").append(m_name);
            }
            if (command != COMMAND_QUERY) {
                if (result.length() != 0) {
                    result.append("&");
                }
                result.append("version=").append(version);
            }
        }

        try {
            if (result.length() > 0) {
                return new URL(url.toString() + command + "?" + result.toString());
            }
            else {
                return new URL(url.toString() + command);
            }
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not create URL: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RemoteRepository[" + m_url + "," + m_customer + "," + m_name + "," + m_filter + "]";
    }
}
