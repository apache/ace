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
package org.apache.ace.it.deployment.provider.filebased;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.ArtifactDataHelper;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.provider.OverloadedException;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * This class reads data from the filesystem. It contains deployment data in the following format: <storage dir>/<target-name>/<bundle-version>/<jars>
 * example : storage-directory/ storage-directory/target-a storage-directory/target-a/1.0.0
 * storage-directory/target-a/1.0.0/bundle1.jar storage-directory/target-a/1.0.0/bundle2.jar storage-directory/target-a/1.1.0
 * storage-directory/target-a/1.1.0/bundle2.jar storage-directory/target-a/1.1.0/bundle3.jar The versions are in the
 * org.osgi.framework.Version format.
 */
public class FileBasedProvider implements DeploymentProvider, ManagedService {
    /** Directory where all the target ID folders are located. */
    private static final String DIRECTORY_NAME = "BaseDirectoryName";
    /** Fallback directory for all targets that have no specific versions. Defaults to BaseDirectoryName if not specified. */
    private static final String DEFAULT_DIRECTORY_NAME = "DefaultDirectoryName";
    /** Configuration key for the number of concurrent users */
    private static final String MAXIMUM_NUMBER_OF_USERS = "MaximumNumberOfUsers";
    private static final int OSGI_R4_MANIFEST_VERSION = 2;
    private volatile File m_baseDirectory;
    private volatile File m_defaultDirectory;

    private volatile ArtifactDataHelper m_artifactDataHelper;
    private volatile LogService m_log;

    private final Semaphore m_disk = new Semaphore(1, true);

    private final AtomicInteger m_usageCounter = new AtomicInteger();
    /** Maximum number of concurrent users. Value 0 is used for unlimited users. */
    private int m_maximumNumberOfUsers = 0;
    /** The default backoff time for each new user over the limit */
    private static final int BACKOFF_TIME_PER_USER = 5;

    /**
     * Get the bundle data from the bundles in the &lt;data dir&gt;/&lt;target&gt;/&lt;version&gt; directory It reads the manifest from all the
     * .jar files in that directory. If the manifest cannot be found, This method can only parse OSGi R4 bundles
     */
    public List<ArtifactData> getBundleData(String targetId, String version) throws OverloadedException, IllegalArgumentException {
        try {
            int concurrentUsers = m_usageCounter.incrementAndGet();
            if (m_maximumNumberOfUsers != 0  && m_maximumNumberOfUsers < concurrentUsers) {
                throw new OverloadedException("Too many users, maximum allowed = " + m_maximumNumberOfUsers + ", current = " + concurrentUsers,  (concurrentUsers - m_maximumNumberOfUsers) * BACKOFF_TIME_PER_USER);
            }

            return m_artifactDataHelper.process(internalGetBundleData(targetId, version), targetId, null, version);
        } finally {
            m_usageCounter.getAndDecrement();
        }

    }

    /**
     * Version folder and requested version do not always match (see implementation of getVersions, which uses Versions.parseVersion to allow different styles)
     * like 1 instead of 1.0.0 and alike.
     * So we need to do some crawling to map them.
     *
     * @param targetDirectory store directory
     * @param version          that has been requested.
     *
     * @return the matching folder.
     *
     * @throws IllegalArgumentException if no matching folder has been found. If this happens something is weirdly wrong.
     */
    private File findMatchingVersionDirectory(File targetDirectory, String version) {
        // first try the direct way:
        File directTry = new File(targetDirectory, version);
        if ((directTry != null) && directTry.isDirectory()) {
            return directTry;
        }
        // otherwise try to find it:
        Version requestedVersion;
        try {
            requestedVersion = Version.parseVersion(version);
        }
        catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Requested version " + version + " has no matching folder in store: " + targetDirectory.getAbsolutePath());
        }

