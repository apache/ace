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

import java.io.IOException;
import java.util.List;

/**
 * An interface that provides the meta information for the bundles
 * in a certain version number.
 */
public interface DeploymentProvider {

    /**
     * Get the collection of bundleData for a specific version. This data can be used to generate a deployment package.
     * The ArtifactData.hasChanged method will return true for all bundles in this collection
     *
     * @return a collection of bundledata. If there are no bundles in this version, return an empty list
     * @throws IllegalArgumentException if the gateway or version do not exist
     * @throws IOException If an IOException occurs.
     */
    public List<ArtifactData> getBundleData(String gatewayId, String version) throws IllegalArgumentException, IOException;

    /**
     * This data can be used to generate a fix package. It gives the differences between the versionFrom and versionTo.
     *
     * Changes between versions are indicated by ArtifactData.hasChanged:
     * <ol>
     * <li> If a bundle was present in versionFrom and not in VersionTo, it will not be in the collection</li>
     * <li> If a bundle existed in versionFrom and exists unchanged in VersionTo, hasChanged will return false</li>
     * <li> If a bundle existed in versionFrom and exists changed (i.e. other version) in versionTo, hasChanged will return true</li>
     * <li> If a bundle did not exist in versionFrom and exists in VersionTo, hasChanged will return true</li>
     * </ol>
     *
     * @return a list of bundles.
     * @throws IllegalArgumentException if the gateway, the versionFrom or versionTo do no exist
     * @throws IOException If an IOException occurs.
     */

    public List<ArtifactData> getBundleData(String gatewayId, String versionFrom, String versionTo) throws IllegalArgumentException, IOException;

    /**
     * Returns a list of versions for a specific gateway. The list is sorted in
     * ascending order, so the latest version is the last one in the list.
     *
     * @param gatewayId  The id of the gateway for which all available deployment package
     *                   versions are being retrieved.
     * @return All available deployment package versions for a specific gateway. If none available,
     *         return an empty List.
     *         If the gateway doesn't exist, an IllegalArgumentException is thrown
     * @throws IOException If an IOException occurs.
     */
    public List<String> getVersions(String gatewayId) throws IllegalArgumentException, IOException;
}
