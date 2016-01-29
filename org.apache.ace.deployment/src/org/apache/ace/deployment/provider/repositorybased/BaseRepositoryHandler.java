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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.osgi.framework.Version;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Provides a SAX-based push parser for obtaining all versions of the deployment packages of a certain target.
 */
class BaseRepositoryHandler extends DefaultHandler {

    private final String m_targetID;

    /** Denotes the current tag in the XML structure. */
    private XmlTag m_currentTag;
    /** Denotes the current version of the found target. */
    private Version m_currentVersion;
    /** Denotes whether or not the requested target is found. */
    private boolean m_targetFound;
    /** Denotes the current deployment artifact. */
    private XmlDeploymentArtifact m_currentArtifact;
    /** Denotes the directive key of the current deployment artifact. */
    private String m_currentDirectiveKey;
    /** Denotes the size of an artifact. */
    private long m_artifactSize;
    /** Denotes the actual URL to the artifact. */
    private URL m_artifactURL;
    /** To collect characters() */
    private final StringBuilder m_buffer;

    /**
     * Creates a new {@link BaseRepositoryHandler} instance.
     * 
     * @param targetID
     *            the target ID to search for, cannot be <code>null</code>.
     */
    public BaseRepositoryHandler(String targetID) {
        m_targetID = targetID;
        m_currentTag = XmlTag.unknown;
        m_buffer = new StringBuilder();
    }

    /**
     * Parses the given text as {@link Version}.
     * 
     * @param text
     *            the text to parse as version, can not be <code>null</code>.
     * @return a {@link Version} if the given text represent a correct version, never <code>null</code>.
     */
    static final Version parseVersion(String text) {
        try {
            if (text != null) {
                return Version.parseVersion(text);
            }
        }
        catch (Exception e) {
            // Ignore; simply return an empty version to denote this invalid version...
        }
        return Version.emptyVersion;
    }

