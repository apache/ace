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
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.ArtifactDataHelper;
import org.apache.ace.deployment.provider.impl.ArtifactDataImpl;
import org.apache.ace.deployment.util.test.BundleStreamGenerator;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * This test class tests the Repositorybased Provider class. This class implements 2 backend interfaces, and both are
 * tested here.
 */
public class RepositoryBasedProviderTest {

    private static final String TAGS_TAG = "tags";
    private static final String VERSION_TAG = "version";
    private static final String TARGETID_TAG = "targetID";
    private static final String ARTIFACTS_TAG = "artifacts";
    private static final String ARTIFACT_TAG = "deploymentArtifact";
    private static final String URL_TAG = "url";
    private static final String SIZE_TAG = "size";
    private static final String DIRECTIVES_TAG = "directives";
    private static final String ATTRIBUTES_TAG = "attributes";
    private static final String DEPLOYMENTVERSION_TAG = "deploymentversion";
    public static final String KEY_SYMBOLICNAME = Constants.BUNDLE_SYMBOLICNAME;
    public static final String KEY_NAME = Constants.BUNDLE_NAME;
    public static final String KEY_VERSION = Constants.BUNDLE_VERSION;
    public static final String KEY_VENDOR = Constants.BUNDLE_VENDOR;
    public static final String KEY_RESOURCE_PROCESSOR_PID = "Deployment-ProvidesResourceProcessor";
    /**
     * Key, intended to be used for artifacts which are bundles and will publish a resource processor (see OSGi
     * compendium section 114.10).
     */
    public static final String DIRECTIVE_ISCUSTOMIZER = "DeploymentPackage-Customizer";

    /**
     * Key, intended to be used for resources which require a resource processor (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_KEY_PROCESSORID = "Resource-Processor";

    /**
     * Key, intended to be used for artifacts which have a resourceID that's different from their generated name (based
     * on URL).
     */
    public static final String DIRECTIVE_KEY_RESOURCE_ID = "Resource-ID";

    /**
     * Key, intended to be used for matching processed (see ArtifactPreprocessor) to their 'original' one.
     */
    public static final String DIRECTIVE_KEY_BASEURL = "Base-Url";

    public static final String REPOSITORY_PATH = "ACE-RepositoryPath";

    private RepositoryBasedProvider m_backend;

    private File m_tempDirectory;

    private final String VERSION1 = "1.0.0";
    private final String VERSION2 = "2.0.0";
    private final String VERSION3 = "3.0.0";
    private final String VERSION4 = "4.0.0";
    private final String INVALIDVERSION = "Invalid.version.directory";

    private final String TARGET = "target";
    private final String MULTIPLEVERSIONTARGET = "multi-version-target";
    private final String INVALIDVERSIONTARGET = "illegal-version-target";
    private final String EMPTYVERSIONTARGET = "empty-version-target";
    private final String RESOURCETARGET = "resource-target";

    private ArtifactData BUNDLE1;
    private ArtifactData BUNDLE3;
    private ArtifactData BUNDLE4;
    private ArtifactData BUNDLE4_1;
    private ArtifactData BUNDLE5;
    private ArtifactData BUNDLE3_2;
    private ArtifactData BUNDLE4_2;

    private ArtifactData RESOURCEPROCESSOR1;
    private ArtifactData ARTIFACT1;
    private ArtifactData ARTIFACT2;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {

        // first create a file
        m_tempDirectory = FileUtils.createTempFile(null);
        // and make a directory with that name.
        m_tempDirectory.mkdir();

        // generate sample data
        setupSampleData();
        String deploymentRepositoryXml = generateValidTestXml();
        String range = "1,2,3";

        // setup mock repository
        Repository mock = new MockDeploymentRepository(range, deploymentRepositoryXml, null);
        m_backend = new RepositoryBasedProvider();
        TestUtils.configureObject(m_backend, Repository.class, mock);
        TestUtils.configureObject(m_backend, LogService.class);
        TestUtils.configureObject(m_backend, ArtifactDataHelper.class, new NoOpArtifactDataHelper());
    }

