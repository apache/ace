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
package org.apache.ace.client.repository.repository;

import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;

import org.osgi.annotation.versioning.ProviderType;


/**
 * Interface to a DeploymentVersionRepository. The functionality is defined by the generic ObjectRepository.
 */
@ProviderType
public interface DeploymentVersionRepository extends ObjectRepository<DeploymentVersionObject> {
    /**
     * Creates a new inhabitant based on the given attributes and bundle URLs. The object
     * will be stored in this repository's store, and will be returned.
     * @throws IllegalArgumentException Will be thrown when the attributes cannot be accepted.
     */
    public DeploymentVersionObject create(Map<String, String> attributes, Map<String, String> tags, DeploymentArtifact[] artifacts);

    /**
     * Gets all available deployment versions for this target. If none can be
     * found, an empty list will be returned.
     * @param targetID The target to be used.
     * @return A list of <code>DeploymentVersionObject</code>s which are related to
     * this target, sorted lexically by version.
     */
    public List<DeploymentVersionObject> getDeploymentVersions(String targetID);

    /**
     * Get the most recent known deployment version for a given target.
     * @param targetID The target to be used.
     * @return A <code>DeploymentVersionObject</code> which is the most recent one to be deployed
     * to the target. If none can be found, <code>null</code> will be returned.
     */
    public DeploymentVersionObject getMostRecentDeploymentVersion(String targetID);

    /**
     * Creates a DeploymentArtifact object.
     * @param url The url to be used in this object.
     * @param directives A map of directives to be packed into the object.
     * @return The newly created deployment artifact object.
     */
    public DeploymentArtifact createDeploymentArtifact(String url, long size, Map<String, String> directives);
}
