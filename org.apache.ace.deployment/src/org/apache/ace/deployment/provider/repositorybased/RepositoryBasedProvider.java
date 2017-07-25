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
package org.apache.ace.deployment.provider.repositorybased;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.SAXParserFactory;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.ArtifactDataHelper;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.provider.OverloadedException;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.deployment.provider.repositorybased.BaseRepositoryHandler.XmlDeploymentArtifact;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.ext.impl.CachedRepositoryImpl;
import org.apache.ace.repository.ext.impl.FilebasedBackupRepository;
import org.apache.ace.repository.ext.impl.RemoteRepository;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * The RepositoryBasedProvider provides version information and bundle data by the DeploymentProvider interface. It uses a
 * Repository to get its information from, which it parses using a SAX parser.
 */
public class RepositoryBasedProvider implements DeploymentProvider, ManagedService {
    private static final String URL = "url";
    private static final String NAME = "name";
    private static final String CUSTOMER = "customer";

    /**
     * Key, intended to be used for artifacts which are bundles and will publish
     * a resource processor (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_ISCUSTOMIZER = "DeploymentPackage-Customizer";

    /**
     * Key, intended to be used for resources which require a resource processor
     * (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_KEY_PROCESSORID = "Resource-Processor";

    /**
     * Key, intended to be used for artifacts which have a resourceID that's different
     * from their generated name (based on URL).
     */
    public static final String DIRECTIVE_KEY_RESOURCE_ID = "Resource-ID";

    /**
     * Key, intended to be used for matching processed (see ArtifactPreprocessor) to their
     * 'original' one.
     */
    public static final String DIRECTIVE_KEY_BASEURL = "Base-Url";

	public static final String REPOSITORY_PATH = "ACE-RepositoryPath";

    public static final String KEY_SYMBOLICNAME = Constants.BUNDLE_SYMBOLICNAME;
    public static final String KEY_NAME = Constants.BUNDLE_NAME;
    public static final String KEY_VERSION = Constants.BUNDLE_VERSION;
    public static final String KEY_VENDOR = Constants.BUNDLE_VENDOR;
    public static final String KEY_RESOURCE_PROCESSOR_PID = "Deployment-ProvidesResourceProcessor";

    public static final String MIMETYPE = "application/vnd.osgi.bundle";

    /**
     * Key, intended for configurations that specifies the maximum number of concurrent users for this repository provider.
     */
    private static final String MAXIMUM_NUMBER_OF_USERS = "MaximumNumberOfUsers";

    private volatile ArtifactDataHelper m_artifactDataHelper;
    private volatile LogService m_log;

    /** This variable is volatile since it can be changed by the Updated() method. */
    private volatile CachedRepository m_cachedRepository;

    /**
     * This variable is volatile since it can be changed by the updated() method. Furthermore, it will be used to inject a
     * custom repository in the integration test.
     */
    private volatile Repository m_directRepository;
    private volatile DependencyManager m_manager;

    private final SAXParserFactory m_saxParserFactory;
    private final Map<String,List<String>> m_cachedVersionLists;

    private final AtomicInteger m_usageCounter = new AtomicInteger();
    /** Maximum number of concurrent users. Values <= 0 are used for unlimited users. */
    private int m_maximumNumberOfUsers = 0;
    /** The default backoff time for each new user over the limit */
    private static final int BACKOFF_TIME_PER_USER = 5;

    public RepositoryBasedProvider() {
        m_saxParserFactory = SAXParserFactory.newInstance();
        m_cachedVersionLists = new LRUMap<>();
    }

    public List<ArtifactData> getBundleData(String targetId, String version) throws OverloadedException, IllegalArgumentException, IOException {
        return getBundleData(targetId, null, version);
    }

