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

import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the LicenseObject. For 'what it does', see LicenseObject,
 * for 'how it works', see RepositoryObjectImpl.
 */
public class LicenseObjectImpl extends RepositoryObjectImpl<LicenseObject> implements LicenseObject {
    private final static String XML_NODE = "license";

    LicenseObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), tags, notifier, XML_NODE);
    }

    LicenseObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_NAME), notifier, XML_NODE);
    }

    LicenseObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
    }

    public List<GatewayObject> getGateways() {
        return getAssociations(GatewayObject.class);
    }

    public List<GroupObject> getGroups() {
        return getAssociations(GroupObject.class);
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

    public List<Group2LicenseAssociation> getAssociationsWith(GroupObject group) {
        return getAssociationsWith(group, GroupObject.class, Group2LicenseAssociation.class);
    }

    public List<License2GatewayAssociation> getAssociationsWith(GatewayObject gateway) {
        return getAssociationsWith(gateway, GatewayObject.class, License2GatewayAssociation.class);
    }

    private static String[] DEFINING_KEYS = new String[] {KEY_NAME};
    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }
}
