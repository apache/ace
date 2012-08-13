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
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Feature2DistributionAssociation. For 'what it does', see Feature2DistributionAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class Feature2DistributionAssociationImpl extends AssociationImpl<FeatureObject, DistributionObject, Feature2DistributionAssociation> implements Feature2DistributionAssociation {
    private final static String XML_NODE = "feature2distribution";

    public Feature2DistributionAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, FeatureRepositoryImpl featureRepository, DistributionRepositoryImpl distributionRepository) throws InvalidSyntaxException {
        super(attributes, notifier, FeatureObject.class, DistributionObject.class, featureRepository, distributionRepository, XML_NODE);
    }
    public Feature2DistributionAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, FeatureRepositoryImpl featureRepository, DistributionRepositoryImpl distributionRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, FeatureObject.class, DistributionObject.class, featureRepository, distributionRepository, XML_NODE);
    }
    public Feature2DistributionAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, FeatureRepositoryImpl featureRepository, DistributionRepositoryImpl distributionRepository) throws InvalidSyntaxException {
        super(reader, notifier, FeatureObject.class, DistributionObject.class, null, null, featureRepository, distributionRepository, XML_NODE);
    }
}
