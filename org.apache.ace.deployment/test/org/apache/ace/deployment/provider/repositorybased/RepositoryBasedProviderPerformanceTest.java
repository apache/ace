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

import java.io.StringWriter;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.ArtifactDataHelper;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RepositoryBasedProviderPerformanceTest {

    private static final String TAGS_TAG = "tags";
    private static final String VERSION_TAG = "version";
    private static final String TARGETID_TAG = "targetID";
    private static final String ARTIFACTS_TAG = "artifacts";
    private static final String ARTIFACT_TAG = "deploymentArtifact";
    private static final String URL_TAG = "url";
    private static final String DIRECTIVES_TAG = "directives";
    private static final String ATTRIBUTES_TAG = "attributes";
    private static final String DEPLOYMENTVERSION_TAG = "deploymentversion";

    private static final String TARGET = "target";
    private static final String RESOURCETARGET = "resource-target";
    public static final String KEY_SYMBOLICNAME = Constants.BUNDLE_SYMBOLICNAME;
    public static final String KEY_NAME = Constants.BUNDLE_NAME;
    public static final String KEY_VERSION = Constants.BUNDLE_VERSION;
    public static final String KEY_VENDOR = Constants.BUNDLE_VENDOR;
    public static final String KEY_RESOURCE_PROCESSOR_PID = "Deployment-ProvidesResourceProcessor";
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

    public static final String MIMETYPE = "application/vnd.osgi.bundle";

    private RepositoryBasedProvider m_backend;

    /**
     * Sets up for the test cases.
     */
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        // setup mock repository
        String range = "1-100000";
        Repository mock = new MockDeploymentRepository(range, generateHugeTestXml(), null);
        m_backend = new RepositoryBasedProvider();
        TestUtils.configureObject(m_backend, Repository.class, mock);
        TestUtils.configureObject(m_backend, LogService.class);
        TestUtils.configureObject(m_backend, ArtifactDataHelper.class, new NoOpArtifactDataHelper());
    }

    /**
     * Test the getBundleData for a single version, returning a single bundle, for a huge XML.
     */
    @Test(timeOut = 2000 /* millis */)
    public void testSingleBundleSingleVersionBundleDataFromHugeXml() throws Exception {
        // will parse the entire XML structure;
        // with XPath queries, it takes about 115 seconds of time;
        // with a SAX parser, it takes about 0.9(!) seconds of time.
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, "44.0.0");
        assert bundleData.size() == 1 : "Expected one bundle to be found, but found " + bundleData.size();
    }

    /**
     * Test the getBundleData for a single version, returning a single bundle, for a huge XML.
     */
    @Test(timeOut = 3000 /* millis */)
    public void testSingleBundleMultipleVersionBundleDataFromHugeXml() throws Exception {
        // will parse the entire XML structure;
        // with XPath queries, it takes about 115 seconds of time;
        // with a SAX parser, it takes about 0.9(!) seconds of time.
        Collection<ArtifactData> bundleData = m_backend.getBundleData(TARGET, "40.0.0", "44.6.0");
        assert bundleData.size() == 1 : "Expected one bundle to be found, but found " + bundleData.size();
    }

    /**
     * @return a "huge" XML repository, with lots of deployment versions (approx. 10000) for a single target.
     */
    private String generateHugeTestXml() throws Exception {
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        // create the root element
        Element root = doc.createElement("repository");
        doc.appendChild(root);

        // create the versions element
        Element versions = doc.createElement("deploymentversions");
        root.appendChild(versions);

        String bundleUrl = "file:///path/to/bundle1";
        String symName = "my-test-bundle1";

        // create deployment versions
        for (int i = 0; i < 10000; i++) {
            Version dpVersion = new Version(i / 100, i % 100, 0);
            Version bundleVersion = new Version(i / 100, i % 100, 1);
            versions.appendChild(generateDeploymentVersion(doc, TARGET, dpVersion.toString(),
                new String[] { bundleUrl, KEY_SYMBOLICNAME, symName, KEY_VERSION,
                    bundleVersion.toString() }));
        }

        String rpUrl = "file:///path/to/rp";
        String artUrl = "file:///path/to/artifact";
        String rpSymName = "my-test-rp1";

        versions.appendChild(generateDeploymentVersion(doc, RESOURCETARGET, "1.0.0",
            new String[] { bundleUrl, KEY_SYMBOLICNAME, symName, KEY_VERSION, "1.0.1" },
            new String[] { rpUrl, DIRECTIVE_ISCUSTOMIZER, "true", KEY_SYMBOLICNAME, rpSymName, KEY_VERSION, "1.0.2" },
            new String[] { artUrl, DIRECTIVE_KEY_PROCESSORID, "my.processor.pid" },
            new String[] { artUrl, DIRECTIVE_KEY_RESOURCE_ID, "Artifact2", DIRECTIVE_KEY_PROCESSORID, "my.processor.pid" }));

        // transform the document to string
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        DOMSource source = new DOMSource(doc);

        StringWriter sw = new StringWriter();
        StreamResult result = new StreamResult(sw);
        transformer.transform(source, result);

        return sw.toString();
    }

    /**
     * Helper method to create the description of a deploymentpacakge with given data.
     *
     * @param doc The document to add the version to.
     * @param targetText The targetId in the deploymentversion.
     * @param versionText The version in the deploymentversion.
     * @param data An array of data for the deployment artifact. [0] is the url, and each following item is
     *        first a directive key, and a directive value. For example,<br>
     *        <code>new String[] { "http://mybundle", "somedirective", "somevalue" }</code>
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
}
