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

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface TargetObject extends RepositoryObject {
    public static final String KEY_ID = "id";
    public static final String KEY_AUTO_APPROVE = "autoapprove";

    public static final String TOPIC_ENTITY_ROOT = TargetObject.class.getSimpleName() + "/";

    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    public static final String PRIVATE_TOPIC_ADDED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String PRIVATE_TOPIC_REMOVED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String PRIVATE_TOPIC_CHANGED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String PRIVATE_TOPIC_ALL = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Returns all <code>DistributionObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<DistributionObject> getDistributions();
    /**
     * Returns all associations this target has with a given distribution.
     */
    public List<Distribution2TargetAssociation> getAssociationsWith(DistributionObject distribution);

    /**
     * Gets the ID of this TargetObject.
     */
    public String getID();

    /**
     * Enable or disable automatic approval.
     */
    public void setAutoApprove(boolean approve);

    /**
     * Get the auto approval value of this target.
     */
    public boolean getAutoApprove();
}