    /**
     * make a bundle with the given symbolic name and version in the given file.
     */
    private ArtifactData generateBundle(File file, Map<String, String> directives, String symbolicName, String version,
        Map<String, String> additionalHeaders) throws Exception {
        // create a mock bundle, which is only used to generate the bundle on disk, and not used for anything else...
        ArtifactData bundle = new ArtifactDataImpl(file.toURI().toURL(), directives, symbolicName, -1L, version, false);
        if (additionalHeaders == null) {
            BundleStreamGenerator.generateBundle(bundle);
        }
        else {
            BundleStreamGenerator.generateBundle(bundle, additionalHeaders);
        }
        return bundle;
    }

    /**
     * Create the testbundles in the tempdirectory
     */
    private void setupSampleData() throws Exception {
        BUNDLE1 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle1", "1.0.0", null);
        BUNDLE3 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle3", "3.0.0", null);
        BUNDLE4 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle4", "4.0.0", null);
        BUNDLE4_1 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle4", "4.1.0", null);
        BUNDLE5 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle5;singleton:=true", "5.0.0", null);
        BUNDLE3_2 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle3", "3.0.0", null);
        BUNDLE4_2 = generateBundle(FileUtils.createTempFile(m_tempDirectory), null, "Bundle4", "5.0.0", null);

        Map<String, String> attr = new HashMap<>();
        attr.put(DIRECTIVE_ISCUSTOMIZER, "true");
        RESOURCEPROCESSOR1 = generateBundle(FileUtils.createTempFile(m_tempDirectory), attr, "Autoconf", "1.0.0", null);
        attr = new HashMap<>();
        attr.put(DIRECTIVE_KEY_PROCESSORID, "my.processor.pid");
        ARTIFACT1 = new ArtifactDataImpl(FileUtils.createTempFile(m_tempDirectory).toURI().toURL(), attr, false);
        attr = new HashMap<>();
        attr.put(DIRECTIVE_KEY_PROCESSORID, "my.processor.pid");
        attr.put(DIRECTIVE_KEY_RESOURCE_ID, "Artifact2");
        ARTIFACT2 = new ArtifactDataImpl(FileUtils.createTempFile(m_tempDirectory).toURI().toURL(), attr, false);
    }

