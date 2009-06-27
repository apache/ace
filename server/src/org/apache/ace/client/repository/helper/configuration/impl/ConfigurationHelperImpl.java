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
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.ArtifactRecognizer;
import org.apache.ace.client.repository.helper.base.VelocityArtifactPreprocessor;
import org.apache.ace.client.repository.helper.configuration.ConfigurationHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class ConfigurationHelperImpl implements ArtifactRecognizer, ConfigurationHelper {

    public boolean canHandle(String mimetype) {
        return MIMETYPE.equals(mimetype);
    }

    public Map<String, String> extractMetaData(URL artifact) throws IllegalArgumentException {
        Map<String, String> result = new HashMap<String, String>();
        result.put(KEY_FILENAME, new File(artifact.getFile()).getName());
        result.put(ArtifactObject.KEY_PROCESSOR_PID, PROCESSOR);
        result.put(ArtifactObject.KEY_MIMETYPE, MIMETYPE);
        result.put(ArtifactObject.KEY_ARTIFACT_NAME, result.get(KEY_FILENAME));
        return result;
    }

    public String recognize(URL artifact) {
        try {
            InputStream in = artifact.openStream();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            Node first = doc.getFirstChild();
            NamedNodeMap attributes = first.getAttributes();
            Node metatype = attributes.getNamedItem("xmlns:metatype");
            if (new String("http://www.osgi.org/xmlns/metatype/v1.0.0").equals(metatype.getTextContent())) {
                return MIMETYPE;
            }
        }
        catch (Exception e) {
            // Does not matter.
        }

        return null;
    }

    public boolean canUse(ArtifactObject object) {
        return MIMETYPE.equals(object.getMimetype());
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        // All necessary checks will be done by the constructor using getMandatoryAttributes.
        return attributes;
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
        return new String[] {KEY_FILENAME};
    }

    public String[] getMandatoryAttributes() {
        return new String[] {KEY_FILENAME};
    }

    private final static VelocityArtifactPreprocessor VELOCITY_ARTIFACT_PREPROCESSOR = new VelocityArtifactPreprocessor();
    public ArtifactPreprocessor getPreprocessor() {
        return VELOCITY_ARTIFACT_PREPROCESSOR;
    }
}
