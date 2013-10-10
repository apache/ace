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

import java.util.Map;

import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Artifact2FeatureAssociationRepository. For 'what it does', see Artifact2FeatureAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Artifact2FeatureAssociationRepositoryImpl extends AssociationRepositoryImpl<ArtifactObject, FeatureObject, Artifact2FeatureAssociationImpl, Artifact2FeatureAssociation> implements Artifact2FeatureAssociationRepository {
    private final static String XML_NODE = "artifacts2features";

    private final ArtifactRepositoryImpl m_artifactRepository;
    private final FeatureRepositoryImpl m_featureRepository;

    public Artifact2FeatureAssociationRepositoryImpl(ArtifactRepositoryImpl artifactRepository, FeatureRepositoryImpl featureRepository, ChangeNotifier notifier, RepositoryConfiguration repoConfig) {
        super(notifier, XML_NODE, repoConfig);
        m_artifactRepository = artifactRepository;
        m_featureRepository = featureRepository;
    }

    @Override
    Artifact2FeatureAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Artifact2FeatureAssociationImpl(attributes, this, m_artifactRepository, m_featureRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: " + e.getMessage(), e);
        }
    }

    @Override
    Artifact2FeatureAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Artifact2FeatureAssociationImpl(attributes, tags, this, m_artifactRepository, m_featureRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: " + e.getMessage(), e);
        }
    }

    @Override
    Artifact2FeatureAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Artifact2FeatureAssociationImpl(reader, this, m_artifactRepository, m_featureRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: " + e.getMessage(), e);
        }
    }
}
