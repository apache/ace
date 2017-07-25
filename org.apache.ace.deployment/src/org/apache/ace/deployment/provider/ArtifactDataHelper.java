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
package org.apache.ace.deployment.provider;

import java.util.List;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Provides an additional hook for processing {@link ArtifactData}s as returned by a {@link DeploymentProvider}.
 * <p>
 * {@link DeploymentProvider}s can use this service to define a particular order in which they should be included in the
 * deployment package.
 * </p>
 */
@ConsumerType
public interface ArtifactDataHelper {

    /**
     * @param artifacts
     *            the list of artifacts that should be processed, cannot be <code>null</code>;
     * @param targetId
     *            the identifier of the target these artifacts are intended for, cannot be <code>null</code>;
     * @param versionFrom
     *            the optional from version, can be <code>null</code>;
     * @param versionTo
     *            the to version, cannot be <code>null</code>.
     * @return the list of processed artifacts, in the order they should appear in the deployment package.
     */
    List<ArtifactData> process(List<ArtifactData> artifacts, String targetId, String versionFrom, String versionTo);

}
