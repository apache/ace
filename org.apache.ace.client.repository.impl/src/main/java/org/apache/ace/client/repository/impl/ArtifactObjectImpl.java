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
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GroupObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the ArtifactObject. For 'what it does', see ArtifactObject,
 * for 'how it works', see RepositoryObjectImpl.<br>
 * <br>
 * Some functionality of this class is delegated to implementers of {@link ArtifactHelper}.
 */
public class ArtifactObjectImpl extends RepositoryObjectImpl<ArtifactObject> implements ArtifactObject {
    private final static String XML_NODE = "artifact";

    /*
     * As a general rule, RepositoryObjects do not know about their repository. However, since the Helper
     * to be used is dictated by the repository, this rule is broken for this class.
     */
    private ArtifactRepositoryImpl m_repo;

    ArtifactObjectImpl(Map<String, String> attributes, String[] mandatoryAttributes, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(checkAttributes(attributes, completeMandatoryAttributes(mandatoryAttributes)), notifier, XML_NODE);
        m_repo = repo;
    }

    ArtifactObjectImpl(Map<String, String> attributes, String[] mandatoryAttributes, Map<String, String> tags, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(checkAttributes(attributes, completeMandatoryAttributes(mandatoryAttributes)), tags, notifier, XML_NODE);
        m_repo = repo;
    }

    private static String[] completeMandatoryAttributes(String[] mandatory) {
        String[] result = new String[mandatory.length + 2];
        for (int i = 0; i < mandatory.length; i++) {
            result[i] = mandatory[i];
        }
        result[mandatory.length] = KEY_MIMETYPE;
        result[mandatory.length + 1 ] = KEY_URL;
        return result;
    }

    ArtifactObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, ArtifactRepositoryImpl repo) {
        super(reader, notifier, XML_NODE);
        m_repo = repo;
    }

    public List<GroupObject> getGroups() {
        return getAssociations(GroupObject.class);
    }

    public List<Artifact2GroupAssociation> getAssociationsWith(GroupObject group) {
        return getAssociationsWith(group, GroupObject.class, Artifact2GroupAssociation.class);
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
    String[] getDefiningKeys() {
        String[] fromHelper = getHelper().getDefiningKeys();

        String[] result = new String[fromHelper.length + 1];
        for (int i = 0; i < fromHelper.length; i++) {
            result[i] = fromHelper[i];
        }
        result[fromHelper.length] = KEY_URL;

        return result;
    }

    public String getURL() {
        return getAttribute(KEY_URL);
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

    private synchronized ArtifactHelper getHelper() {
        return m_repo.getHelper(getMimetype());
    }

    public void setDescription(String value) {
        addAttribute(KEY_ARTIFACT_DESCRIPTION, value);
    }
}
