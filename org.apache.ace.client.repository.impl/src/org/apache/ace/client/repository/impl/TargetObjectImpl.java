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

import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the TargetObject. For 'what it does', see TargetObject,
 * for 'how it works', see RepositoryObjectImpl.
 */
public class TargetObjectImpl extends RepositoryObjectImpl<TargetObject> implements TargetObject {
    private final static String XML_NODE = "target";

    TargetObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_ID), notifier, XML_NODE);
    }

    TargetObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, KEY_ID), tags, notifier, XML_NODE);
    }

    TargetObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
        if(getAttribute(KEY_AUTO_APPROVE) == null) {
            addAttribute(KEY_AUTO_APPROVE, String.valueOf(false));
        }
    }

    public List<DistributionObject> getDistributions() {
        return getAssociations(DistributionObject.class);
    }

    public String getID() {
        return getAttribute(KEY_ID);
    }

    public List<Distribution2TargetAssociation> getAssociationsWith(DistributionObject distribution) {
        return getAssociationsWith(distribution, DistributionObject.class, Distribution2TargetAssociation.class);
    }

    private static String[] DEFINING_KEYS = new String[] {KEY_ID};
    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }

    public boolean getAutoApprove() {
        String value = getAttribute(KEY_AUTO_APPROVE);
        if (value == null) {
            return false;
        }
        else {
            return Boolean.parseBoolean(value);
        }
    }

    public void setAutoApprove(boolean approve) {
       addAttribute(KEY_AUTO_APPROVE, String.valueOf(approve));
    }
}