    private String generateValidTestXml() {
        Document doc = null;
        try {
            DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
            doc = docBuilder.newDocument();
        }
        catch (ParserConfigurationException e) {
            // Should not happen
            e.printStackTrace();
        }

        // create the root element
        Element root = doc.createElement("repository");
        doc.appendChild(root);

        // create the versions element
        Element versions = doc.createElement("deploymentversions");
        root.appendChild(versions);

        // create deployment versions
        versions.appendChild(generateDeploymentVersion(doc, TARGET, VERSION1, new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() }));
        versions
            .appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION1, new String[] {
                BUNDLE3.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE3.getSymbolicName(),
                KEY_VERSION, BUNDLE3.getVersion() },
                new String[] { BUNDLE4.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE4.getSymbolicName(),
                    KEY_VERSION, BUNDLE4.getVersion() }));
        versions
            .appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION2, new String[] {
                BUNDLE4_1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE4_1.getSymbolicName(),
                KEY_VERSION, BUNDLE4_1.getVersion() },
                new String[] { BUNDLE5.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE5.getSymbolicName(),
                    KEY_VERSION, BUNDLE5.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION3, new String[] {
            BUNDLE4.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE4.getSymbolicName(),
            KEY_VERSION, BUNDLE4.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION4, new String[] {
            BUNDLE3_2.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE3_2.getSymbolicName(),
            KEY_VERSION, BUNDLE3_2.getVersion() },
            new String[] { BUNDLE4_2.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE4_2.getSymbolicName(),
                KEY_VERSION, BUNDLE4_2.getVersion() }));
        // Add versions with special characters like ' " < >
        versions
            .appendChild(generateDeploymentVersion(doc, "'", VERSION1,
                new String[] { BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
                    KEY_VERSION, BUNDLE1.getVersion() }));
        versions
            .appendChild(generateDeploymentVersion(doc, "\"", VERSION2,
                new String[] { BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
                    KEY_VERSION, BUNDLE1.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, "target'\"", VERSION3, new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, " '''' \"\"\"\" ", VERSION4, new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, "myTarget", "1'0'0", new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() }));

        // Add a valid deployment version (5.0.0) with no bundle urls (empty package)
        versions.appendChild(generateDeploymentVersion(doc, EMPTYVERSIONTARGET, VERSION1, new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() }));
        versions.appendChild(generateDeploymentVersion(doc, EMPTYVERSIONTARGET, VERSION2));

        versions.appendChild(generateDeploymentVersion(doc, RESOURCETARGET, VERSION1, new String[] {
            BUNDLE1.getUrl().toString(), KEY_SYMBOLICNAME, BUNDLE1.getSymbolicName(),
            KEY_VERSION, BUNDLE1.getVersion() },
            new String[] { RESOURCEPROCESSOR1.getUrl().toString(), DIRECTIVE_ISCUSTOMIZER, "true",
                KEY_SYMBOLICNAME, RESOURCEPROCESSOR1.getSymbolicName(), KEY_VERSION,
                RESOURCEPROCESSOR1.getVersion() },
            new String[] { ARTIFACT1.getUrl().toString(),
                DIRECTIVE_KEY_PROCESSORID, "my.processor.pid" },
            new String[] {
                ARTIFACT2.getUrl().toString(), DIRECTIVE_KEY_RESOURCE_ID, "Artifact2",
                DIRECTIVE_KEY_PROCESSORID, "my.processor.pid" }));

        // transform the document to string
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = null;
        StringWriter sw = null;
        try {
            transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            DOMSource source = new DOMSource(doc);
            sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            transformer.transform(source, result);
        }
        catch (TransformerConfigurationException e) {
            // Should not happen
            e.printStackTrace();
        }
        catch (TransformerException e) {
            // Should not happen
            e.printStackTrace();
        }

        return sw.toString();
    }

    /**
     * Helper method to create the description of a deploymentpacakge with given data.
     *
     * @param doc
     *            The document to add the version to.
     * @param targetText
     *            The targetId in the deploymentversion.
     * @param versionText
     *            The version in the deploymentversion.
     * @param data
     *            An array of data for the deployment artifact. [0] is the url, and each following item is first a
     *            directive key, and a directive value. For example,<br>
     *            <code>new String[] { "http://mybundle", "somedirective", "somevalue" }</code>
     * @return
     */
    private Node generateDeploymentVersion(Document doc, String targetText, String versionText, String[]... data) {
        Element deploymentversion = doc.createElement(DEPLOYMENTVERSION_TAG);
        Element attr = doc.createElement(ATTRIBUTES_TAG);
        deploymentversion.appendChild(attr);

        // Create and add targetId Tag
        Element elem = null;
        elem = doc.createElement(TARGETID_TAG);
        elem.setTextContent(targetText);
        attr.appendChild(elem);

        // Create and add versionTag
        elem = doc.createElement(VERSION_TAG);
        elem.setTextContent(versionText);
        attr.appendChild(elem);

        // Create and add empty tagsTag to deploymentversion
        elem = doc.createElement(TAGS_TAG);
        deploymentversion.appendChild(elem);

        // create and add bundlesTag
        elem = doc.createElement(ARTIFACTS_TAG);
        for (String[] s : data) {
            Element artifact = doc.createElement(ARTIFACT_TAG);
            Element url = doc.createElement(URL_TAG);
            url.setTextContent(s[0]);
            artifact.appendChild(url);
            Element size = doc.createElement(SIZE_TAG);
            size.setTextContent("100");
            artifact.appendChild(size);
            Element directives = doc.createElement(DIRECTIVES_TAG);
            for (int i = 1; i < s.length; i += 2) {
                Element directive = doc.createElement(s[i]);
                directive.setTextContent(s[i + 1]);
                directives.appendChild(directive);
            }
            artifact.appendChild(directives);
            elem.appendChild(artifact);
        }

        deploymentversion.appendChild(elem);

        return deploymentversion;
    }

    /**
     * Without any checked in data, we should just get back no version, but the provider should not crash.
     *
     * @throws java.io.IOException
     */
    @Test()
    public void testEmptyRepository() throws Exception {
        Repository mock = new MockDeploymentRepository("", null, null);
        TestUtils.configureObject(m_backend, Repository.class, mock);

        List<String> versions = m_backend.getVersions(TARGET);
        assert versions.size() == 0 : "From an empty repository, we should get 0 versions, but we get "
            + versions.size();
    }

    /**
     * See if the getVersions() methods normal output works
     */
    @Test()
    public void testGetVersion() throws Exception {
        List<String> versions = m_backend.getVersions(TARGET);
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION1) : "Expected version " + VERSION1 + " but found " + versions.get(0);
    }

    /**
     * Test the getVersions method with an illegal version (not in org.osgi.framework.Version format)
     */
    @Test()
    public void testIllegalVersion() throws Exception {
        // an illegal version should be silently ignored
        List<String> versions = m_backend.getVersions(INVALIDVERSIONTARGET);
        assert versions.isEmpty() : "Expected no versions to be found, but found " + versions.size();
    }

    /**
     * Test with multiple versions. It expects all versions in an ascending order.
     */
    @Test()
    public void testMultipleVersions() throws Exception {
        List<String> versions = m_backend.getVersions(MULTIPLEVERSIONTARGET);
        assert versions.size() == 4 : "Expected three version to be found, but found " + versions.size();
        // all versions should be in ascending order
        assert versions.get(0).equals(VERSION1) : "Expected version " + VERSION1 + " but found " + versions.get(0);
        assert versions.get(1).equals(VERSION2) : "Expected version " + VERSION2 + " but found " + versions.get(1);
        assert versions.get(2).equals(VERSION3) : "Expected version " + VERSION3 + " but found " + versions.get(2);
        assert versions.get(3).equals(VERSION4) : "Expected version " + VERSION4 + " but found " + versions.get(3);
    }

    /**
     * Test the getBundleData for a single version, returning a single bundle
     */
    @Test()
    public void testSingleBundleSingleVersionBundleData() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, VERSION1);
        assert bundleData.size() == 1 : "Expected one bundle to be found, but found " + bundleData.size();
        assert bundleData.contains(BUNDLE1) : "Expected to find bundle " + BUNDLE1.getSymbolicName();
    }

    /**
     * Test the getBundleData for a single version, returning a multiple bundles
     */
    @Test()
    public void testMultipleBundleSingleVersionBundleData() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        assert bundleData.contains(BUNDLE3) : "Expected to find bundle " + BUNDLE3.getSymbolicName();
        assert bundleData.contains(BUNDLE4) : "Expected to find bundle " + BUNDLE4.getSymbolicName();
    }

    /**
     * Test the getBundleData with an illegal version (i.e. a version that doesn't exist)
     */
    @Test()
    public void testInvalidVersionBundleData() throws Exception {
        try {
            m_backend.getBundleData(TARGET, INVALIDVERSION);
            assert false : "Expected an error because version " + INVALIDVERSION + " doesn't exist for target: "
                + TARGET;
        }
        catch (IllegalArgumentException iae) {
            // expected, because the version doesn't exist
        }
    }

    /**
     * Test the getBundleData with an illegal target (i.e. a target that doesn't exist)
     */
    @Test()
    public void testInvalidTargetBundleData() throws Exception {
        try {
            m_backend.getBundleData(INVALIDVERSIONTARGET, VERSION1);
            assert false : "Expected an error because version " + VERSION1 + " doesn't exist for target: "
                + INVALIDVERSIONTARGET;
        }
        catch (IllegalArgumentException iae) {
            // expected, because the version doesn't exist
        }
    }

    /**
     * Test the getBundleData for a two versions, returning a single bundle that hasn't changed
     */
    @Test()
    public void testSingleUnchangedBundleMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, VERSION1, VERSION1);
        assert bundleData.size() == 1 : "Expect one bundle, got " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            assert data.getSize() == 100 : "Bundle has no sensible size?! " + data.getSize();
            assert !data.hasChanged() : "The data should not have been changed.";
        }
    }

    /**
     * Test the getBundleData for a two versions, returning multiple bundles that haven't changed
     */
    @Test()
    public void testMultipleBundlesMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            assert !data.hasChanged() : "The data should not have been changed.";
        }
    }

    /**
     * Test the getBundleData for a two versions, where in the second version a bundle is removed
     */
    @Test()
    public void testRemovedBundleMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION3);
        assert bundleData.size() == 1 : "Expected one bundle to be found, but found " + bundleData.size();
    }

    /**
     * Test the getBundleData for a two versions, where in the second version a bundle is added
     */
    @Test()
    public void testAddedBundleMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION3, VERSION1);
        assert bundleData.size() == 2 : "Expected two bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.getSymbolicName().equals("Bundle4")) {
                assert !data.hasChanged() : "The data (Bundle4) should not have been changed.";
            }
            else {
                assert data.hasChanged() : "The data (Bundle3) should have been changed.";
            }
        }
    }

    /**
     * Test the getBundleData for a two versions, where in the second version one bundle has changed and another hasn't
     */
    @Test()
    public void testSingleChangedBundleMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION4);
        assert bundleData.size() == 2 : "Expected two bundles to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.equals(BUNDLE3_2)) {
                assert !data.hasChanged() : "The data should not have been changed.";
            }
            else if (data.equals(BUNDLE4_2)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else {
                assert false : "Unknown bundle found";
            }
        }
    }

    /**
     * Test the getBundleData for a two versions, where two bundles have changed
     */
    @Test()
    public void testMultipleChangedBundlesMultipleVersions() throws Exception {
        Collection<ArtifactData> bundleData = m_backend.getBundleData(MULTIPLEVERSIONTARGET, VERSION1, VERSION2);
        assert bundleData.size() == 2 : "Expected two bundles to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.equals(BUNDLE4_1)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else if (data.equals(BUNDLE5)) {
                assert data.hasChanged() : "The data should have been changed.";
            }
            else {
                assert false : "Unknown bundle found";
            }
        }
    }

    /**
     * See if the getVersions() methods normal output works with literals ' and "
     */
    @Test()
    public void testGetLiteralTargetVersion() throws Exception {
        List<String> versions = m_backend.getVersions("'");
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION1) : "Expected version " + VERSION1 + " but found " + versions.get(0);

        versions = m_backend.getVersions("\"");
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION2) : "Expected version " + VERSION2 + " but found " + versions.get(0);

        versions = m_backend.getVersions("target'\"");
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION3) : "Expected version " + VERSION3 + " but found " + versions.get(0);

        versions = m_backend.getVersions(" '''' \"\"\"\" ");
        assert versions.size() == 1 : "Expected one version to be found, but found " + versions.size();
        assert versions.get(0).equals(VERSION4) : "Expected version " + VERSION4 + " but found " + versions.get(0);
    }

    /**
     * Test the getBundleData for an empty version (no bundle URLS are included)
     */
    @Test()
    public void testEmptyDeploymentVersion() throws Exception {
        // get the version number
        List<String> versions = m_backend.getVersions(EMPTYVERSIONTARGET);
        assert versions.size() == 2 : "Expected two version to be found, but found " + versions.size();

        // get the (empty bundle data version (2))
        Collection<ArtifactData> bundleData = m_backend.getBundleData(EMPTYVERSIONTARGET, VERSION2);
        assert bundleData.size() == 0 : "Expected no bundles to be found, but got: " + bundleData.size();

        // check an update from and to an empty version
        Collection<ArtifactData> bundleData2 = m_backend.getBundleData(EMPTYVERSIONTARGET, VERSION1, VERSION2);
        assert bundleData2.size() == 0 : "Expected no bundles to be found, but got: " + bundleData2.size();

        Collection<ArtifactData> bundleData3 = m_backend.getBundleData(EMPTYVERSIONTARGET, VERSION2, VERSION1);
        assert bundleData3.size() == 1 : "Expected one bundle to be found, but got: " + bundleData3.size();
        assert bundleData3.iterator().next().getVersion().equals("1.0.0");
    }

    /**
     * See if a version with a literal is parsed correct and ignored.
     */
    @Test()
    public void testGetLiteralTargetIllegalVersion() throws Exception {
        List<String> versions = m_backend.getVersions("myTarget");
        assert versions.size() == 0 : "Expected no versions to be found, but found " + versions.size();
    }

    /**
     * Test the getBundleData with some resources.
     */
    @Test()
    public void testBundleDataWithResources() throws Exception {
        List<String> versions = m_backend.getVersions(RESOURCETARGET);
        assert versions.size() == 1 : "Expected two version to be found, but found " + versions.size();

        Collection<ArtifactData> bundleData = m_backend.getBundleData(RESOURCETARGET, versions.get(0));

        assert bundleData.size() == 4 : "Expected four bundle to be found, but found " + bundleData.size();
        Iterator<ArtifactData> it = bundleData.iterator();
        while (it.hasNext()) {
            ArtifactData data = it.next();
            if (data.equals(BUNDLE1)) {
                // fine
            }
            else if (data.equals(RESOURCEPROCESSOR1)) {
                // fine
            }
            else if (data.equals(ARTIFACT1)) {
                // fine
            }
            else if (data.equals(ARTIFACT2)) {
                // check the filename
                assert data.getFilename().equals("Artifact2");
                assert data.getProcessorPid().equals("my.processor.pid");
            }
            else {
                assert false : "Unknown bundle found";
            }
        }
    }

    @Test()
    public void testArtifactDataManifestGeneration() {
        Attributes B1NoFixpack = BUNDLE1.getManifestAttributes(false);
        assert B1NoFixpack.size() == 2;
        for (Map.Entry<Object, Object> entry : B1NoFixpack.entrySet()) {
            if (entry.getKey().toString().equals(Constants.BUNDLE_SYMBOLICNAME)) {
                assert entry.getValue().toString().equals(BUNDLE1.getSymbolicName());
            }
            else if (entry.getKey().toString().equals(Constants.BUNDLE_VERSION)) {
                assert entry.getValue().toString().equals(BUNDLE1.getVersion());
            }
            else {
                assert false : "Unknown header found: " + entry.getKey().toString();
            }
        }

        Attributes B1Fixpack = BUNDLE1.getManifestAttributes(true);
        assert B1Fixpack.size() == 3;
        for (Map.Entry<Object, Object> entry : B1Fixpack.entrySet()) {
            if (entry.getKey().toString().equals(Constants.BUNDLE_SYMBOLICNAME)) {
                assert entry.getValue().toString().equals(BUNDLE1.getSymbolicName());
            }
            else if (entry.getKey().toString().equals(Constants.BUNDLE_VERSION)) {
                assert entry.getValue().toString().equals(BUNDLE1.getVersion());
            }
            else if (entry.getKey().toString().equals("DeploymentPackage-Missing")) {
                assert entry.getValue().toString().equals("true");
            }
            else {
                assert false : "Unknown header found: " + entry.getKey().toString();
            }
        }

        Attributes R1NoFixpack = RESOURCEPROCESSOR1.getManifestAttributes(false);
        assert R1NoFixpack.size() == 3 : "We expect 3 headers, but find " + R1NoFixpack.size();
        for (Map.Entry<Object, Object> entry : R1NoFixpack.entrySet()) {
            if (entry.getKey().toString().equals(Constants.BUNDLE_SYMBOLICNAME)) {
                assert entry.getValue().toString().equals(RESOURCEPROCESSOR1.getSymbolicName());
            }
            else if (entry.getKey().toString().equals(Constants.BUNDLE_VERSION)) {
                assert entry.getValue().toString().equals(RESOURCEPROCESSOR1.getVersion());
            }
            else if (entry.getKey().toString().equals(DIRECTIVE_ISCUSTOMIZER)) {
                assert entry.getValue().toString().equals("true");
            }
            else {
                assert false : "Unknown header found: " + entry.getKey().toString();
            }
        }

        Attributes A1NoFixpack = ARTIFACT1.getManifestAttributes(false);
        assert A1NoFixpack.size() == 1 : "We expect 1 headers, but find " + A1NoFixpack.size();
        for (Map.Entry<Object, Object> entry : A1NoFixpack.entrySet()) {
            if (entry.getKey().toString().equals(DIRECTIVE_KEY_PROCESSORID)) {
                assert entry.getValue().toString().equals("my.processor.pid");
            }
            else {
                assert false : "Unknown header found: " + entry.getKey().toString();
            }
        }
    }

    @AfterTest(alwaysRun = true)
    public void tearDown() throws Exception {
        FileUtils.removeDirectoryWithContent(m_tempDirectory);
    }

}
