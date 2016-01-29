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

import static org.apache.ace.agent.impl.ConnectionUtil.checkConnectionResponse;
import static org.apache.ace.agent.impl.ConnectionUtil.close;
import static org.apache.ace.agent.impl.ConnectionUtil.closeSilently;

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
import org.apache.ace.agent.UpdateHandler;
import org.osgi.framework.Version;

abstract class UpdateHandlerBase extends ComponentBase implements UpdateHandler {

    public UpdateHandlerBase(String componentIdentifier) {
        super(componentIdentifier);
    }

    @Override
    public final Version getHighestAvailableVersion() throws RetryAfterException, IOException {
        SortedSet<Version> available = getAvailableVersions();
        return getHighestVersion(available);
    }

    protected SortedSet<Version> getAvailableVersions(URL endpoint) throws RetryAfterException, IOException {
        SortedSet<Version> versions = new TreeSet<>();
        URLConnection connection = null;
        BufferedReader reader = null;
        try {
            connection = getConnection(endpoint);

            checkConnectionResponse(connection);

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
            closeSilently(reader);
            close(connection);
        }
    }

    protected DownloadHandle getDownloadHandle(URL packageURL) {
        return getDownloadHandler().getHandle(packageURL);
    }

    protected String getIdentification() {
        return getIdentificationHandler().getAgentId();
    }

    protected InputStream getInputStream(URL packageURL) throws RetryAfterException, IOException {
        return new ContentRangeInputStream(getConnectionHandler(), packageURL);
    }

    protected long getPackageSize(URL url) throws RetryAfterException, IOException {
        URLConnection urlConnection = null;
        try {
            urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection) {
                ((HttpURLConnection) urlConnection).setRequestMethod("HEAD");
            }

            long dpSize = -1L;
            // getHeaderFieldLong is added in JDK7, unfortunately...
            String headerDPSize = urlConnection.getHeaderField(AgentConstants.HEADER_DPSIZE);
            if (headerDPSize != null && !"".equals(headerDPSize.trim())) {
                try {
                    dpSize = Long.parseLong(headerDPSize);
                }
                catch (NumberFormatException exception) {
                    // Ignore, use default of -1...
                }
            }
            return dpSize;
        }
        finally {
            close(urlConnection);
        }
    }

    protected URL getServerURL() throws RetryAfterException {
        // FIXME not sure if this is the proper place
        URL serverURL = getDiscoveryHandler().getServerUrl();
        if (serverURL == null) {
            throw new RetryAfterException(10);
        }
        return serverURL;
    }

    private URLConnection getConnection(URL url) throws IOException {
        return getConnectionHandler().getConnection(url);
    }

    private Version getHighestVersion(SortedSet<Version> available) {
        Version highest = Version.emptyVersion;
        if (available != null && !available.isEmpty()) {
            highest = available.last();
        }
        return highest;
    }
}
