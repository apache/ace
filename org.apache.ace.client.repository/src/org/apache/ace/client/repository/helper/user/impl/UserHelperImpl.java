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
package org.apache.ace.client.repository.helper.user.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.ArtifactResource;
import org.apache.ace.client.repository.helper.base.VelocityArtifactPreprocessor;
import org.apache.ace.client.repository.helper.user.UserAdminHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.service.log.LogService;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class UserHelperImpl implements ArtifactRecognizer, UserAdminHelper {

    static class LoggingErrorHandler implements ErrorHandler {
        private final ArtifactResource m_resource;
        private final LogService m_log;

        public LoggingErrorHandler(ArtifactResource resource, LogService log) {
            m_resource = resource;
            m_log = log;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
            log(LogService.LOG_DEBUG, "Artifact '" + getName() + "' contains a warning!", exception);
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            log(LogService.LOG_DEBUG, "Artifact '" + getName() + "' contains an error!", exception);
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            log(LogService.LOG_DEBUG, "Artifact '" + getName() + "' contains a fatal error!", exception);
        }

        private String getName() {
            URL url = m_resource.getURL();
            try {
                if ("file".equals(url.getProtocol())) {
                    return new File(url.toURI()).getName();
                }
            }
            catch (URISyntaxException exception) {
                // Ignore; fall through to return complete name...
            }
            return url.getFile();
        }

        private void log(int level, String msg, Exception exception) {
            if (m_log != null) {
                m_log.log(level, msg.concat(" ").concat(exception.getMessage()), exception);
            }
        }
    }

    static class UserAdminXmlHandler extends DefaultHandler {
        private boolean m_appearsValid = false;
        private boolean m_rolesTagSeen = false;
        private boolean m_groupOrUserTagSeen = false;

        public boolean appearsValid() {
            return m_appearsValid;
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (m_rolesTagSeen && isQName("roles", qName)) {
                m_rolesTagSeen = false;
            }
            if (m_groupOrUserTagSeen && (isQName("group", qName) || isQName("user", qName))) {
                m_groupOrUserTagSeen = false;
            }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (isQName("roles", qName)) {
                m_rolesTagSeen = true;
            }
            else if (m_rolesTagSeen) {
                if (!m_groupOrUserTagSeen) {
                    if (isQName("group", qName) || isQName("user", qName)) {
                        m_groupOrUserTagSeen = true;
                        m_appearsValid = true;
                    }
                    else {
                        m_appearsValid = false;
                        // Unexpected tag...
                        throw new SAXException("Done");
                    }
                }
                else {
                    // inside a group or user tag we do not care about the tags...
                }
            }
        }

        private boolean isQName(String expected, String name) {
            return (name != null) && (expected.equals(name) || name.endsWith(":".concat(expected)));
        }
    }

    private final SAXParserFactory m_saxParserFactory;
    // Injected by Dependency Manager
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogService m_log;

    // Created in #start()
    private volatile VelocityArtifactPreprocessor m_artifactPreprocessor;

    /**
     * Creates a new {@link UserHelperImpl} instance.
     */
    public UserHelperImpl() {
        m_saxParserFactory = SAXParserFactory.newInstance();
        m_saxParserFactory.setNamespaceAware(false);
        m_saxParserFactory.setValidating(false);
    }

    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public Map<String, String> extractMetaData(ArtifactResource artifact) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<>();
        result.put(ArtifactObject.KEY_PROCESSOR_PID, PROCESSOR);
        result.put(ArtifactObject.KEY_MIMETYPE, MIMETYPE);
        String name = new File(artifact.getURL().getFile()).getName();
        String key = ArtifactObject.KEY_ARTIFACT_NAME + "-";
        int idx = name.indexOf(key);
        if (idx > -1) {
            int endIdx = name.indexOf("-", idx + key.length());
            name = name.substring(idx + key.length(), (endIdx > -1) ? endIdx : (name.length() - getExtension(artifact).length()));
        }
        result.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        return result;
    }

    public String recognize(ArtifactResource artifact) {
        UserAdminXmlHandler handler = new UserAdminXmlHandler();
        InputStream input = null;
        try {
            input = artifact.openStream();
            SAXParser parser = m_saxParserFactory.newSAXParser();

            XMLReader reader = parser.getXMLReader();
            reader.setErrorHandler(new LoggingErrorHandler(artifact, m_log));
            reader.setContentHandler(handler);
            reader.parse(new InputSource(input));
        }
        catch (Exception e) {
            // Ignore, we're only detecting whether or not it is a valid XML file that resembles our scheme...
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException exception) {
                    // Ignore...
                }
            }
        }

        return handler.appearsValid() ? MIMETYPE : null;
    }

    public boolean canUse(ArtifactObject object) {
        return MIMETYPE.equals(object.getMimetype());
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        // All necessary checks will be done by the constructor using getMandatoryAttributes.
        return attributes;
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return "(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + obj.getAttribute(ArtifactObject.KEY_ARTIFACT_NAME) + ")";
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[] { ArtifactObject.KEY_ARTIFACT_NAME };
    }

    public String[] getMandatoryAttributes() {
        return new String[] { ArtifactObject.KEY_ARTIFACT_NAME };
    }

    public ArtifactPreprocessor getPreprocessor() {
        return m_artifactPreprocessor;
    }

    public String getExtension(ArtifactResource artifact) {
        return ".xml";
    }

    /**
     * Called by dependency manager upon start of this component.
     */
    protected void start() {
        m_artifactPreprocessor = new VelocityArtifactPreprocessor(m_connectionFactory);
    }

    /**
     * Called by dependency manager upon stopping of this component.
     */
    protected void stop() {
        m_artifactPreprocessor = null;

    }

    /**
     * @param log
     *            the log service to set, can be <code>null</code>.
     */
    final void setLog(LogService log) {
        m_log = log;
    }
}
