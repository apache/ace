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

package org.apache.ace.client.rest.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * 
 */
public class WebResource {
    private final Client m_client;
    private final URI m_uri;

    protected WebResource(Client client, URI uri) {
        m_client = client;
        m_uri = uri;
    }

    public WebResponse delete() throws IOException {
        return delete("");
    }

    public WebResponse delete(String data) throws IOException {
        HttpURLConnection conn = openConnection();
        try {
            conn.setRequestMethod("DELETE");

            if (data.length() > 0) {
                conn.setFixedLengthStreamingMode(data.length());
                conn.setDoOutput(data.length() > 0);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes());
                    os.flush();
                }
            }
            return handleResponse(m_uri, conn);
        }
        finally {
            conn.disconnect();
        }
    }

    public String getString() throws IOException {
        HttpURLConnection conn = openConnection();
        try {
            conn.setRequestMethod("GET");
            
            return handleResponse(m_uri, conn).getContent();
        } finally {
            conn.disconnect();
        }
    }

    public URI getURI() {
        return m_uri;
    }

    public WebResource path(String path) {
        return new WebResource(m_client, m_uri.resolve(path));
    }

    public void post() throws IOException {
        post("");
    }

    public WebResponse post(String data) throws IOException {
        HttpURLConnection conn = openConnection();
        try {
            conn.setRequestMethod("POST");

            if (data.length() > 0) {
                conn.setFixedLengthStreamingMode(data.length());
                conn.setDoOutput(data.length() > 0);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes());
                    os.flush();
                }
            }
            return handleResponse(m_uri, conn);
        }
        finally {
            conn.disconnect();
        }
    }

    private String getContent(HttpURLConnection conn, String encoding) throws IOException {
        InputStream is;
        try {
            is = conn.getInputStream();
        }
        catch (IOException e) {
            is = conn.getErrorStream();
        }

        int len = conn.getContentLength();
        if (len < 0) {
            len = 4096;
        }

        try {
            byte[] buf = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
            int read = 0;
            while ((read = is.read(buf)) > 0) {
                baos.write(buf, 0, read);
            }
            return baos.toString(encoding);
        }
        finally {
            is.close();
        }
    }

    private WebResponse handleResponse(URI location, HttpURLConnection conn) throws IOException {
        int rc = conn.getResponseCode();
        String enc = conn.getContentEncoding();
        if (enc == null) {
            enc = m_client.getDefaultCharset();
        }

        if (rc >= 200 && rc < 300) {
            // Success
            return new WebResponse(location, rc, getContent(conn, enc));
        }
        else if (rc >= 300 && rc < 400) {
            // Redirection
            String newLocation = conn.getHeaderField("Location");
            throw new WebResourceException(new WebResponse(newLocation, rc, getContent(conn, enc)));
        }
        else if (rc >= 400 && rc < 600) {
            // Client or server error
            throw new WebResourceException(new WebResponse(location, rc, getContent(conn, enc)));
        }
        else {
            throw new IOException("Unknown/unhandled response code: " + rc);
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        URL url = m_uri.toURL();

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(m_client.isFollowRedirects());
        conn.setAllowUserInteraction(false);
        conn.setDefaultUseCaches(false);
        conn.setUseCaches(false);
        conn.setConnectTimeout(1000);
        conn.setReadTimeout(1000);

        return conn;
    }
}
