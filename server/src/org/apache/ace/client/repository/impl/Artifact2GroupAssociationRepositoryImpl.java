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

import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Artifact2GroupAssociationRepository. For 'what it does', see Artifact2GroupAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Artifact2GroupAssociationRepositoryImpl extends AssociationRepositoryImpl<ArtifactObject, GroupObject, Artifact2GroupAssociationImpl, Artifact2GroupAssociation> implements Artifact2GroupAssociationRepository {
    private final static String XML_NODE = "artifacts2groups";

    private final ArtifactRepositoryImpl m_bundleRepository;
    private final GroupRepositoryImpl m_groupRepository;

    public Artifact2GroupAssociationRepositoryImpl(ArtifactRepositoryImpl bundleRepository, GroupRepositoryImpl groupRepository, ChangeNotifier notifier) {
        super(notifier, XML_NODE);
        m_bundleRepository = bundleRepository;
        m_groupRepository = groupRepository;
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Artifact2GroupAssociationImpl(attributes, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Artifact2GroupAssociationImpl(attributes, tags, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Artifact2GroupAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Artifact2GroupAssociationImpl(reader, this, m_bundleRepository, m_groupRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }
}
