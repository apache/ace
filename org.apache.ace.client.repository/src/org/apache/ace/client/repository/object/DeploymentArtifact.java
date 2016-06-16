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

import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface to a deployment artifact, which is used to gather information about
 * the deployment of a single artifact.
 */
@ProviderType
public interface DeploymentArtifact {

    /**
     * Key, intended to be used for artifacts which are bundles and will publish
     * a resource processor (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_ISCUSTOMIZER = "DeploymentPackage-Customizer";

    /**
     * Key, intended to be used for resources which require a resource processor
     * (see OSGi compendium section 114.10).
     */
    public static final String DIRECTIVE_KEY_PROCESSORID = "Resource-Processor";

    /**
     * Key, intended to be used for artifacts which have a resourceID that's different
     * from their generated name (based on URL).
     */
    public static final String DIRECTIVE_KEY_RESOURCE_ID = "Resource-ID";

    /**
     * Key, intended to be used for matching processed (see ArtifactPreprocessor) to their
     * 'original' one.
     */
    public static final String DIRECTIVE_KEY_BASEURL = "Base-Url";

	public static final String REPOSITORY_PATH = "ACE-RepositoryPath";

    /**
     * @return the URL for this deployment artifact.
     */
    public String getUrl();

    /**
     * @return the (estimated) size of this deployment artifact, in bytes.
     */
    public long getSize();

    /**
     * @param key A key String, such as the <code>DIRECTIVE_</code> constants in
     * <code>DeploymentArtifact</code>.
     * @return the value for the given directive key, or <code>null</code> if not found.
     */
    public String getDirective(String key);

    /**
     * @return an array of all keys that are used in this object, to be used in <code>getDirective</code>.
     */
    public String[] getKeys();
}
