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
package org.apache.ace.deployment.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.Deployment;
import org.apache.ace.deployment.service.DeploymentService;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.identification.Identification;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Provides an implementation for {@link DeploymentService}.
 */
public class DeploymentServiceImpl implements DeploymentService {
    
    private final String TOPIC_DEPLOYMENTPACKAGE_INSTALL = "org/apache/ace/deployment/INSTALL";

    // injected by dependencymanager
    protected volatile Deployment m_deployer;
    protected volatile Identification m_identification;
    protected volatile Discovery m_discovery;
    protected volatile LogService m_log;
    protected volatile EventAdmin m_eventAdmin;
    protected volatile ConnectionFactory m_connectionFactory;

    /**
     * @see org.apache.ace.deployment.service.DeploymentService#getHighestLocalVersion()
     */
    public Version getHighestLocalVersion() {
        Object[] installedPackages = m_deployer.list();
        List<Version> versions = new ArrayList<Version>();
        for (int i = 0; i < installedPackages.length; i++) {
            if (m_deployer.getName(installedPackages[i]).equals(m_identification.getID())) {
                versions.add(m_deployer.getVersion(installedPackages[i]));
            }
        }
        return getHighestVersion(versions);
    }

    /**
     * @see org.apache.ace.deployment.service.DeploymentService#getHighestRemoteVersion()
     */
    public Version getHighestRemoteVersion() throws IOException {
        SortedSet<Version> versions = getRemoteVersions(getURL());
        return ((versions == null) || versions.isEmpty()) ? null : versions.last();
    }

    /**
     * @see org.apache.ace.deployment.service.DeploymentService#getRemoteVersions()
     */
    public SortedSet<Version> getRemoteVersions() throws IOException {
        return getRemoteVersions(getURL());
    }

    /**
     * @see org.apache.ace.deployment.service.DeploymentService#installVersion(org.osgi.framework.Version, org.osgi.framework.Version)
     */
    public void installVersion(Version highestRemoteVersion, Version highestLocalVersion) throws IOException, Exception {
        InputStream inputStream = null;
        
        m_log.log(LogService.LOG_INFO, "Installing version: " + highestRemoteVersion);
        
        try {
            String version = highestRemoteVersion.toString();
            URL baseURL = getURL();
            boolean isFileBasedProtocol = "file".equals(baseURL.getProtocol());
            if (highestLocalVersion != null && !isFileBasedProtocol) {
                version += "?current=" + highestLocalVersion.toString();
            }
			URL dataURL = new URL(baseURL, version);
			if (isFileBasedProtocol) {
                File file = urlToFile(dataURL);
                inputStream = new FileInputStream(file);
            }
            else {
                inputStream = getContents(dataURL);
            }

            // Post event for auditlog
            m_eventAdmin.postEvent(createEvent(version, dataURL));

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
     * @see org.apache.ace.deployment.service.DeploymentService#update(org.osgi.framework.Version)
     */
    public void update(Version toVersion) throws Exception {
        installVersion(toVersion, getHighestLocalVersion());
    }

    /**
     * @param url
     * @return
     * @throws IOException
     */
    final SortedSet<Version> getRemoteVersions(URL url) throws IOException {
        if (url == null) {
            return null;
        }
        
        if ("file".equals(url.getProtocol())) {
            return getVersionsFromDirectory(url);
        }
        else {
            return getVersionsFromServer(url);
        }
    }

    /**
     * @param version
     * @param dataURL
     * @return
     */
    private Event createEvent(String version, URL dataURL) {
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put("deploymentpackage.url", dataURL.toString());
        properties.put("deploymentpackage.version", version);
        Event event = new Event(TOPIC_DEPLOYMENTPACKAGE_INSTALL, properties);
        return event;
    }

    /**
     * @param versions
     * @return
     */
    private Version getHighestVersion(List versions) {
        Version highestVersion = null;
        for (Iterator i = versions.iterator(); i.hasNext();) {
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

    /**
     * @return
     */
    private URL getURL() {
        URL host = m_discovery.discover();
        if (host == null) {
            return null;
        }
        try {
            return new URL(host, "deployment/" + m_identification.getID() + "/versions/");
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_WARNING, "Malformed URL", e);
            return null;
        }
    }

    /**
     * @param url
     * @return
     */
    private SortedSet<Version> getVersionsFromDirectory(URL url) {
        File file = urlToFile(url);
        if (!file.isDirectory()) {
            return null;
        }
            
        final File[] files = file.listFiles();
        SortedSet<Version> versions = new TreeSet<Version>();
        for (File f : files) {
            try {
                Version version = Version.parseVersion(f.getName());
                if (version != Version.emptyVersion) {
                    versions.add(version);
                }
            }
            catch (IllegalArgumentException e) {
                // if the file is not a valid version, we skip it
            }
        }
        return versions;
    }

    /**
     * @param url
     * @return
     */
    private SortedSet<Version> getVersionsFromServer(URL url) {
        BufferedReader bufReader = null;
        try {
            bufReader = new BufferedReader(new InputStreamReader(getContents(url)));
            SortedSet<Version> versions = new TreeSet<Version>();
            
            String versionString;
            while ((versionString = bufReader.readLine()) != null) {
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
            
            return versions;
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_DEBUG, "I/O error accessing server!", ioe);
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

    /**
     * @param url
     * @return
     */
    private File urlToFile(URL url) {
        File file;
        // See: http://weblogs.java.net/blog/kohsuke/archive/2007/04/how_to_convert.html
        // makes a best effort to convert a file URL to a File
        try {
            file = new File(url.toURI());
        }
        catch (URISyntaxException e) {
            file = new File(url.getPath());
        }
        return file;
    }

    /**
     * @param url the remote URL to connect to, cannot be <code>null</code>.
     * @return an {@link InputStream} to the remote URL, never <code>null</code>.
     * @throws IOException in case of I/O problems opening the remote connection.
     */
    private InputStream getContents(URL url) throws IOException {
        URLConnection conn = m_connectionFactory.createConnection(url);
        return conn.getInputStream();
    }
}