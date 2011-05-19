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

import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Group2LicenseAssociationRepository. For 'what it does', see Group2LicenseAssociationRepository,
 * for 'how it works', see AssociationRepositoryImpl.
 */
public class Group2LicenseAssociationRepositoryImpl extends AssociationRepositoryImpl<GroupObject, LicenseObject, Group2LicenseAssociationImpl, Group2LicenseAssociation> implements Group2LicenseAssociationRepository {
    private final static String XML_NODE = "groups2licenses";

    private final GroupRepositoryImpl m_groupRepository;
    private final LicenseRepositoryImpl m_licenseRepository;

    public Group2LicenseAssociationRepositoryImpl(GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository, ChangeNotifier notifier) {
        super(notifier, XML_NODE);
        m_groupRepository = groupRepository;
        m_licenseRepository = licenseRepository;
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(Map<String, String> attributes) {
        try {
            return new Group2LicenseAssociationImpl(attributes, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        try {
            return new Group2LicenseAssociationImpl(attributes, tags, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }

    @Override
    Group2LicenseAssociationImpl createNewInhabitant(HierarchicalStreamReader reader) {
        try {
            return new Group2LicenseAssociationImpl(reader, this, m_groupRepository, m_licenseRepository);
        }
        catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to create association: ", e);
        }
    }
}
