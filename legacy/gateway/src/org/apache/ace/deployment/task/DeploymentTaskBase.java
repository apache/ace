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
package org.apache.ace.deployment.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.ace.deployment.Deployment;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

public class DeploymentTaskBase {

    private final String TOPIC_DEPLOYMENTPACKAGE_INSTALL = "org/apache/ace/deployment/INSTALL";

    // injected by dependencymanager
    protected volatile Deployment m_deployer;
    protected volatile Identification m_identification;
    protected volatile Discovery m_discovery;
    protected volatile LogService m_log;
    protected volatile EventAdmin m_eventAdmin;

    /**
     * Installs the version specified by the highestRemoteVersion.
     *
     * @param url Base URL for retrieving a specific version
     * @param highestRemoteVersion The version to retrieve and install
     * @param highestLocalVersion The current version or <code>null</code> in case of none.
     */
    public void installVersion(URL url, Version highestRemoteVersion, Version highestLocalVersion) throws IOException, Exception {
        InputStream inputStream = null;
        m_log.log(LogService.LOG_INFO, "Installing version: " + highestRemoteVersion);
        try {
            String version = highestRemoteVersion.toString();
            if (highestLocalVersion != null) {
                version += "?current=" + highestLocalVersion.toString();
            }
            URL dataURL = new URL(url, version);
            inputStream = dataURL.openStream();

            // Post event for auditlog
            Dictionary properties = new Properties();
            properties.put("deploymentpackage.url", dataURL.toString());
            properties.put("deploymentpackage.version", version);
            m_eventAdmin.postEvent(new Event(TOPIC_DEPLOYMENTPACKAGE_INSTALL, properties));

            m_deployer.install(inputStream);
        }
        finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                }
                catch (Exception ex) {
                    // Not much we can do.
                }
            }
        }
    }

    /**
     * Returns the highest version that is available locally (already installed).
     *
     * @return The highest installed version or <code>null</code> if no version is available locally.
     */
    public Version getHighestLocalVersion() {
        Object[] installedPackages = m_deployer.list();
        List versions = new ArrayList();
        for (int i = 0; i < installedPackages.length; i++) {
            versions.add(m_deployer.getVersion(installedPackages[i]));
        }
        return getHighestVersion(versions);
    }

    /**
     * Returns the highest version that is available remotely.
     *
     * @param url The URL to be used to retrieve the versions available on the remote.
     * @return The highest version available on the remote or <code>null</code> if no versions were available or the remote could not be reached.
     */
    public Version getHighestRemoteVersion(URL url) {
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new InputStreamReader(url.openStream()));

            List versions = new ArrayList();
            for (String versionString = bufReader.readLine(); versionString != null; versionString = bufReader.readLine()) {
                try {
                    Version version = Version.parseVersion(versionString);
                    if (version != Version.emptyVersion) {
                        versions.add(version);
                    }
                }
                catch (IllegalArgumentException iae) {
                    m_log.log(LogService.LOG_WARNING, "Received malformed version, ignoring: " + versionString);
                }
            }
            return getHighestVersion(versions);
        }
        catch (IOException ioe) {
            return null;
        }
        finally {
            if (bufReader != null) {
                try {
                    bufReader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
    }

    private Version getHighestVersion(List versions) {
        Version highestVersion = null;
        for (Iterator i = versions.iterator(); i.hasNext(); ) {
            Version version = (Version) i.next();
            if (highestVersion == null) {
                highestVersion = version;
            }
            else if (version.compareTo(highestVersion) > 0) {
                highestVersion = version;
            }
        }
        return highestVersion;
    }

}
