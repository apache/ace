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

import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
/**
 * Implementation class for the Distribution2TargetAssociationRepository. For 'what it does', see Distribution2TargetAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */

public class Distribution2TargetAssociationRepositoryImpl extends AssociationRepositoryImpl<DistributionObject, TargetObject, Distribution2TargetAssociationImpl, Distribution2TargetAssociation> implements Distribution2TargetAssociationRepository {
    private final static String XML_NODE = "distributions2targets";

    private final DistributionRepositoryImpl m_distributionRepository;
    private final TargetRepositoryImpl m_targetRepository;

    public Distribution2TargetAssociationRepositoryImpl(DistributionRepositoryImpl distributionRepository, TargetRepositoryImpl targetRepository, ChangeNotifier notifier, RepositoryConfiguration repoConfig) {
        super(notifier, XML_NODE, repoConfig);
        m_distributionRepository = distributionRepository;
        m_targetRepository = targetRepository;
    }

    @Override
    Distribution2TargetAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Distribution2TargetAssociationImpl(attributes, this, m_distributionRepository, m_targetRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Distribution2TargetAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Distribution2TargetAssociationImpl(attributes, tags, this, m_distributionRepository, m_targetRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Distribution2TargetAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Distribution2TargetAssociationImpl(reader, this, m_distributionRepository, m_targetRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    public Distribution2TargetAssociation createDistribution2TargetFilter(DistributionObject distribution, String targetFilter) {
        try {
            m_targetRepository.createFilter(targetFilter);
        }
        catch (InvalidSyntaxException ise) {
            throw new IllegalArgumentException("Target filter '" + targetFilter + "' cannot be parsed into a valid Filter.", ise);
        }

        return create(distribution.getAssociationFilter(null), targetFilter);
    }
}
