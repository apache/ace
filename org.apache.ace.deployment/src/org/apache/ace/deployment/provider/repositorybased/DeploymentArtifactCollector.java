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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Version;

/**
 * Provides {@link BaseRepositoryHandler} implementation that gathers all deployment
 * artifacts of deployment packages for a specific target with a specific version.
 */
public class DeploymentArtifactCollector extends BaseRepositoryHandler {

    private final List<Version> m_expectedVersions;
    private final Map<Version, List<XmlDeploymentArtifact>> m_artifacts;

    /**
     * @param targetID the identification of the target to gather all artifacts for;
     * @param versions the version of the deployment package to gather all artifacts for.
     */
    public DeploymentArtifactCollector(String targetID, String... versions) {
        super(targetID);

        m_artifacts = new HashMap<>();

        m_expectedVersions = new ArrayList<>(versions.length);
        for (int i = 0; i < versions.length; i++) {
            Version v = parseVersion(versions[i]);
            if (Version.emptyVersion.equals(v)) {
                throw new IllegalArgumentException("Expected real version for " + versions[i]);
            }
            m_expectedVersions.add(v);
        }
    }

    /**
     * Returns all deployment artifacts of the requested target's deployment package.
     * 
     * @return an array with lists of all found deployment artifacts, never <code>null</code>.
     *         The array contains the deployment artifacts per requested version, in the same
     *         order as given in the class constructor.
     */
    @SuppressWarnings("unchecked")
    public List<XmlDeploymentArtifact>[] getArtifacts() {
        List<XmlDeploymentArtifact>[] result = new List[m_expectedVersions.size()];
        int i = 0;
        for (Version version : m_expectedVersions) {
            List<XmlDeploymentArtifact> list = m_artifacts.get(version);
            if (list == null) {
                throw new IllegalArgumentException("No artifacts found for version " + version);
            }
            result[i++] = list;
        }
        return result;
    }

    @Override
    protected void handleVersion(Version version) {
        if (m_expectedVersions.contains(version)) {
            List<XmlDeploymentArtifact> artifacts = m_artifacts.get(version);
            if (artifacts == null) {
                artifacts = new ArrayList<>();
                m_artifacts.put(version, artifacts);
            }
        }
    }

    @Override
    protected void handleArtifact(Version version, XmlDeploymentArtifact artifact) {
        if (m_expectedVersions.contains(version)) {
            m_artifacts.get(version).add(artifact);
        }
    }
}
