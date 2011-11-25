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
package org.apache.ace.client.repository.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the GatewayRepository. For 'what it does', see GatewayRepository,
 * for 'how it works', see ObjectRepositoryImpl.
 * TODO: For now, this class reuses the functionality of ObjectRepositoryImpl. In the future, it
 * might be useful to create a custom implementation for performance reasons.
 */
public class DeploymentVersionRepositoryImpl extends ObjectRepositoryImpl<DeploymentVersionObjectImpl, DeploymentVersionObject> implements DeploymentVersionRepository {
    private final static String XML_NODE = "deploymentversions";

    public DeploymentVersionRepositoryImpl(ChangeNotifier notifier) {
        super(notifier, XML_NODE);
    }

    /*
     * The mechanism below allows us to insert the artifacts that are passed to create
     * into the newInhabitant, while still using the nice handling of ObjectRepositoryImpl.
     */
    private DeploymentArtifact[] m_tempDeploymentArtifacts;
    private final Object m_creationLock = new Object();

    @Override
    DeploymentVersionObjectImpl createNewInhabitant(Map<String, String> attributes) {
        synchronized (m_creationLock) {
            DeploymentVersionObjectImpl result = new DeploymentVersionObjectImpl(attributes, this);
            result.setDeploymentArtifacts(m_tempDeploymentArtifacts);
            m_tempDeploymentArtifacts = null;
            return result;
        }
    }

    @Override
    DeploymentVersionObjectImpl createNewInhabitant(Map<String, String> attributes, Map<String, String> tags) {
        synchronized (m_creationLock) {
            DeploymentVersionObjectImpl result = new DeploymentVersionObjectImpl(attributes, tags, this);
            result.setDeploymentArtifacts(m_tempDeploymentArtifacts);
            m_tempDeploymentArtifacts = null;
            return result;
        }
    }

    @Override
    DeploymentVersionObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new DeploymentVersionObjectImpl(reader, this);
    }

    public DeploymentVersionObject create(Map<String, String> attributes, Map<String, String> tags, DeploymentArtifact[] artifacts) {
        synchronized (m_creationLock) {
            m_tempDeploymentArtifacts = artifacts;
            return super.create(attributes, tags);
        }
    }

    private Comparator<DeploymentVersionObject> versionComparator = new Comparator<DeploymentVersionObject>() {
        public int compare(DeploymentVersionObject o1, DeploymentVersionObject o2) {
            return Version.parseVersion(o1.getVersion()).compareTo(Version.parseVersion(o2.getVersion()));
        }
    };

    public List<DeploymentVersionObject> getDeploymentVersions(String gatewayID) {
        List<DeploymentVersionObject> result = null;
            try {
                result = get(createFilter("(" + DeploymentVersionObject.KEY_GATEWAYID + "=" + RepositoryUtil.escapeFilterValue(gatewayID) + ")"));
                Collections.sort(result, versionComparator);
            }
            catch (InvalidSyntaxException e) {
                // Too bad, probably an illegal gatewayID.
                result = new ArrayList<DeploymentVersionObject>();
            }
        return result;
    }

    public DeploymentVersionObject getMostRecentDeploymentVersion(String gatewayID) {
        List<DeploymentVersionObject> versions = getDeploymentVersions(gatewayID);
        DeploymentVersionObject result = null;
        if ((versions != null) && (versions.size() > 0)) {
            result = versions.get(versions.size() - 1);
        }
        return result;
    }

    public DeploymentArtifact createDeploymentArtifact(String url, Map<String, String> directives) {
        DeploymentArtifactImpl result =  new DeploymentArtifactImpl(url);
        for (Map.Entry<String, String> entry : directives.entrySet()) {
            result.addDirective(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
