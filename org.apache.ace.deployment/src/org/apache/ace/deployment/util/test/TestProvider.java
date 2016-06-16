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
package org.apache.ace.deployment.util.test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;

import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public class TestProvider implements DeploymentProvider {
    private List<ArtifactData> m_collection;
    private List<String> m_versions;

    public TestProvider() throws Exception {
        m_collection = new ArrayList<>();
        m_versions = new ArrayList<>();
    }

    public void addData(String fileName, String symbolicName, URL url, String version) {
        addData(fileName, symbolicName, url, version, true);
    }

    public void addData(String fileName, String symbolicName, URL url, String version, boolean changed) {
        m_collection.add(new TestData(fileName, symbolicName, url, version, changed));
        m_versions.add(version);
    }

    public List<ArtifactData> getBundleData(String id, String version) {
        return m_collection;
    }

    public List<ArtifactData> getBundleData(String id, String versionFrom, String versionTo) {
        return m_collection;
    }

    public List<String> getVersions(String id) throws IllegalArgumentException {
        Collections.sort(m_versions);
        return m_versions;
    }
}
