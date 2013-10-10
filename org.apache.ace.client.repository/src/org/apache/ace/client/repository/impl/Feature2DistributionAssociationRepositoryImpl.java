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

import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Feature2DistributionAssociationRepository. For 'what it does', see Feature2DistributionAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Feature2DistributionAssociationRepositoryImpl extends AssociationRepositoryImpl<FeatureObject, DistributionObject, Feature2DistributionAssociationImpl, Feature2DistributionAssociation> implements Feature2DistributionAssociationRepository {
    private final static String XML_NODE = "features2distributions";

    private final FeatureRepositoryImpl m_featureRepository;
    private final DistributionRepositoryImpl m_distributionRepository;

    public Feature2DistributionAssociationRepositoryImpl(FeatureRepositoryImpl featureRepository, DistributionRepositoryImpl distributionRepository, ChangeNotifier notifier, RepositoryConfiguration repoConfig) {
        super(notifier, XML_NODE, repoConfig);
        m_featureRepository = featureRepository;
        m_distributionRepository = distributionRepository;
    }

    @Override
    Feature2DistributionAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Feature2DistributionAssociationImpl(attributes, this, m_featureRepository, m_distributionRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Feature2DistributionAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Feature2DistributionAssociationImpl(attributes, tags, this, m_featureRepository, m_distributionRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Feature2DistributionAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Feature2DistributionAssociationImpl(reader, this, m_featureRepository, m_distributionRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }
}