    @Override
    public void startDocument() throws SAXException {
        m_currentTag = XmlTag.unknown;
        m_currentVersion = null;
        m_targetFound = false;
        m_currentArtifact = null;
        m_currentDirectiveKey = null;
        m_artifactURL = null;
        m_artifactSize = -1L;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        XmlTag tag = XmlTag.asXmlTag(qName);
        // If the given element is an expected child of the current tag, we
        // traverse deeper into the XML-hierarchy; otherwise, consider it an
        // "unknown"/uninteresting child and keep the current tag as-is...
        if (m_currentTag.isExpectedChild(tag)) {
            m_currentTag = tag;
        }

        m_currentDirectiveKey = null;
        // If we're parsing the directives of an artifact, take the name for
        // later use (the literal text in this tag will be used as value)...
        if (XmlTag.directives.equals(m_currentTag)) {
            m_currentDirectiveKey = qName;
        }
        if (XmlTag.size.equals(m_currentTag)) {
            m_artifactSize = -1L;
        }
        m_buffer.setLength(0);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        // just collect whatever comes along into our buffer (ACE-399)
        // see: http://stackoverflow.com/questions/4567636/java-sax-parser-split-calls-to-characters
        m_buffer.append(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String text = m_buffer.toString();

        if (XmlTag.targetID.equals(m_currentTag)) {
            // verify whether we're in the DP for the requested target...
            m_targetFound = m_targetID.equals(text);
        }
        else if (XmlTag.version.equals(m_currentTag)) {
            // Don't assume we've got the desired version (yet)...
            m_currentVersion = null;

            if (m_targetFound) {
                m_currentVersion = parseAsVersion(text);
            }
        }
        else if (XmlTag.url.equals(m_currentTag)) {
            try {
                m_artifactURL = new URL(text);
            }
            catch (MalformedURLException e) {
                throw new SAXException("Unexpected URL!", e);
            }
        }
        else if (XmlTag.size.equals(m_currentTag)) {
            try {
                m_artifactSize = Long.valueOf(text);
            }
            catch (NumberFormatException exception) {
                m_artifactSize = -1L;
            }
        }
        else if (XmlTag.directives.equals(m_currentTag)) {
            if (m_currentArtifact == null) {
                m_currentArtifact = new XmlDeploymentArtifact(m_artifactURL, m_artifactSize);
            }
            String value = text.trim();
            if (m_currentDirectiveKey != null && !value.equals("")) {
                m_currentArtifact.m_directives.put(m_currentDirectiveKey, value);
            }
        }

        XmlTag tag = XmlTag.asXmlTag(qName);
        // When we're ending the current tag, traverse up to its parent...
        if (!XmlTag.unknown.equals(tag) && (m_currentTag == tag)) {
            m_currentTag = tag.getParent();
        }

        // Invoke the callbacks for events we're interested in...
        if (XmlTag.version.equals(tag)) {
            if (m_currentVersion != null && !Version.emptyVersion.equals(m_currentVersion)) {
                // Let the version be handled (if needed)...
                handleVersion(m_currentVersion);
            }

            // Let the currentVersion field as-is! We want to reuse it for the artifacts...
        }
        else if (XmlTag.deploymentArtifact.equals(tag)) {
            if (m_currentArtifact != null) {
                // push out the current deployment artifact...
                handleArtifact(m_currentVersion, m_currentArtifact);
            }

            m_currentArtifact = null;
            m_artifactURL = null;
            m_artifactSize = -1L;
        }
        else if (XmlTag.directives.equals(tag)) {
            m_currentDirectiveKey = null;
        }
    }

    /**
     * Allows subclasses to handle the given version of a target's deployment package.
     * <p>
     * By default, this method does nothing.
     * </p>
     * 
     * @param version
     *            the version found, never <code>null</code>.
     */
    protected void handleVersion(Version version) {
        // NO-op
    }

    /**
     * Allows subclasses to handle the given deployment artifact for the given version of the deployment package.
     * <p>
     * By default, this method does nothing.
     * </p>
     * 
     * @param version
     *            the version of the deployment package;
     * @param artifact
     *            the deployment artifact itself.
     */
    protected void handleArtifact(Version version, XmlDeploymentArtifact artifact) {
        // NO-op
    }

    /**
     * Parses the given text as {@link Version}.
     * 
     * @param text
     *            the text to parse as version, can not be <code>null</code>.
     * @return a {@link Version} if the given text represent a correct version, can be <code>null</code> in case of an
     *         incorrect/empty version..
     */
    protected final Version parseAsVersion(String text) {
        Version result = parseVersion(text);
        if (Version.emptyVersion.equals(result)) {
            return null;
        }
        return result;
    }

    /**
     * Helper class to store a pair of URL and directive, in which the directive may be empty.
     */
    public static class XmlDeploymentArtifact {
        private final URL m_url;
        private final long m_size;
        private final Map<String, String> m_directives;

        private XmlDeploymentArtifact(URL url, long size) {
            m_url = url;
            m_size = size;
            m_directives = new HashMap<>();
        }

        public long getSize() {
            return m_size;
        }

        public URL getUrl() {
            return m_url;
        }

        public Map<String, String> getDirective() {
            return m_directives;
        }
    }

    /**
     * Defines the structure of our XML (only the parts we're interested in).
     */
    public static enum XmlTag {
        targetID,
        version,
        attributes(targetID, version),
        url,
        size,
        directives,
        deploymentArtifact(url, size, directives),
        artifacts(deploymentArtifact),
        tags,
        deploymentversion(attributes, tags, artifacts),
        deploymentversions(deploymentversion),
        repository(deploymentversions),
        unknown(repository);

        private final XmlTag[] m_children;
        private XmlTag m_parent;

        private XmlTag(XmlTag... possibleChildren) {
            m_children = possibleChildren;
            // Update the children's parent...
            for (int i = 0; i < m_children.length; i++) {
                m_children[i].m_parent = this;
            }
        }

        /**
         * Returns the parent tag of this tag.
         * 
         * @return a parent tag, can be <code>null</code>.
         */
        public XmlTag getParent() {
            return m_parent;
        }

        /**
         * Returns whether the given XML tag is an expected child of this tag.
         * 
         * @param xmlTag
         *            the XML tag to test, cannot be <code>null</code>.
         * @return <code>true</code> if the given tag is an expected child of this tag, <code>false</code> otherwise.
         */
        public boolean isExpectedChild(XmlTag xmlTag) {
            for (int i = 0; i < m_children.length; i++) {
                if (xmlTag.equals(m_children[i])) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Provides a "safe" way of representing the given tag name as instance of this enum. If the given name does not
         * represent a defined tag, "unknown" will be returned.
         * 
         * @param name
         *            the XML tag-name to represent as enum value, cannot be <code>null</code>.
         * @return a {@link XmlTag} representation of the given tag-name, never <code>null</code>.
         */
        public static XmlTag asXmlTag(String name) {
            XmlTag[] values = { artifacts, attributes, deploymentArtifact, deploymentversion, deploymentversions, directives, repository, size, tags, targetID, url, version };
            for (int i = 0; i < values.length; i++) {
                if (values[i].name().equals(name)) {
                    return values[i];
                }
            }
            return XmlTag.unknown;
        }
    }
}
