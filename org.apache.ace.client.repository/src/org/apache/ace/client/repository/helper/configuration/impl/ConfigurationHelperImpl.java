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
package org.apache.ace.client.repository.helper.configuration.impl;

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
import org.apache.ace.client.repository.helper.configuration.ConfigurationHelper;
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

public class ConfigurationHelperImpl implements ArtifactRecognizer, ConfigurationHelper {

    static class MetaDataNamespaceCollector extends DefaultHandler {
        private String m_metaDataNameSpace = "";

        public String getMetaDataNamespace() {
            return m_metaDataNameSpace;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.equals("MetaData") || qName.endsWith(":MetaData")) {
                String nsAttributeQName = "xmlns";
                if (qName.endsWith(":MetaData")) {
                    nsAttributeQName = "xmlns" + ":" + qName.split(":")[0];
                }
                for (int i = 0; i < attributes.getLength(); i++) {
                    if (attributes.getQName(i).equals(nsAttributeQName)) {
                        m_metaDataNameSpace = attributes.getValue(i);
                    }
                }
            }
            // first element is expected to have been the MetaData
            // root so we can now terminate processing.
            throw new SAXException("Done");
        }
    }

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

    // known valid metatype namespaces
    private static final String NAMESPACE_1_0 = "http://www.osgi.org/xmlns/metatype/v1.0.0";
    private static final String NAMESPACE_1_1 = "http://www.osgi.org/xmlns/metatype/v1.1.0";
    private static final String NAMESPACE_1_2 = "http://www.osgi.org/xmlns/metatype/v1.2.0";

    private final SAXParserFactory m_saxParserFactory;
    // Injected by Dependency Manager
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogService m_log;

    // Created in #start()
    private volatile VelocityArtifactPreprocessor m_artifactPreprocessor;

    public ConfigurationHelperImpl() {
        m_saxParserFactory = SAXParserFactory.newInstance();
        m_saxParserFactory.setNamespaceAware(false);
        m_saxParserFactory.setValidating(false);
    }

    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public boolean canUse(ArtifactObject object) {
        return MIMETYPE.equals(object.getMimetype());
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        // All necessary checks will be done by the constructor using getMandatoryAttributes.
        return attributes;
    }

    public Map<String, String> extractMetaData(ArtifactResource artifact) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<>();
        result.put(ArtifactObject.KEY_PROCESSOR_PID, PROCESSOR);
        result.put(ArtifactObject.KEY_MIMETYPE, MIMETYPE);
        String name = new File(artifact.getURL().getFile()).getName();
        String key = KEY_FILENAME + "-";
        int idx = name.indexOf(key);
        if (idx > -1) {
            int endIdx = name.indexOf("-", idx + key.length());
            name = name.substring(idx + key.length(), (endIdx > -1) ? endIdx : (name.length() - getExtension(artifact).length()));
        }
        result.put(ArtifactObject.KEY_ARTIFACT_NAME, name);
        result.put(KEY_FILENAME, name);
        return result;
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return "(" + KEY_FILENAME + "=" + obj.getAttribute(KEY_FILENAME) + ")";
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[] { KEY_FILENAME };
    }

    public String getExtension(ArtifactResource artifact) {
        return ".xml";
    }

    public String[] getMandatoryAttributes() {
        return new String[] { KEY_FILENAME };
    }

    public ArtifactPreprocessor getPreprocessor() {
        return m_artifactPreprocessor;
    }

    public String recognize(ArtifactResource artifact) {
        MetaDataNamespaceCollector handler = new MetaDataNamespaceCollector();
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
            // Ignore, we're only interested in the results contained in the handler...
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException e) {
                    // Ignore...
                }
            }
        }
        String namespace = handler.getMetaDataNamespace();
        if (NAMESPACE_1_0.equals(namespace) || NAMESPACE_1_1.equals(namespace) || NAMESPACE_1_2.equals(namespace)) {
            return MIMETYPE;
        }
        return null;
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
