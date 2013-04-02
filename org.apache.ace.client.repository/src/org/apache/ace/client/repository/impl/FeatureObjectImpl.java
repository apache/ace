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

import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.DistributionObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the FeatureObject. For 'what it does', see FeatureObject,
 * for 'how it works', see RepositoryObjectImpl.
 */
public class FeatureObjectImpl extends RepositoryObjectImpl<FeatureObject> implements FeatureObject {
    private final static String XML_NODE = "feature";

    FeatureObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), tags, notifier, XML_NODE);
    }

    FeatureObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), notifier, XML_NODE);
    }

    FeatureObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
    }

    public List<ArtifactObject> getArtifacts() {
        return getAssociations(ArtifactObject.class);
    }

    public List<DistributionObject> getDistributions() {
        return getAssociations(DistributionObject.class);
    }

    public String getDescription() {
        return getAttribute(KEY_DESCRIPTION);
    }

    public String getName() {
        return getAttribute(KEY_NAME);
    }

    public void setDescription(String description) {
        addAttribute(KEY_DESCRIPTION, description);
    }

    public void setName(String name) {
        addAttribute(KEY_NAME, name);
    }

    public List<Artifact2FeatureAssociation> getAssociationsWith(ArtifactObject artifact) {
        return getAssociationsWith(artifact, ArtifactObject.class, Artifact2FeatureAssociation.class);
    }

    public List<Feature2DistributionAssociation> getAssociationsWith(DistributionObject distribution) {
        return getAssociationsWith(distribution, DistributionObject.class, Feature2DistributionAssociation.class);
    }

    private static String[] DEFINING_KEYS = new String[] {KEY_NAME};
    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }
}
