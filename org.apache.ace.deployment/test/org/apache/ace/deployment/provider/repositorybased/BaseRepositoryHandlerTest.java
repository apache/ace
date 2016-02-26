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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.ace.deployment.provider.repositorybased.BaseRepositoryHandler.XmlDeploymentArtifact;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Test cases for {@link BaseRepositoryHandler}.
 */
public class BaseRepositoryHandlerTest {

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
    private static final String MULTIPLEVERSIONTARGET = "multi-version-target";
    private static final String EMPTYVERSIONTARGET = "empty-version-target";

    private static final String VERSION1 = "1.0.0";
    private static final String VERSION2 = "2.0.0";
    private static final String VERSION3 = "3.0.0";
    private static final String VERSION3_2 = "3.2.0";
    private static final String VERSION4 = "4.0.0";
    private static final String VERSION4_1 = "4.1.0";
    private static final String VERSION4_2 = "4.2.0";
    private static final String VERSION5 = "5.0.0";

    public static final String KEY_SYMBOLICNAME = Constants.BUNDLE_SYMBOLICNAME;
    public static final String KEY_NAME = Constants.BUNDLE_NAME;
    public static final String KEY_VERSION = Constants.BUNDLE_VERSION;
    public static final String KEY_VENDOR = Constants.BUNDLE_VENDOR;
    public static final String KEY_RESOURCE_PROCESSOR_PID = "Deployment-ProvidesResourceProcessor";

    public static final String MIMETYPE = "application/vnd.osgi.bundle";

    private InputStream m_inputStream;
    private SAXParser m_parser;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        m_parser = saxParserFactory.newSAXParser();

