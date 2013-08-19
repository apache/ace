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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.SortedSet;

import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.RetryAfterException;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentException;
import org.osgi.service.deploymentadmin.DeploymentPackage;

public class DeploymentHandlerImpl extends UpdateHandlerBase implements DeploymentHandler {

    private final AgentContext m_agentContext;
    private final DeploymentAdmin m_deploymentAdmin;

    public DeploymentHandlerImpl(AgentContext agentContext, DeploymentAdmin deploymentAdmin) {
        super(agentContext);
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
    public void deployPackage(InputStream inputStream) {
        // FIXME exceptions
        try {
            m_deploymentAdmin.installDeploymentPackage(inputStream);
        }
        catch (DeploymentException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getPackageSize(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getPackageSize(getPackageURL(version, fixPackage));
    };

    @Override
    public InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getInputStream(getPackageURL(version, fixPackage));
    };

    @Override
    public DownloadHandle getDownloadHandle(Version version, boolean fixPackage) {
        return getDownloadHandle(getPackageURL(version, fixPackage));
    };

    @Override
    public SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException {
        return getAvailableVersions(getEndpoint(getServerURL(), getIdentification()));
    };

    private URL getPackageURL(Version version, boolean fixPackage) {
        URL url = getEndpoint(getServerURL(), getIdentification(), fixPackage ? getInstalledVersion() : Version.emptyVersion, version);
        return url;
    }

    private URL getEndpoint(URL serverURL, String identification) {
        try {
            return new URL(serverURL, "deployment/" + identification + "/versions/");
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private URL getEndpoint(URL serverURL, String identification, Version from, Version to) {
        try {
            if (from == null || from.equals(Version.emptyVersion)) {
                return new URL(serverURL, "deployment/" + identification + "/versions/" + to.toString());
            }
            else {
                return new URL(serverURL, "deployment/" + identification + "/versions/" + to.toString() + "?current=" + from);
            }
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
