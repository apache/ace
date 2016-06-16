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

import java.net.URL;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Provides the configuration options for several of the repositories.
 */
@ProviderType
public interface RepositoryConfiguration {

    /**
     * @return the number of deployment versions to retain per target, can be <tt>-1</tt> (the default) if the number of
     *         deployment versions per target is unbounded.
     */
    int getDeploymentVersionLimit();

    /**
     * @return the URL where the OBR can be accessed to store artifacts, never <code>null</code>. Defaults to
     *         <tt>http://localhost:8080/obr/</tt>.
     */
    URL getOBRLocation();

    /**
     * @return <code>true</code> (the default) if unregistered targets should be shown in the target repository,
     *         <code>false</code> to only show (pre-)registered targets.
     */
    boolean isShowUnregisteredTargets();

}