    public List<ArtifactData> getBundleData(String targetId, String versionFrom, String versionTo) throws OverloadedException, IllegalArgumentException, IOException {
        try {
            int concurrentUsers = m_usageCounter.incrementAndGet();
            if (m_maximumNumberOfUsers != 0  && m_maximumNumberOfUsers < concurrentUsers) {
                throw new OverloadedException("Too many users, maximum allowed = " + m_maximumNumberOfUsers + ", current = " + concurrentUsers,  (concurrentUsers - m_maximumNumberOfUsers) * BACKOFF_TIME_PER_USER);
            }

            try {
                if (versionFrom != null) {
                    Version.parseVersion(versionFrom);
                }
                Version.parseVersion(versionTo);
            }
            catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(nfe);
            }

            InputStream input = null;
            List<ArtifactData> dataVersionTo = null;
            List<ArtifactData> dataVersionFrom = null;

            List<XmlDeploymentArtifact>[] pairs = null;
            try {
                // ACE-240: do NOT allow local/remote repositories to be empty. If we're
                // asking for real artifacts, it means we must have a repository...
                input = getRepositoryStream(true /* fail */);
                if (versionFrom == null) {
                    pairs = getDeploymentArtifactPairs(input, targetId, new String[] { versionTo });
                }
                else {
                    pairs = getDeploymentArtifactPairs(input, targetId, new String[] { versionFrom, versionTo });
                }
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_WARNING, "Problem parsing source version.", ioe);
                throw ioe;
            }
            finally {
                if (input != null) {
                    try {
                        input.close();
                    }
                    catch (IOException e) {
                        m_log.log(LogService.LOG_DEBUG, "Error closing stream", e);
                    }
                }
            }

            if ((pairs != null) && (pairs.length > 1)) {
                dataVersionFrom = getAllArtifactData(pairs[0]);
                dataVersionTo = getAllArtifactData(pairs[1]);
                Iterator<ArtifactData> it = dataVersionTo.iterator();
                while (it.hasNext()) {
                    ArtifactDataImpl bundleDataVersionTo = (ArtifactDataImpl) it.next();
                    // see if there was previously a version of this bundle, and update the 'changed' property accordingly.
                    if (bundleDataVersionTo.isBundle()) {
                        ArtifactData bundleDataVersionFrom = getArtifactData(bundleDataVersionTo.getSymbolicName(), dataVersionFrom);
                        bundleDataVersionTo.setChanged(!bundleDataVersionTo.equals(bundleDataVersionFrom));
                    }
                    else {
                        ArtifactData bundleDataVersionFrom = getArtifactData(bundleDataVersionTo.getUrl(), dataVersionFrom);
                        bundleDataVersionTo.setChanged(bundleDataVersionFrom == null);
                    }
                }
            }
            else {
                dataVersionTo = getAllArtifactData(pairs[0]);
            }

            if (dataVersionTo == null) {
                return new ArrayList<>();
            }