        String xml = generateValidTestXml();
        m_inputStream = new ByteArrayInputStream(xml.getBytes());
    }

    /**
     * Tests that a deployment package with a single version can be parsed & found.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherSingleVersionOk() throws Exception {
        DeploymentPackageVersionCollector handler = new DeploymentPackageVersionCollector(TARGET);

        m_parser.parse(m_inputStream, handler);

        List<Version> versions = handler.getVersions();
        assert versions.size() == 1 : "Expected a single version to be found!";

        assert Version.parseVersion(VERSION1).equals(versions.get(0)) : "Expected version1 to be found!";
    }

    /**
     * Tests that the single artifact of a deployment package with a single version can be parsed & found.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherSingleArtifactOk() throws Exception {
        DeploymentArtifactCollector handler = new DeploymentArtifactCollector(TARGET, VERSION1);

        m_parser.parse(m_inputStream, handler);

        List<XmlDeploymentArtifact>[] artifacts = handler.getArtifacts();
        assert artifacts.length == 1 : "Expected a single artifact to be found!";
        assert artifacts[0].size() == 1 : "Expected a single artifact to be found!";

        XmlDeploymentArtifact artifact1 = artifacts[0].get(0);
        assert new URL("file:///bundle1").equals(artifact1.getUrl()) : "Expected 'file:///bundle1' URL to be found!";
        assert artifact1.getDirective().size() == 2 : "Expected two directives to be found!";
        assert "bundle1".equals(artifact1.getDirective().get(KEY_SYMBOLICNAME)) : "Expected correct symbolic name to be found!";
        assert "1.0.0".equals(artifact1.getDirective().get(KEY_VERSION)) : "Expected correct bundle version to be found!";
    }

    /**
     * Tests that a deployment package with multiple versions can be parsed & found.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherMultipleVersionOk() throws Exception {
        DeploymentPackageVersionCollector handler = new DeploymentPackageVersionCollector(MULTIPLEVERSIONTARGET);

        m_parser.parse(m_inputStream, handler);

        List<Version> versions = handler.getVersions();
        assert versions.size() == 4 : "Expected four versions to be found!";

        assert Version.parseVersion(VERSION1).equals(versions.get(0)) : "Expected version1 to be found!";
        assert Version.parseVersion(VERSION2).equals(versions.get(1)) : "Expected version2 to be found!";
        assert Version.parseVersion(VERSION3).equals(versions.get(2)) : "Expected version3 to be found!";
        assert Version.parseVersion(VERSION4).equals(versions.get(3)) : "Expected version4 to be found!";
    }

    /**
     * Tests that multiple artifacts of a deployment package with a single version can be parsed & found.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherMultipleArtifactsOfMultipleVersionTargetOk() throws Exception {
        DeploymentArtifactCollector handler = new DeploymentArtifactCollector(MULTIPLEVERSIONTARGET, VERSION2);

        m_parser.parse(m_inputStream, handler);

        List<XmlDeploymentArtifact>[] artifacts = handler.getArtifacts();
        assert artifacts.length == 1 : "Expected two artifacts to be found!";
        assert artifacts[0].size() == 2 : "Expected two artifacts to be found!";

        XmlDeploymentArtifact artifact1 = artifacts[0].get(0);
        assert new URL("file:///bundle4.1").equals(artifact1.getUrl()) : "Expected 'file:///bundle4.1' URL to be found!";
        assert artifact1.getDirective().size() == 2 : "Expected two directives to be found!";
        assert "bundle4.1".equals(artifact1.getDirective().get(KEY_SYMBOLICNAME)) : "Expected correct symbolic name to be found!";
        assert "4.1.0".equals(artifact1.getDirective().get(KEY_VERSION)) : "Expected correct bundle version to be found!";

        XmlDeploymentArtifact artifact2 = artifacts[0].get(1);
        assert new URL("file:///bundle5").equals(artifact2.getUrl()) : "Expected 'file:///bundle5' URL to be found!";
        assert artifact2.getDirective().size() == 2 : "Expected two directives to be found!";
        assert "bundle5".equals(artifact2.getDirective().get(KEY_SYMBOLICNAME)) : "Expected correct symbolic name to be found!";
        assert "5.0.0".equals(artifact2.getDirective().get(KEY_VERSION)) : "Expected correct bundle version to be found!";
    }

    /**
     * Tests that single artifact of a deployment package with multiple versions can be parsed & found.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherSingleArtifactsOfMultipleVersionTargetOk() throws Exception {
        DeploymentArtifactCollector handler = new DeploymentArtifactCollector(MULTIPLEVERSIONTARGET, VERSION3);

        m_parser.parse(m_inputStream, handler);

        List<XmlDeploymentArtifact>[] artifacts = handler.getArtifacts();
        assert artifacts.length == 1 : "Expected a single artifact to be found!";
        assert artifacts[0].size() == 1 : "Expected a single artifact to be found!";

        XmlDeploymentArtifact artifact1 = artifacts[0].get(0);
        assert new URL("file:///bundle4").equals(artifact1.getUrl()) : "Expected 'file:///bundle4' URL to be found!";
        assert artifact1.getDirective().size() == 2 : "Expected two directives to be found!";
        assert "bundle4".equals(artifact1.getDirective().get(KEY_SYMBOLICNAME)) : "Expected correct symbolic name to be found!";
        assert "4.0.0".equals(artifact1.getDirective().get(KEY_VERSION)) : "Expected correct bundle version to be found!";
    }

    /**
     * Tests that non existing artifacts of a deployment package with multiple versions can be parsed.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherNonExistingArtifactsOfMultipleVersionTargetOk() throws Exception {
        DeploymentArtifactCollector handler = new DeploymentArtifactCollector(EMPTYVERSIONTARGET, VERSION2);

        m_parser.parse(m_inputStream, handler);

        List<XmlDeploymentArtifact>[] artifacts = handler.getArtifacts();
        assert artifacts.length == 1 : "Expected a single artifact to be found!";
        assert artifacts[0].isEmpty() : "Expected no deployment artifacts to be found!";
    }

    /**
     * Tests that requesting the artifacts of a deployment package with an invalid version can be done.
     * 
     * @throws Exception
     *             not part of this test case.
     */
    @Test()
    public void testGatherArtifactsOfMultipleVersionTargetWithInvalidVersionOk() throws Exception {
        DeploymentArtifactCollector handler = new DeploymentArtifactCollector(EMPTYVERSIONTARGET, VERSION3);

        m_parser.parse(m_inputStream, handler);

        try {
            handler.getArtifacts();

            assert false : "Expected no deployment artifacts to be found!";
        }
        catch (IllegalArgumentException e) {
            // Ok; expected...
        }
    }

    /**
     * @return a valid repository XML; never <code>null</code>.
     */
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
        versions.appendChild(generateDeploymentVersion(doc, TARGET, VERSION1, new String[] { "file:///bundle1",
            KEY_SYMBOLICNAME, "bundle1", KEY_VERSION, VERSION1 }));

        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION1, new String[] {
            "file:///bundle3", KEY_SYMBOLICNAME, "bundle3", KEY_VERSION, VERSION3 },
            new String[] { "file:///bundle4", KEY_SYMBOLICNAME, "bundle4", KEY_VERSION,
                VERSION4 }));
        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION2, new String[] {
            "file:///bundle4.1", KEY_SYMBOLICNAME, "bundle4.1", KEY_VERSION, VERSION4_1 },
            new String[] { "file:///bundle5", KEY_SYMBOLICNAME, "bundle5", KEY_VERSION,
                VERSION5 }));
        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION3, new String[] {
            "file:///bundle4", KEY_SYMBOLICNAME, "bundle4", KEY_VERSION, VERSION4 }));
        versions.appendChild(generateDeploymentVersion(doc, MULTIPLEVERSIONTARGET, VERSION4, new String[] {
            "file:///bundle3.2", KEY_SYMBOLICNAME, "bundle3.2", KEY_VERSION, VERSION3_2 },
            new String[] { "file:///bundle4.2", KEY_SYMBOLICNAME, "bundle4.2", KEY_VERSION,
                VERSION4_2 }));

        // Add a valid deployment version (5.0.0) with no bundle urls (empty package)
        versions.appendChild(generateDeploymentVersion(doc, EMPTYVERSIONTARGET, VERSION1, new String[] {
            "file:///bundle1", KEY_SYMBOLICNAME, "bundle1", KEY_VERSION, VERSION1 }));
        versions.appendChild(generateDeploymentVersion(doc, EMPTYVERSIONTARGET, VERSION2));

        // transform the document to string
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = null;
        StringWriter sw = null;
        try {
            transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
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
