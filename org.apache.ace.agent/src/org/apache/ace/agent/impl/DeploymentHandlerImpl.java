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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.RetryAfterException;
import org.apache.felix.deploymentadmin.DeploymentAdminImpl;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentException;
import org.osgi.service.deploymentadmin.DeploymentPackage;

public class DeploymentHandlerImpl implements DeploymentHandler {

    private final AgentContext m_agentContext;
    private DeploymentAdmin m_deploymentAdmin;

    public DeploymentHandlerImpl(AgentContext agentContext) {
        this(agentContext, new DeploymentAdminImpl());
    }

    public DeploymentHandlerImpl(AgentContext agentContext, DeploymentAdmin deploymentAdmin) {
        m_agentContext = agentContext;
        m_deploymentAdmin = deploymentAdmin;
    }

    @Override
    public Version getInstalledVersion() {
        Version highestVersion = Version.emptyVersion;
        DeploymentPackage[] installedPackages = m_deploymentAdmin.listDeploymentPackages();
        for (DeploymentPackage installedPackage : installedPackages) {
            if (installedPackage.getName().equals(getIdentification())
                && installedPackage.getVersion().compareTo(highestVersion) > 0) {
                highestVersion = installedPackage.getVersion();
            }
        }
        return highestVersion;
    }

    @Override
    public SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException {

        SortedSet<Version> versions = new TreeSet<Version>();

        URL endpoint = getEndpoint(getServerURL(), getIdentification());
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
            if (connection != null && connection instanceof HttpURLConnection)
                ((HttpURLConnection) connection).disconnect();
            if (reader != null)
                reader.close();
        }
    }

    @Override
    public long getPackageSize(Version version, boolean fixPackage) throws RetryAfterException, IOException {

        URL url = getPackageURL(version, fixPackage);
        long packageSize = -1l;

        URLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = url.openConnection();
            if (urlConnection instanceof HttpURLConnection)
                ((HttpURLConnection) urlConnection).setRequestMethod("HEAD");

            String dpSizeHeader = urlConnection.getHeaderField(AgentConstants.HEADER_DPSIZE);
            if (dpSizeHeader != null)
                try {
                    packageSize = Long.parseLong(dpSizeHeader);
                }
                catch (NumberFormatException e) {
                    // ignore
                }
            return packageSize;
        }
        finally {
            if (urlConnection != null && urlConnection instanceof HttpURLConnection)
                ((HttpURLConnection) urlConnection).disconnect();
            if (inputStream != null)
                try {
                    inputStream.close();
                }
                catch (IOException e) {
                    // ignore
                }
        }
    }

    @Override
    public InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        URL packageURL = getPackageURL(version, fixPackage);
        URLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            // TODO handle problems and retries
            urlConnection = packageURL.openConnection();
            inputStream = urlConnection.getInputStream();
            return inputStream;
        }
        finally {
            if (urlConnection != null && urlConnection instanceof HttpURLConnection)
                ((HttpURLConnection) urlConnection).disconnect();
            if (inputStream != null)
                try {
                    inputStream.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    @Override
    public DownloadHandle getDownloadHandle(Version version, boolean fixPackage) {
        URL packageURL = getPackageURL(version, fixPackage);
        DownloadHandle downloadHandle = m_agentContext.getDownloadHandler().getHandle(packageURL);
        return downloadHandle;
    }

    @Override
    public void deployPackage(InputStream inputStream) {
        // FIXME exceptions
        try {
            m_deploymentAdmin.installDeploymentPackage(inputStream);
        }
        catch (DeploymentException e) {
            e.printStackTrace();
        }
    }

    private URL getPackageURL(Version version, boolean fixPackage) {
        URL url = null;
        if (fixPackage) {
            url = getEndpoint(getServerURL(), getIdentification(), getInstalledVersion(), version);
        }
        else {
            url = getEndpoint(getServerURL(), getIdentification(), version);
        }
        return url;
    }

    private String getIdentification() {
        return m_agentContext.getIdentificationHandler().getIdentification();
    }

    private URL getServerURL() {
        return m_agentContext.getDiscoveryHandler().getServerUrl();
    }

    private URLConnection getConnection(URL url) throws IOException {
        return m_agentContext.getConnectionHandler().getConnection(url);
    }

    private static URL getEndpoint(URL serverURL, String identification) {
        try {
            return new URL(serverURL, "deployment/" + identification + "/versions/");
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private URL getEndpoint(URL serverURL, String identification, Version version) {
        try {
            return new URL(serverURL, "deployment/" + identification + "/versions/" + version.toString());
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private URL getEndpoint(URL serverURL, String identification, Version from, Version to) {
        try {
            return new URL(serverURL, "deployment/" + identification + "/versions/" + to.toString() + "?current=" + from);
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
