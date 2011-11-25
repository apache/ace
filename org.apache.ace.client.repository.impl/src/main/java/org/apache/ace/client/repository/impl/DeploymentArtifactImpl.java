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
package org.apache.ace.client.repository.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.ace.client.repository.object.DeploymentArtifact;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;


/**
 * This class is a basic implementation of DeploymentArtifact, with additional facilities
 * for serializing this artifacts with its directives.
 */
public class DeploymentArtifactImpl implements DeploymentArtifact {
    private final static String XML_NODE = "deploymentArtifact";
    private final static String XML_NODE_URL = "url";
    private final static String XML_NODE_DIRECTIVES = "directives";
    private final String m_url;
    private final Map<String, String> m_directives = new HashMap<String, String>();

    /**
     * Creates a new DeploymentArtifactImpl.
     * @param url The url to the new artifact, as a string; should not be null;
     */
    public DeploymentArtifactImpl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("The url should not be null.");
        }
        m_url = url;
    }

    /**
     * Creates a new DeploymentArtifactImpl by deserializing it from an XML stream.
     * @param reader A stream reader for the XML representation of this object.
     */
    public DeploymentArtifactImpl(HierarchicalStreamReader reader) {
        reader.moveDown();
        m_url = reader.getValue();
        reader.moveUp();

        reader.moveDown();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            m_directives.put(reader.getNodeName(), reader.getValue());
            reader.moveUp();
        }
        reader.moveUp();

    }

    /**
     * Writes this object to an XML stream.
     */
    public void marshal(HierarchicalStreamWriter writer) {
        writer.startNode(XML_NODE);

        writer.startNode(XML_NODE_URL);
        writer.setValue(m_url);
        writer.endNode(); // url node

        writer.startNode(XML_NODE_DIRECTIVES);
        for (Map.Entry<String, String> entry : m_directives.entrySet()) {
            writer.startNode(entry.getKey());
            assert (entry.getValue() != null);
            writer.setValue(entry.getValue());
            writer.endNode(); // this directive entry
        }
        writer.endNode(); // directives node

        writer.endNode(); // deploymentartifact node
    }

    /**
     * Adds a directive to this object.
     */
    void addDirective(String key, String value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException("Neither the key nor the value should be null.");
        }
        m_directives.put(key, value);
    }

    public String getDirective(String key) {
        return m_directives.get(key);
    }

    public String[] getKeys() {
        return m_directives.keySet().toArray(new String[m_directives.size()]);
    }

    public String getUrl() {
        return m_url;
    }

    @Override
    public boolean equals(Object other) {
        if ((other == null) || !getClass().equals(other.getClass())) {
            return false;
        }

        DeploymentArtifactImpl dai = (DeploymentArtifactImpl) other;

        boolean result = true;

        result &= getUrl().equals(dai.getUrl());

        result &= (getKeys().length == dai.getKeys().length);

        for (String key : getKeys()) {
            result &= getDirective(key).equals(dai.getDirective(key));
        }

        return result;
    }

    @Override
    public int hashCode() {
        int result = getUrl().hashCode();

        for (String key : getKeys()) {
            result ^= getDirective(key).hashCode();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(getUrl() + " [ ");
        for (String key : getKeys()) {
            result.append(key)
            .append(": ")
            .append(getDirective(key))
            .append(" ");
        }
        result.append("]");

        return result.toString();
    }

}