        File[] files = targetDirectory.listFiles();
        for (int i = 0; i < files.length; i++) {
            File possibleVersionDirectory = files[i];
            if (possibleVersionDirectory.isDirectory()) {
                // ok, it is a directory. Now see if it is a version
                try {
                    Version foundVersion = Version.parseVersion(possibleVersionDirectory.getName());
                    // no exception, but is could still be an empty version
                    if ((requestedVersion != null) && requestedVersion.equals(foundVersion)) {
                        return new File(targetDirectory, possibleVersionDirectory.getName());
                    }
                }
                catch (IllegalArgumentException iae) {
                    // dont' care at this point.
                }
            }
        }
        throw new IllegalArgumentException("Requested version " + version + " has no matching folder in store: " + targetDirectory.getAbsolutePath());
    }

    public List<ArtifactData> getBundleData(String targetId, String versionFrom, String versionTo) throws OverloadedException, IllegalArgumentException {
        try {
            int concurrentUsers = m_usageCounter.incrementAndGet();
            if (m_maximumNumberOfUsers != 0  && m_maximumNumberOfUsers < concurrentUsers) {
                throw new OverloadedException("Too many users, maximum allowed = " + m_maximumNumberOfUsers + ", current = " + concurrentUsers,  (concurrentUsers - m_maximumNumberOfUsers) * BACKOFF_TIME_PER_USER);
            }
            List<ArtifactData> dataVersionFrom = internalGetBundleData(targetId, versionFrom);
            List<ArtifactData> dataVersionTo = internalGetBundleData(targetId, versionTo);

            Iterator<ArtifactData> it = dataVersionTo.iterator();
            while (it.hasNext()) {
                ArtifactDataImpl bundleDataVersionTo = (ArtifactDataImpl) it.next();
                // see if there was previously a version of this bundle.
                ArtifactData bundleDataVersionFrom = getBundleData(bundleDataVersionTo.getSymbolicName(), dataVersionFrom);
                bundleDataVersionTo.setChanged(!bundleDataVersionTo.equals(bundleDataVersionFrom));
            }
            return m_artifactDataHelper.process(dataVersionTo, targetId, versionFrom, versionTo);
        } finally {
            m_usageCounter.getAndDecrement();
        }
    }

    /**
     * Check for the existence of bundledata in the collection for a bundle with the given symbolic name
     *
     * @param symbolicName
     */
    private ArtifactData getBundleData(String symbolicName, Collection<ArtifactData> data) {
        Iterator<ArtifactData> it = data.iterator();
        while (it.hasNext()) {
            ArtifactData bundle = it.next();
            if (bundle.getSymbolicName().equals(symbolicName)) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * Look in the baseDirectory for the specified target. If it exists, get the list of directories in there and check if they
     * conform to the <code>org.osgi.framework.Version</code> format. If it does, it will be in the list of versions that are
     * returned. If there are no valid versions, return an empty list. If the target cannot be found, an
     * IllegalArgumentException is thrown. The list will be sorted on version.
     */
    public List<String> getVersions(String targetId) throws OverloadedException, IllegalArgumentException {
        try {
            int concurrentUsers = m_usageCounter.incrementAndGet();
            if (m_maximumNumberOfUsers != 0  && m_maximumNumberOfUsers < concurrentUsers) {
                throw new OverloadedException("Too many users, maximum allowed = " + m_maximumNumberOfUsers + ", current = " + concurrentUsers,  (concurrentUsers - m_maximumNumberOfUsers) * BACKOFF_TIME_PER_USER);
            }
            List<Version> versionList = new ArrayList<>();
            File targetDirectory = new File(m_baseDirectory.getAbsolutePath(), targetId);
            if (targetDirectory.isDirectory()) {
                getVersions(targetId, versionList, targetDirectory);
            }
            else {
                // try the default
                getVersions(targetId, versionList, m_defaultDirectory);
            }

            // now sort the list of versions and convert all values to strings.
            Collections.sort(versionList);
            List<String> stringVersionList = new ArrayList<>();
            Iterator<Version> it = versionList.iterator();
            while (it.hasNext()) {
                String version = (it.next()).toString();
                stringVersionList.add(version);
            }
            return stringVersionList;
        } finally {
            m_usageCounter.getAndDecrement();
        }
    }

    private List<ArtifactData> internalGetBundleData(String targetId, String version) throws OverloadedException, IllegalArgumentException {
        List<String> versions = getVersions(targetId);
        if (!versions.contains(version)) {
            throw new IllegalArgumentException("Unknown version " + version + " requested");
        }
        File targetDirectory = new File(m_baseDirectory, targetId);
        File versionDirectory;
        if (targetDirectory.isDirectory()) {
            // target has its own folder
            versionDirectory = findMatchingVersionDirectory(targetDirectory, version);
        }
        else {
            versionDirectory = findMatchingVersionDirectory(m_defaultDirectory, version);
        }
        List<ArtifactData> bundleData = new ArrayList<>();

        JarInputStream jarInputStream = null;

        File[] jarFiles = versionDirectory.listFiles();
        for (int i = 0; i < jarFiles.length; i++) {
            Manifest bundleManifest = null;
            File jarFile = jarFiles[i];
            try {
                jarInputStream = new JarInputStream(new FileInputStream(jarFile));
                bundleManifest = jarInputStream.getManifest();
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_WARNING, "Error making inputstream", ioe);
                continue;
            }
            finally {
                if (jarInputStream != null) {
                    try {
                        jarInputStream.close();
                    }
                    catch (Exception ioe) {
                        m_log.log(LogService.LOG_ERROR, "Error closing the file input stream", ioe);
                    }
                }
            }
            Attributes mainAttributes = bundleManifest.getMainAttributes();
            String manifestVersion = mainAttributes.getValue(Constants.BUNDLE_MANIFESTVERSION);
            String symbolicName = mainAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            String bundleVersion = mainAttributes.getValue(Constants.BUNDLE_VERSION);

            if ((manifestVersion != null) && (symbolicName != null) && (bundleVersion != null)) {
                // ok, now at least we have the required attributes
                // now check if they have the expected values
                if (OSGI_R4_MANIFEST_VERSION != new Integer(manifestVersion).intValue()) {
                    m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + jarFile.getAbsolutePath() + " has the wrong manifest version.");
                }
                else {
                    // it is the right manifest version
                    if (symbolicName.trim().equals("")) {
                        m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + jarFile.getAbsolutePath() + " the symbolic name is empty.");
                    }
                    else {
                        // it also has the right symbolic name
                        try {
                            new Version(bundleVersion);
                            // Do a file.toURI().toURL() to preserve special path characters
                            // see http://www.javalobby.org/java/forums/t19698.html
                            URL bundleUrl = new URL(null, jarFile.toURI().toURL().toString(), new URLStreamHandler() {
                                @Override
                                protected URLConnection openConnection(final URL u) throws IOException {
                                    return new URLConnection(u) {
                                        @Override
                                        public void connect() throws IOException {
                                            // TODO Auto-generated method stub
                                        }
                                        @Override
                                        public InputStream getInputStream() throws IOException {
                                            final InputStream parent;
                                            try {
                                                parent = new URL(u.toURI().toURL().toString()).openStream();
                                            }
                                            catch (URISyntaxException ex) {
                                                throw new IOException(ex.getMessage());
                                            }
                                            return new InputStream() {
                                                @Override
                                                public int read() throws IOException {
                                                    return parent.read();
                                                }

                                                @Override
                                                public int read(byte[] buffer) throws IOException {
                                                    return read(buffer, 0, buffer.length);
                                                }

                                                @Override
                                                public int read(byte[] buffer, int off, int length) throws IOException {
                                                    m_disk.acquireUninterruptibly();
                                                    try {
                                                        return parent.read(buffer, off, length);
                                                    }
                                                    finally {
                                                        m_disk.release();
                                                    }
                                                }

                                                @Override
                                                public void close() throws IOException {
                                                    parent.close();
                                                }
                                            };
                                        }
                                    };
                                }
                            });
                            bundleData.add(new ArtifactDataImpl(jarFile.getName(), symbolicName, jarFile.length(), bundleVersion, bundleUrl, true));
                        }
                        catch (IllegalArgumentException iae) {
                            m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + jarFile.getAbsolutePath() + " has an illegal version", iae);
                        }
                        catch (MalformedURLException mue) {
                            m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + jarFile.getAbsolutePath() + " unable to convert path to URL", mue);
                        }
                    }
                }
            }
            else {
                m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + jarFile.getAbsolutePath() + " is missing required attributes");
            }
        }

        return bundleData;
    }

    /**
     *
     * @param targetId ID that requested versions
     * @param versionList where collected versions will be put into.
     * @param base folder to be crawled.
     */
    private void getVersions(String targetId, List<Version> versionList, File base) {
        // ok, it is a directory. Now treat all the subdirectories as seperate versions
        File[] files = base.listFiles();
        for (int i = 0; i < files.length; i++) {
            File possibleVersionDirectory = files[i];
            if (possibleVersionDirectory.isDirectory()) {
                // ok, it is a directory. Now see if it is a version
                try {
                    Version version = Version.parseVersion(possibleVersionDirectory.getName());
                    // no exception, but is could still be an empty version
                    if (!version.equals(Version.emptyVersion)) {
                        versionList.add(version);
                    }
                }
                catch (IllegalArgumentException iae) {
                    // do nothing. This version will be ignored.
                }
            }
        }
        if (files.length == 0) {
            m_log.log(LogService.LOG_DEBUG, "No versions found for target: " + targetId);
        }
    }

    /**
     * Update the configuration for this bundle. It checks if the basedirectory exists and is a directory.
     */
    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String maximumNumberOfUsers = (String) settings.get(MAXIMUM_NUMBER_OF_USERS);
            if (maximumNumberOfUsers != null) {
                m_maximumNumberOfUsers = Integer.parseInt(maximumNumberOfUsers);
            }

            String baseDirectoryName = getNotNull(settings, DIRECTORY_NAME, "The base directory cannot be null");
            File baseDirectory = new File(baseDirectoryName);
            if (!baseDirectory.exists() || !baseDirectory.isDirectory()) {
                throw new ConfigurationException(DIRECTORY_NAME, "The directory called '" + baseDirectoryName + "' " + (baseDirectory.exists() ? "is no directory." : "doesn't exist."));
            }
            m_baseDirectory = baseDirectory;

            String defaultDirectoryName = (String) settings.get(DEFAULT_DIRECTORY_NAME);
            if (defaultDirectoryName != null) {
                File defaultDirectory = new File(defaultDirectoryName);
                if (defaultDirectory.exists() && defaultDirectory.isDirectory()) {
                    m_defaultDirectory = defaultDirectory;
                    return;
                }
            }
            // fallback to using the base directory
            m_defaultDirectory = baseDirectory;
        }
    }

    /**
     * Convenience method for getting settings from a configuration dictionary.
     */
    private String getNotNull(Dictionary<String, ?> settings, String id, String errorMessage) throws ConfigurationException {
        String result = (String) settings.get(id);
        if (result == null) {
            throw new ConfigurationException(id, errorMessage);
        }
        return result;
    }
}