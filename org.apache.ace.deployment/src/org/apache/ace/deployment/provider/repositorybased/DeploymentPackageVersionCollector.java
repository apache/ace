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
package org.apache.ace.deployment.provider.repositorybased;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Version;

/**
 * Provides {@link BaseRepositoryHandler} implementation that gathers all versions of deployment packages for a specific target.
 */
public class DeploymentPackageVersionCollector extends BaseRepositoryHandler {

    private final List<Version> m_versions;

    /**
     * @param targetID the target to gather all deployment package versions for.
     */
    public DeploymentPackageVersionCollector(String targetID) {
        super(targetID);

        m_versions = new ArrayList<>();
    }

    /**
     * Returns a list of all found deployment package versions.
     * 
     * @return a list of {@link Version}s, never <code>null</code>.
     */
    public List<Version> getVersions() {
        return m_versions;
    }

    @Override
    protected void handleVersion(Version version) {
        m_versions.add(version);
    }
}
