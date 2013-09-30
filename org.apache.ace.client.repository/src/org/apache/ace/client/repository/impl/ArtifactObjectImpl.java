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

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.FeatureObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the ArtifactObject. For 'what it does', see ArtifactObject, for 'how it works', see
 * RepositoryObjectImpl.<br>
 * <br>
 * Some functionality of this class is delegated to implementers of {@link ArtifactHelper}.
 */
public class ArtifactObjectImpl extends RepositoryObjectImpl<ArtifactObject> implements ArtifactObject {
    private final static String XML_NODE = "artifact";

    /*
     * As a general rule, RepositoryObjects do not know about their repository. However, since the Helper to be used is
     * dictated by the repository, this rule is broken for this class.
     */
    private final ArtifactRepositoryImpl m_repo;

    ArtifactObjectImpl(Map<String, String> attributes, String[] mandatoryAttributes, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(checkAttributes(attributes, completeMandatoryAttributes(mandatoryAttributes)), notifier, XML_NODE);
        m_repo = repo;
    }

    ArtifactObjectImpl(Map<String, String> attributes, String[] mandatoryAttributes, Map<String, String> tags, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(checkAttributes(attributes, completeMandatoryAttributes(mandatoryAttributes)), tags, notifier, XML_NODE);
        m_repo = repo;
    }

    ArtifactObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(reader, notifier, XML_NODE);
        m_repo = repo;
    }

    private static String[] completeMandatoryAttributes(String[] mandatory) {
        String[] result = new String[mandatory.length + 1];
        for (int i = 0; i < mandatory.length; i++) {
            result[i] = mandatory[i];
        }
        result[mandatory.length] = KEY_MIMETYPE;
        return result;
    }

    public List<FeatureObject> getFeatures() {
        return getAssociations(FeatureObject.class);
    }

    public List<Artifact2FeatureAssociation> getAssociationsWith(FeatureObject feature) {
        return getAssociationsWith(feature, FeatureObject.class, Artifact2FeatureAssociation.class);
    }

    @Override
    public String getAssociationFilter(Map<String, String> properties) {
        return getHelper().getAssociationFilter(this, properties);
    }

    @Override
    public int getCardinality(Map<String, String> properties) {
        return getHelper().getCardinality(this, properties);
    }

    @Override
    public Comparator<ArtifactObject> getComparator() {
        return getHelper().getComparator();
    }

    @Override
    public long getSize() {
        String size = getAttribute(KEY_SIZE);
        try {
            if (size != null) {
                return Long.parseLong(size);
            }
        }
        catch (NumberFormatException exception) {
        }
        return -1L;
    }

    public String getURL() {
        return getAttribute(KEY_URL);
    }

    public String getResourceId() {
        return getAttribute(KEY_RESOURCE_ID);
    }

    public String getMimetype() {
        return getAttribute(KEY_MIMETYPE);
    }

    public String getProcessorPID() {
        return getAttribute(KEY_PROCESSOR_PID);
    }

    public void setProcessorPID(String processorPID) {
        addAttribute(KEY_PROCESSOR_PID, processorPID);
    }

    public String getName() {
        return getAttribute(KEY_ARTIFACT_NAME);
    }

    public String getDescription() {
        return getAttribute(KEY_ARTIFACT_DESCRIPTION);
    }

    public void setDescription(String value) {
        addAttribute(KEY_ARTIFACT_DESCRIPTION, value);
    }

    @Override
    String[] getDefiningKeys() {
        return getHelper().getDefiningKeys().clone();
    }

    private ArtifactHelper getHelper() {
        // getMimetype is safe, as is getHelper, and m_repo is final, so no
        // need to synchronize here...
        return m_repo.getHelper(getMimetype());
    }
}