            // ACE-627: allow a custom ordering to be specified for the artifacts to appear in our DP...
            return m_artifactDataHelper.process(dataVersionTo, targetId, versionFrom, versionTo);
        }
        finally {
            m_usageCounter.getAndDecrement();
        }
    }

    public List<String> getVersions(String targetId) throws OverloadedException, IllegalArgumentException, IOException {
        try {
            int concurrentUsers = m_usageCounter.incrementAndGet();
            if (m_maximumNumberOfUsers != 0  && m_maximumNumberOfUsers < concurrentUsers) {
                throw new OverloadedException("Too many users, maximum allowed = " + m_maximumNumberOfUsers + ", current = " + concurrentUsers,  (concurrentUsers - m_maximumNumberOfUsers) * BACKOFF_TIME_PER_USER);
            }

            // check if cache is up to date
            if (isCacheUpToDate()) {
                List<String> result = m_cachedVersionLists.get(targetId);
                if (result != null) {
                    return result;
                }
            }
            else {
                m_cachedVersionLists.clear();
            }

            List<String> stringVersionList = new ArrayList<>();
            InputStream input = null;

            try {
                // ACE-240: allow local/remote repositories to be empty; as the target
                // might be new & unregistered, it can have no repository yet...
                input = getRepositoryStream(false /* fail */);
                List<Version> versionList;
                if (input == null) {
                    versionList = Collections.EMPTY_LIST;
                }
                else {
                    versionList = getAvailableVersions(input, targetId);
                }
                if (versionList.isEmpty()) {
                    m_log.log(LogService.LOG_DEBUG, "No versions found for target: " + targetId);
                }
                else {
                    // now sort the list of versions and convert all values to strings.
                    Collections.sort(versionList);
                    Iterator<Version> it = versionList.iterator();
                    while (it.hasNext()) {
                        String version = (it.next()).toString();
                        stringVersionList.add(version);
                    }
                }
            }
            catch (IllegalArgumentException iae) {
                // just move on.
            }
            catch (IOException ioe) {
                m_log.log(LogService.LOG_DEBUG, "Problem parsing DeploymentRepository", ioe);
                throw ioe;
            }
            finally {
                if (input != null) {
                    try {
                        input.close();
                    }
                    catch (IOException e) {
                        m_log.log(LogService.LOG_DEBUG, "Error closing stream", e);
                    }
                }
            }

            m_log.log(LogService.LOG_DEBUG, "Cache added for " + targetId);

            m_cachedVersionLists.put(targetId, stringVersionList);
            return stringVersionList;
        }
        finally {
            m_usageCounter.getAndDecrement();
        }
    }

    /**
     * Helper method to get the bundledata given an inputstream to a repository xml file
     *
     * @param input An input stream to the XML data to be parsed.
     * @return A list of ArtifactData object representing this version.
     */
    private List<ArtifactData> getAllArtifactData(List<XmlDeploymentArtifact> deploymentArtifacts) throws IllegalArgumentException {
        List<ArtifactData> result = new ArrayList<>();

        // get the bundledata for each URL
        for (XmlDeploymentArtifact pair : deploymentArtifacts) {
            long artifactSize = pair.getSize();

            Map<String, String> directives = pair.getDirective();
            if (directives.get(DIRECTIVE_KEY_PROCESSORID) == null) {
                // this is a bundle.
                String symbolicName = directives.get(KEY_SYMBOLICNAME);
                String bundleVersion = directives.get(KEY_VERSION);
                if (symbolicName != null) {
                    // it is the right symbolic name
                    if ("".equals(symbolicName.trim())) {
                        m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + pair.toString() + " the symbolic name is empty.");
                    }
                    else {
                        result.add(new ArtifactDataImpl(pair.getUrl(), directives, symbolicName, artifactSize, bundleVersion, true /* hasChanged */));
                    }
                }
            }
            else {
                // it is an artifact.
                String filename = directives.get(DIRECTIVE_KEY_RESOURCE_ID);
                result.add(new ArtifactDataImpl(pair.getUrl(), directives, filename, artifactSize, true /* hasChanged */));
            }
        }

        return result;
    }

    /**
     * Helper method check for the existence of artifact data in the collection for a bundle with the given url.
     *
     * @param url The url to be found.
     * @return The <code>ArtifactData</code> object that has this <code>url</code>, or <code>null</code> if none can be
     *         found.
     */
    private ArtifactData getArtifactData(URL url, Collection<ArtifactData> data) {
        ArtifactData bundle = null;
        URI uri = null;
        try {
            uri = url.toURI();
        }
        catch (URISyntaxException e) {
            m_log.log(LogService.LOG_ERROR, "Could not convert URL " + url + " to a URI");
            return null;
        }
        Iterator<ArtifactData> it = data.iterator();
        while (it.hasNext()) {
            bundle = it.next();
            try {
                if (uri.equals(bundle.getUrl().toURI())) {
                    return bundle;
                }
            }
            catch (URISyntaxException e) {
                m_log.log(LogService.LOG_ERROR, "Could not convert bundle URL for " + bundle.getFilename() + " to a URI");
            }
        }
        return null;
    }

    /**
     * Helper method check for the existence of artifact data in the collection for a bundle with the given symbolic name.
     *
     * @param symbolicName The symbolic name to be found.
     * @return The <code>ArtifactData</code> object that has this <code>symbolicName</code>, or <code>null</code> if none
     *         can be found.
     */
    private ArtifactData getArtifactData(String symbolicName, Collection<ArtifactData> data) {
        ArtifactData bundle = null;
        Iterator<ArtifactData> it = data.iterator();
        while (it.hasNext()) {
            bundle = it.next();
            String bsn = bundle.getSymbolicName();
            if ((bsn != null) && bsn.equals(symbolicName)) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * Returns the available deployment versions for a target
     *
     * @param input A dom document representation of the repository
     * @param targetId The target identifier
     * @return A list of available versions
     */
    private List<Version> getAvailableVersions(InputStream input, String targetId) throws IllegalArgumentException {
        DeploymentPackageVersionCollector collector = new DeploymentPackageVersionCollector(targetId);

        try {
            m_saxParserFactory.newSAXParser().parse(input, collector);

            return collector.getVersions();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Helper method to retrieve urls and directives for a target-version combination.
     *
     * @param input An input stream from which an XML representation of a deployment repository can be read.
     * @param targetId The target identifier to be used
     * @param versions An array of versions.
     * @return An array of lists of URLDirectivePairs. For each version in <code>versions</code>, a separate list will be
     *         created; the index of a version in the <code>versions</code> array is equal to the index of its result in the
     *         result array.
     * @throws IllegalArgumentException if the targetId or versions cannot be found in the input stream, or if
     *         <code>input</code> does not contain an XML stream.
     */
    private List<XmlDeploymentArtifact>[] getDeploymentArtifactPairs(InputStream input, String targetId, String[] versions) throws IllegalArgumentException {
        final DeploymentArtifactCollector collector = new DeploymentArtifactCollector(targetId, versions);

        try {
            m_saxParserFactory.newSAXParser().parse(input, collector);

            return collector.getArtifacts();
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Helper to get an input stream to the currently used deployment repository.
     *
     * @return An input stream to the repository document. Will return an empty stream if none can be found.
     * @throws java.io.IOException if there is a problem communicating with the local or remote repository.
     */
    private InputStream getRepositoryStream(boolean fail) throws IOException {
        // cache the repositories, since we do not want them to change while we're in this method.
        CachedRepository cachedRepository = m_cachedRepository;
        Repository repository = m_directRepository;
        InputStream result;

        if (cachedRepository != null) {
            // we can use the cached repository
            if (cachedRepository.isCurrent()) {
                result = cachedRepository.getLocal(fail);
            }
            else {
                result = cachedRepository.checkout(fail);
            }
        }
        else {
            RangeIterator ri = repository.getRange().iterator();
            long resultVersion = 0;
            while (ri.hasNext()) {
                resultVersion = ri.next();
            }
            if (resultVersion != 0) {
                result = repository.checkout(resultVersion);
            }
            else {
                throw new IllegalArgumentException("There is no deployment information available.");
            }
        }
        if (result == null) {
        	return null;
        }
        else {
        	return new GZIPInputStream(result);
        }
    }

    private boolean isCacheUpToDate() {
        CachedRepository cachedRepository = m_cachedRepository;
        try {
			return (cachedRepository != null && cachedRepository.isCurrent());
		}
        catch (IOException ioe) {
        	m_log.log(LogService.LOG_WARNING, "Failed to check if cache is current. Assuming it's not.", ioe);
        	return false;
		}
    }

    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String url = getNotNull(settings, URL, "DeploymentRepository URL not configured.");
            String name = getNotNull(settings, NAME, "RepositoryName not configured.");
            String customer = getNotNull(settings, CUSTOMER, "RepositoryCustomer not configured.");
            String maximumNumberOfUsers = (String) settings.get(MAXIMUM_NUMBER_OF_USERS);

            if (maximumNumberOfUsers != null) {
                try {
                    m_maximumNumberOfUsers = Integer.parseInt(maximumNumberOfUsers);
                }
                catch (NumberFormatException nfe) {
                    throw new ConfigurationException(MAXIMUM_NUMBER_OF_USERS, maximumNumberOfUsers + " is not a valid value for the maximum number of concurrent users.");
                }
            }

            // create the remote repository and set it.
            try {
                BackupRepository backup = new FilebasedBackupRepository(File.createTempFile("currentrepository", null), File.createTempFile("backuprepository", null));

                // We always create the remote repository. If we can create a backup repository, we will wrap a CachedRepository
                // around it.
                m_directRepository = new RemoteRepository(new URL(url), customer, name);

                m_manager.add(m_manager.createComponent()
                    .setImplementation(m_directRepository)
                    .add(m_manager.createServiceDependency()
                        .setService(ConnectionFactory.class)
                        .setRequired(true)));

                m_cachedRepository = null;
                if (backup != null) {
                    m_cachedRepository = new CachedRepositoryImpl(m_directRepository, backup, CachedRepositoryImpl.UNCOMMITTED_VERSION);
                }
            }
            catch (IllegalArgumentException e) {
                throw new ConfigurationException("Authentication", e.getMessage());
            }
            catch (MalformedURLException mue) {
                throw new ConfigurationException(URL, mue.getMessage());
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_WARNING, "Unable to create temporary files for FilebasedBackupRepository");
            }
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