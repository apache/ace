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
package org.apache.ace.bnd.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.apache.ace.connectionfactory.ConnectionFactory;

import aQute.bnd.deployer.repository.FixedIndexedRepo;

/**
 * BND repository implementation that supports write capabilities to an Apache ACE OBR.
 * 
 */
public class AceObrRepository extends FixedIndexedRepo {

    private URL m_endpoint;
    private boolean m_verbose;

    @Override
    public synchronized void setProperties(Map<String, String> map) {
        super.setProperties(map);

        String location = getLocation();
        try {
            m_endpoint = new URL(location.substring(0, location.lastIndexOf("/") + 1));
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Failed to determin location endpoint", e);
        }
        m_verbose = map.get("verbose") == null ? false : Boolean.parseBoolean(map.get("verbose"));
    }

    @Override
    public boolean canWrite() {
        return true;
    }

    @Override
    public synchronized PutResult put(InputStream stream, PutOptions options) throws Exception {

        if (options == null)
            options = DEFAULTOPTIONS;

        if (options.type == null)
            options.type = PutOptions.BUNDLE;

        if (stream == null)
            throw new IllegalArgumentException("No stream and/or options specified");

        PutResult result = new PutResult();
        result.artifact = upload(stream, "", options.type);

        reset();
        return result;
    }

    public URL getEndpoint() {
        return m_endpoint;
    }

    public URI upload(InputStream stream, String filename, String mimetype) throws Exception {

        OutputStream output = null;
        String location = null;
        try {

            URL url = new URL(m_endpoint, "?filename=" + filename);
            
            URLConnection connection = null;
            if (registry != null) {
                ConnectionFactory connectionFactory = registry.getPlugin(ConnectionFactory.class);
                if (connectionFactory != null) {
                    connection = connectionFactory.createConnection(url);
                }
            }
            
            if (connection == null) {
                connection = url.openConnection();
            }
            
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);

            if (mimetype != null) {
                connection.setRequestProperty("Content-Type", mimetype);
            }
            else {
                // We need a mimetype or Jetty will throw a 500 Form too large

                connection.setRequestProperty("Content-Type", "application/octet-stream");
            }

            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).setChunkedStreamingMode(8192);
            }

            int size = 0;
            output = connection.getOutputStream();
            byte[] buffer = new byte[4 * 1024];
            for (int count = stream.read(buffer); count != -1; count = stream.read(buffer)) {
                output.write(buffer, 0, count);
                size += count;
                if (m_verbose)
                    System.out.println("Uploaded bytes... " + size);
            }
            output.close();

            if (connection instanceof HttpURLConnection) {
                int responseCode = ((HttpURLConnection) connection).getResponseCode();
                String responseMessage = ((HttpURLConnection) connection).getResponseMessage();
                switch (responseCode) {
                    case HttpURLConnection.HTTP_CREATED:
                        location = connection.getHeaderField("Location");
                        break;
                    case HttpURLConnection.HTTP_CONFLICT:
                        throw new IOException("Resource already exists: " + responseMessage);
                    case HttpURLConnection.HTTP_INTERNAL_ERROR:
                        throw new IOException("Internal server error: " + responseMessage);
                    default:
                        throw new IOException("Unexpected server response: " + responseMessage);
                }
            }
        }
        catch (IOException e) {
            throw new IOException("Error importing resource: " + e.getMessage(), e);
        }
        finally {
            if (output != null) {
                try {
                    output.close();
                }
                catch (Exception ex) {
                }
            }
        }
        return new URI(location);
    }
}
