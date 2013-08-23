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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;

public class UpdateHandlerBase extends ComponentBase {

    public UpdateHandlerBase(String componentIdentifier) {
        super(componentIdentifier);
    }

    protected SortedSet<Version> getAvailableVersions(URL endpoint) throws RetryAfterException, IOException {
        SortedSet<Version> versions = new TreeSet<Version>();
        URLConnection connection = null;
        BufferedReader reader = null;
        try {
            connection = getConnection(endpoint);
            // TODO handle problems and retries
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String versionString;
            while ((versionString = reader.readLine()) != null) {
                try {
                    Version version = Version.parseVersion(versionString);
                    versions.add(version);
                }
                catch (IllegalArgumentException e) {
                    throw new IOException(e);
                }
            }
            return versions;
        }
        finally {
            if (connection != null && connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    protected long getPackageSize(URL url) throws RetryAfterException, IOException {
        long packageSize = -1l;
        URLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection) {
                ((HttpURLConnection) urlConnection).setRequestMethod("HEAD");
            }

            String dpSizeHeader = urlConnection.getHeaderField(AgentConstants.HEADER_DPSIZE);
            if (dpSizeHeader != null) {
                try {
                    packageSize = Long.parseLong(dpSizeHeader);
                }
                catch (NumberFormatException e) {
                    // ignore
                }
            }
            return packageSize;
        }
        finally {
            if (urlConnection != null && urlConnection instanceof HttpURLConnection) {
                ((HttpURLConnection) urlConnection).disconnect();
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    protected InputStream getInputStream(URL packageURL) throws RetryAfterException, IOException {
        URLConnection urlConnection = null;
        InputStream inputStream = null;
        // TODO handle problems and retries
        urlConnection = getConnection(packageURL);
        inputStream = urlConnection.getInputStream();
        return inputStream;
    }

    protected DownloadHandle getDownloadHandle(URL packageURL) {
        return getAgentContext().getDownloadHandler().getHandle(packageURL);
    }

    protected String getIdentification() {
        return getAgentContext().getIdentificationHandler().getAgentId();
    }

    protected URL getServerURL() throws RetryAfterException {
        // FIXME not sure if this is the proper place
        URL serverURL = getAgentContext().getDiscoveryHandler().getServerUrl();
        if (serverURL == null) {
            throw new RetryAfterException(10);
        }
        return serverURL;
    }

    private URLConnection getConnection(URL url) throws IOException {
        return getAgentContext().getConnectionHandler().getConnection(url);
    }
}
