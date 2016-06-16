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

import org.apache.ace.client.repository.RepositoryObject;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The interface to a DeploymentVersion. The basic functionality is defined
 * by RepositoryObject, but extended for deployment version-specific information.
 *
 * DeploymentVersions need some additional information about the artifacts they
 * are associated with; see DeploymentArtifact.
 */
@ProviderType
public interface DeploymentVersionObject extends RepositoryObject {

    public static final String KEY_TARGETID = "targetID";
    public static final String KEY_VERSION = "version";

    public static final String TOPIC_ENTITY_ROOT = DeploymentVersionObject.class.getSimpleName() + "/";

    public static final String TOPIC_ADDED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String TOPIC_REMOVED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String TOPIC_CHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    public static final String PRIVATE_TOPIC_ADDED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ADDED_SUFFIX;
    public static final String PRIVATE_TOPIC_REMOVED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REMOVED_SUFFIX;
    public static final String PRIVATE_TOPIC_CHANGED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_CHANGED_SUFFIX;
    public static final String PRIVATE_TOPIC_ALL = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Gets the target which is related to this version.
     */
    public String getTargetID();

    /**
     * Gets the version number of this deployment version.
     */
    public String getVersion();

    /**
     * @return an array of all deployment artifacts that will be part of this deployment version.
     * The order of the artifacts in the array is equal to the order they should appear in a
     * deployment package.
     */
    public DeploymentArtifact[] getDeploymentArtifacts();
}
