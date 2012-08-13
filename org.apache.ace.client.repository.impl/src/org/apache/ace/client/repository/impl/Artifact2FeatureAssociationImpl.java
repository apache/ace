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
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Artifact2FeatureAssociation. For 'what it does', see Artifact2FeatureAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class Artifact2FeatureAssociationImpl extends AssociationImpl<ArtifactObject, FeatureObject, Artifact2FeatureAssociation> implements Artifact2FeatureAssociation {
    private final static String XML_NODE = "artifact2feature";

    public Artifact2FeatureAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, FeatureRepositoryImpl featureRepository) throws InvalidSyntaxException {
        super(attributes, notifier, ArtifactObject.class, FeatureObject.class, artifactRepository, featureRepository, XML_NODE);
    }

    public Artifact2FeatureAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, FeatureRepositoryImpl featureRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, ArtifactObject.class, FeatureObject.class, artifactRepository, featureRepository, XML_NODE);
    }

    public Artifact2FeatureAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, ArtifactRepositoryImpl artifactRepository, FeatureRepositoryImpl featureRepository) throws InvalidSyntaxException {
        super(reader, notifier, ArtifactObject.class, FeatureObject.class, null, null, artifactRepository, featureRepository, XML_NODE);
    }
}
