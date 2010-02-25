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
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathVariableResolver;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.repository.RangeIterator;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.impl.CachedRepositoryImpl;
import org.apache.ace.repository.impl.FilebasedBackupRepository;
import org.apache.ace.repository.impl.RemoteRepository;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The RepositoryBasedProvider provides version information and bundle data by the DeploymentProvider interface. It uses a
 * Repository to get its information from, which it parses using a SAX parser.
 */
public class RepositoryBasedProvider implements DeploymentProvider, ManagedService {
    private static final String URL = "url";
    private static final String NAME = "name";
    private static final String CUSTOMER = "customer";
    private volatile LogService m_log;

    /** This variable is volatile since it can be changed by the Updated() method. */
    private volatile CachedRepository m_cachedRepository;

    /**
     * This variable is volatile since it can be changed by the updated() method. Furthermore, it will be used to inject a
     * custom repository in the integration test.
     */
    private volatile Repository m_directRepository;

    public List<ArtifactData> getBundleData(String gatewayId, String version) throws IllegalArgumentException, IOException {
        return getBundleData(gatewayId, null, version);
    }

    public List<ArtifactData> getBundleData(String gatewayId, String versionFrom, String versionTo) throws IllegalArgumentException, IOException {
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

        List<URLDirectivePair>[] pairs = null;
        try {
            input = getRepositoryStream();
            if (versionFrom == null) {
                pairs = getURLDirectivePairs(input, gatewayId, new String[] { versionTo });
            }
            else {
                pairs = getURLDirectivePairs(input, gatewayId, new String[] { versionFrom, versionTo });
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
            dataVersionFrom = getBundleDataByDocument(pairs[0]);
            dataVersionTo = getBundleDataByDocument(pairs[1]);
            Iterator<ArtifactData> it = dataVersionTo.iterator();
            while (it.hasNext()) {
                ArtifactDataImpl bundleDataVersionTo = (ArtifactDataImpl) it.next();
                // see if there was previously a version of this bundle, and update the 'changed' property accordingly.
                if (bundleDataVersionTo.isBundle()) {
                    ArtifactData bundleDataVersionFrom = getBundleData(bundleDataVersionTo.getSymbolicName(), dataVersionFrom);
                    bundleDataVersionTo.setChanged(!bundleDataVersionTo.equals(bundleDataVersionFrom));
                }
                else {
                    ArtifactData bundleDataVersionFrom = getBundleData(bundleDataVersionTo.getUrl(), dataVersionFrom);
                    bundleDataVersionTo.setChanged(bundleDataVersionFrom == null);
                }
            }
        }
        else {
            dataVersionTo = getBundleDataByDocument(pairs[0]);
        }

        return dataVersionTo != null ? dataVersionTo : new ArrayList<ArtifactData>();
    }

    @SuppressWarnings("unchecked")
    public List<String> getVersions(String gatewayId) throws IllegalArgumentException, IOException {
        List<String> stringVersionList = new ArrayList<String>();
        InputStream input = null;

        try {
            input = getRepositoryStream();
            List<Version> versionList = getAvailableVersions(input, gatewayId);
            if (versionList.isEmpty()) {
                m_log.log(LogService.LOG_DEBUG, "No versions found for gateway " + gatewayId);
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

        return stringVersionList;
    }

    /**
     * Helper method to get the bundledata given an inputstream to a repository xml file
     *
     * @param input An input stream to the XML data to be parsed.
     * @return A list of ArtifactData object representing this version.
     */
    private List<ArtifactData> getBundleDataByDocument(List<URLDirectivePair> urlDirectivePairs) throws IllegalArgumentException {
        List<ArtifactData> result = new ArrayList<ArtifactData>();

        // get the bundledata for each URL
        for (URLDirectivePair pair : urlDirectivePairs) {
            Map<String, String> directives = pair.getDirective();

            if (directives.get(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID) == null) {
                // this is a bundle.
                String symbolicName = directives.remove(BundleHelper.KEY_SYMBOLICNAME);
                String bundleVersion = directives.remove(BundleHelper.KEY_VERSION);
                if (symbolicName != null) {
                    // it is the right symbolic name
                    if (symbolicName.trim().equals("")) {
                        m_log.log(LogService.LOG_WARNING, "Invalid bundle:" + pair.toString() + " the symbolic name is empty.");
                    }
                    else {
                        result.add(new ArtifactDataImpl(pair.getUrl(), directives, symbolicName, bundleVersion, true));
                    }
                }
            }
            else {
                // it is an artifact.
                result.add(new ArtifactDataImpl(pair.getUrl(), directives, true));
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
    private ArtifactData getBundleData(URL url, Collection<ArtifactData> data) {
        ArtifactData bundle = null;
        Iterator<ArtifactData> it = data.iterator();
        while (it.hasNext()) {
            bundle = it.next();
            if (bundle.getUrl().equals(url)) {
                return bundle;
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
    private ArtifactData getBundleData(String symbolicName, Collection<ArtifactData> data) {
        ArtifactData bundle = null;
        Iterator<ArtifactData> it = data.iterator();
        while (it.hasNext()) {
            bundle = it.next();
            if ((bundle.getSymbolicName() != null) && bundle.getSymbolicName().equals(symbolicName)) {
                return bundle;
            }
        }
        return null;
    }

    /**
     * Returns the available deployment versions for a gateway
     *
     * @param input A dom document representation of the repository
     * @param gatewayId The gatwayId
     * @return A list of available versions
     */
    private List<Version> getAvailableVersions(InputStream input, String gatewayId) throws IllegalArgumentException {
        //result list
        List<Version> versionList = new ArrayList<Version>();
        XPathContext context = XPathContext.getInstance();

        try {
            NodeList versions = context.getVersions(gatewayId, input);
            if (versions != null) {
                for (int j = 0; j < versions.getLength(); j++) {
                    Node n = versions.item(j);
                    String versionValue = n.getTextContent();
                    try {
                        Version version = Version.parseVersion(versionValue);
                        // no exception, but is could still be an empty version
                        if (!version.equals(Version.emptyVersion)) {
                            versionList.add(version);
                        }
                    }
                    catch (NumberFormatException nfe) {
                        // Ignore this version
                        m_log.log(LogService.LOG_WARNING, "Deploymentversion ignored: ", nfe);
                    }
                }
            }

            return versionList;
        }
        catch (XPathExpressionException xee) {
            throw new IllegalArgumentException(xee);
        }
        finally {
            context.destroy();
        }
    }

    /*
     * This class is used to cache compiled xpath expression for the queries we need on a per thread basis. In order
     * to do this a thread local is used to cache an instance of this class per calling thread. The reference to this
     * instance is wrapped in a soft reference to make it possible to GC the instance in case memory is low.
     * <p>
     * Example Usage:
     * <pre>
     * XPathContext context = XPathContext.getInstance();
     *
     * try
     * {
     *     // to get all artifacts of a number of versions:
     *     context.init(gatewayId, versions, input);
     *
     *     for (int i = 0; i < versions.length; i++)
     *     {
     *         Node version = context.getVersion(i);
     *         // Do work with version
     *     }
     *     // to get all versions of a number of a gateway:
     *     NodeList versions = context.getVersions(gatewayId, input);
     *     // Do wortk with versions
     * }
     * finally
     * {
     *     context.destory();
     * }
     * </pre>
     */
    private static final class XPathContext implements XPathVariableResolver {
        private static final ThreadLocal<SoftReference<XPathContext>> m_cache = new ThreadLocal<SoftReference<XPathContext>>();

        private final XPath m_xPath = XPathFactory.newInstance().newXPath();

        private final XPathExpression m_attributesExpression;

        private final XPathExpression m_versionsExpression;

        private final Map<Integer, XPathExpression> m_expressions = new HashMap<Integer, XPathExpression>();

        private String m_gatewayId;

        private String[] m_versions;

        private Node m_node;

        private String m_version;

        private XPathContext() {
            m_xPath.setXPathVariableResolver(this);
            try {
                m_attributesExpression = m_xPath.compile("//deploymentversions/deploymentversion/attributes/child::gatewayID[text()=$id]/../child::version[text()=$version]/../../artifacts");
                m_versionsExpression = m_xPath.compile("//deploymentversions/deploymentversion/attributes/child::gatewayID[text()=$id]/parent::attributes/version/text()");
            }
            catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @return A thread local instance from the cache.
         */
        public static XPathContext getInstance() {
            SoftReference<XPathContext> ref = m_cache.get();
            XPathContext instance = null;
            if (ref != null) {
                instance = ref.get();
            }

            if (instance == null) {
                ref = null;
                instance = new XPathContext();
            }

            if (ref == null) {
                m_cache.set(new SoftReference<XPathContext>(instance));
            }

            return instance;
        }

        /**
         * @param gatewayId the id of the gateway
         * @param input the stream to read repository from
         * @return  the versions in the repo for the given gatewayId or null if none
         * @throws XPathExpressionException in case something goes wrong
         */
        public NodeList getVersions(String gatewayId, InputStream input) throws XPathExpressionException {
            m_gatewayId = gatewayId;
            return (NodeList) m_versionsExpression.evaluate(new InputSource(input), XPathConstants.NODESET);
        }

        /**
         * @param gatewayId the id of the gateway
         * @param versions the versions to return
         * @param input the stream to read repository from
         * @return true if versions can be found, otherwise false
         * @throws XPathExpressionException if something goes wrong
         */
        @SuppressWarnings("boxing")
        public boolean init(String gatewayId, String[] versions, InputStream input) throws XPathExpressionException {
            XPathExpression expression = m_expressions.get(versions.length);
            if (expression == null) {
                StringBuilder versionStatement = new StringBuilder("//deploymentversions/deploymentversion/attributes/child::gatewayID[text()=$id]/following::version[text()=$0");
                for (int i = 1; i < versions.length; i++) {
                    versionStatement.append(" or ").append(".=$").append(i);
                }
                versionStatement.append("]/../../..");
                expression = m_xPath.compile(versionStatement.toString());
                m_expressions.put(versions.length, expression);
            }
            m_gatewayId = gatewayId;
            m_versions = versions;

            m_node = (Node) expression.evaluate(new InputSource(input), XPathConstants.NODE);
            return (m_node != null);
        }

        /**
         *  @param i the index into the versions form init
         *  @return the version at index i
         * @throws XPathExpressionException if something goes wrong
         */
        public Node getVersion(int i) throws XPathExpressionException {
            m_version = m_versions[i];
            return (Node) m_attributesExpression.evaluate(m_node, XPathConstants.NODE);
        }

        /**
         * reset this thread local instance
         */
        public void destroy() {
            m_node = null;
            m_version = null;
            m_gatewayId = null;
            m_versions = null;
        }

        /**
         * @param name id|version|<version-index>
         * @return id->gatewayId | version->version | version-index -> versions[version-index]
         */
        public Object resolveVariable(QName name) {
            String localPart = name.getLocalPart();
            if ("id".equals(localPart)) {
                return m_gatewayId;
            }
            else if ("version".equals(localPart)) {
                return m_version;
            }
            return m_versions[Integer.parseInt(localPart)];
        }
    }

    /**
     * Helper method to retrieve urls and directives for a gateway-version combination.
     *
     * @param input An input stream from which an XML representation of a deployment repository can be read.
     * @param gatewayId The gatewayId to be used
     * @param versions An array of versions.
     * @return An array of lists of URLDirectivePairs. For each version in <code>versions</code>, a separate list will be
     *         created; the index of a version in the <code>versions</code> array is equal to the index of its result in the
     *         result array.
     * @throws IllegalArgumentException if the gatewayId or versions cannot be found in the input stream, or if
     *         <code>input</code> does not contain an XML stream.
     */
    @SuppressWarnings("unchecked")
    private List<URLDirectivePair>[] getURLDirectivePairs(InputStream input, String gatewayId, String[] versions) throws IllegalArgumentException {

        XPathContext context = XPathContext.getInstance();
        List<URLDirectivePair>[] result = new List[versions.length]; //unfortunately, we cannot use a typed list array.

        try {
            if (!context.init(gatewayId, versions, input)) {
                m_log.log(LogService.LOG_WARNING, "Versions not found for Gateway: " + gatewayId);
                throw new IllegalArgumentException("Versions not found.");
            }
            for (int i = 0; i < versions.length; i++) {
                result[i] = new ArrayList<URLDirectivePair>();

                // find all artifacts for the version we're currently working on.
                Node artifactNode = null;
                try {
                    artifactNode = context.getVersion(i);
                }
                catch (XPathExpressionException e) {
                    m_log.log(LogService.LOG_WARNING, "Version " + versions[i] + " not found for Gateway: " + gatewayId);
                    continue;
                }
                NodeList artifacts = artifactNode.getChildNodes();
                // Read the artifacts
                for (int artifactNumber = 0; artifactNumber < artifacts.getLength(); artifactNumber++) {
                    Node artifact = artifacts.item(artifactNumber);

                    NodeList artifactElements = artifact.getChildNodes();

                    String url = null;
                    Map<String, String> directives = new HashMap<String, String>();

                    for (int elementNumber = 0; elementNumber < artifactElements.getLength(); elementNumber++) {
                        // find the attributes of this artifact we are interested in.
                        Node element = artifactElements.item(elementNumber);

                        if (element.getNodeName().equals("url")) {
                            url = element.getTextContent();
                        }
                        else if (element.getNodeName().equals("directives")) {
                            // we found the directives? put all of them into our map.
                            NodeList directivesElements = element.getChildNodes();
                            for (int nDirective = 0; nDirective < directivesElements.getLength(); nDirective++) {
                                Node directivesElement = directivesElements.item(nDirective);
                                if (!"#text".equals(directivesElement.getNodeName())) {
                                    directives.put(directivesElement.getNodeName(), directivesElement.getTextContent());
                                }
                            }
                        }
                    }

                    if (url != null) {
                        try {
                            result[i].add(new URLDirectivePair(new URL(url), directives));
                        }
                        catch (MalformedURLException mue) {
                            m_log.log(LogService.LOG_WARNING, "The BundleUrl is malformed: ", mue);
                        }
                    }
                }
            }

            return result;
        }
        catch (XPathExpressionException ex) {
            throw new IllegalArgumentException(ex);
        }
        finally {
            context.destroy();
        }
    }

    /**
     * Helper to get an input stream to the currently used deployment repository.
     *
     * @return An input stream to the repository document. Will return an empty stream if none can be found.
     * @throws IOException if there is a problem communicating with the local or remote repository.
     */
    private InputStream getRepositoryStream() throws IOException {
        // cache the repositories, since we do not want them to change while we're in this method.
        CachedRepository cachedRepository = m_cachedRepository;
        Repository repository = m_directRepository;
        InputStream result;

        if (cachedRepository != null) {
            // we can use the cached repository
            if (cachedRepository.isCurrent()) {
                result = cachedRepository.getLocal(true);
            }
            else {
                result = cachedRepository.checkout(true);
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

        return result;
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            String url = getNotNull(settings, URL, "DeploymentRepository URL not configured.");
            String name = getNotNull(settings, NAME, "RepositoryName not configured.");
            String customer = getNotNull(settings, CUSTOMER, "RepositoryCustomer not configured.");

            //create the remote repository and set it.
            try {
                BackupRepository backup = null;
                try {
                    backup = new FilebasedBackupRepository(File.createTempFile("currentrepository", null), File.createTempFile("backuprepository", null));
                }
                catch (Exception e) {
                    m_log.log(LogService.LOG_WARNING, "Unable to create temporary files for FilebasedBackupRepository");
                }

                // We always create the remote repository. If we can create a backup repository, we will wrap a CachedRepository
                // around it.
                m_directRepository = new RemoteRepository(new URL(url), customer, name);
                if (backup == null) {
                    m_cachedRepository = null;
                }
                else {
                    m_cachedRepository = new CachedRepositoryImpl(null, m_directRepository, backup, CachedRepositoryImpl.UNCOMMITTED_VERSION);
                }
            }
            catch (MalformedURLException mue) {
                throw new ConfigurationException(URL, mue.getMessage());
            }
        }
    }

    /**
     * Convenience method for getting settings from a configuration dictionary.
     */
    @SuppressWarnings("unchecked")
    private String getNotNull(Dictionary settings, String id, String errorMessage) throws ConfigurationException {
        String result = (String) settings.get(id);
        if (result == null) {
            throw new ConfigurationException(id, errorMessage);
        }
        return result;
    }

    /**
     * Helper class to store a pair of URL and directive, in which the directive may be empty.
     */
    private class URLDirectivePair {
        final private URL m_url;

        final private Map<String, String> m_directives;

        URLDirectivePair(URL url, Map<String, String> directives) {
            m_url = url;
            m_directives = directives;
        }

        public URL getUrl() {
            return m_url;
        }

        public Map<String, String> getDirective() {
            return m_directives;
        }
    }
}
