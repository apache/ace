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
package org.apache.ace.client.repository.object;

import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;

/**
 * Interface to a LicenseObject. The basic functionality is defined by RepositoryObject, but extended for
 * License-specific information.
 */
public interface LicenseObject extends RepositoryObject {
    public static final String TOPIC_ENTITY_ROOT = LicenseObject.class.getSimpleName() + "/";

    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    public static final String KEY_DESCRIPTION = "description";
    public static final String KEY_NAME = "name";

    /**
     * Returns all <code>GroupObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<GroupObject> getGroups();
    /**
     * Returns all <code>GatewayObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<GatewayObject> getGateways();

    /**
     * Returns all associations this license has with a given group.
     */
    public List<Group2LicenseAssociation> getAssociationsWith(GroupObject group);
    /**
     * Returns all associations this license has with a given gateway.
     */
    public List<License2GatewayAssociation> getAssociationsWith(GatewayObject gateway);

    /**
     * Returns the name of this bundle.
     */
    public String getName();
    /**
     * Sets the name of this bundle.
     */
    public void setName(String name);
    /**
     * Returns the description of this bundle.
     */
    public String getDescription();
    /**
     * Sets the description of this bundle.
     */
    public void setDescription(String description);
}
