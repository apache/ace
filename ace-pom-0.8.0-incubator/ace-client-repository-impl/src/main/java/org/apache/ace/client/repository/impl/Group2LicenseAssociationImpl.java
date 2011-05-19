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
import org.osgi.framework.InvalidSyntaxException;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the Group2LicenseAssociation. For 'what it does', see Group2LicenseAssociation,
 * for 'how it works', see AssociationImpl.
 */
public class Group2LicenseAssociationImpl extends AssociationImpl<GroupObject, LicenseObject, Group2LicenseAssociation> implements Group2LicenseAssociation {
    private final static String XML_NODE = "group2license";

    public Group2LicenseAssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(attributes, notifier, GroupObject.class, LicenseObject.class, groupRepository, licenseRepository, XML_NODE);
    }
    public Group2LicenseAssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(attributes, tags, notifier, GroupObject.class, LicenseObject.class, groupRepository, licenseRepository, XML_NODE);
    }
    public Group2LicenseAssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, GroupRepositoryImpl groupRepository, LicenseRepositoryImpl licenseRepository) throws InvalidSyntaxException {
        super(reader, notifier, GroupObject.class, LicenseObject.class, null, null, groupRepository, licenseRepository, XML_NODE);
    }
}
