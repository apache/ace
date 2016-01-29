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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.RepositoryConfiguration;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * Implementation class for the TargetRepository. For 'what it does', see TargetRepository, for 'how it works', see
 * ObjectRepositoryImpl. TODO: For now, this class reuses the functionality of ObjectRepositoryImpl. In the future, it
 * might be useful to create a custom implementation for performance reasons.
 */
public class DeploymentVersionRepositoryImpl extends ObjectRepositoryImpl<DeploymentVersionObjectImpl, DeploymentVersionObject> implements DeploymentVersionRepository {
    private final static String XML_NODE = "deploymentversions";

    /*
     * The mechanism below allows us to insert the artifacts that are passed to create into the newInhabitant, while
     * still using the nice handling of ObjectRepositoryImpl.
     */
    private DeploymentArtifact[] m_tempDeploymentArtifacts;

    private final Object m_creationLock = new Object();

    private Comparator<DeploymentVersionObject> versionComparator = new Comparator<DeploymentVersionObject>() {
        public int compare(DeploymentVersionObject o1, DeploymentVersionObject o2) {
            return Version.parseVersion(o1.getVersion()).compareTo(Version.parseVersion(o2.getVersion()));
        }
    };

    public DeploymentVersionRepositoryImpl(ChangeNotifier notifier, RepositoryConfiguration repositoryConfig) {
        super(notifier, XML_NODE, repositoryConfig);
    }

    public DeploymentVersionObject create(Map<String, String> attributes, Map<String, String> tags, DeploymentArtifact[] artifacts) {
        synchronized (m_creationLock) {
            m_tempDeploymentArtifacts = artifacts;
            return super.create(attributes, tags);
        }
    }

    public DeploymentArtifact createDeploymentArtifact(String url, long size, Map<String, String> directives) {
        DeploymentArtifactImpl result = new DeploymentArtifactImpl(url, size);
        for (Map.Entry<String, String> entry : directives.entrySet()) {
            result.addDirective(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public List<DeploymentVersionObject> getDeploymentVersions(String targetID) {
        List<DeploymentVersionObject> result = null;
        try {
            result = get(createFilter("(" + DeploymentVersionObject.KEY_TARGETID + "=" + RepositoryUtil.escapeFilterValue(targetID) + ")"));
            Collections.sort(result, versionComparator);
        }
        catch (InvalidSyntaxException e) {
            // Too bad, probably an illegal targetID.
            result = new ArrayList<>();
        }
        return result;
    }

    public DeploymentVersionObject getMostRecentDeploymentVersion(String targetID) {
        List<DeploymentVersionObject> versions = getDeploymentVersions(targetID);
        DeploymentVersionObject result = null;
        if ((versions != null) && (versions.size() > 0)) {
            result = versions.get(versions.size() - 1);
        }
        return result;
    }

    @Override
    boolean internalAdd(DeploymentVersionObject entity) {
        boolean result = super.internalAdd(entity);

        int deploymentVersionLimit = getDeploymentVersionLimit();
        if (deploymentVersionLimit > 0) {
            purgeOldDeploymentVersions(deploymentVersionLimit);
        }

        return result;
    }

    @Override
    DeploymentVersionObjectImpl createNewInhabitant(HierarchicalStreamReader reader) {
        return new DeploymentVersionObjectImpl(reader, this);
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

    /**
     * @return an index of deployment versions (sorted!) per target, never <code>null</code>.
     */
    private Map<String, SortedSet<DeploymentVersionObject>> createDeploymentVersionIndex() {
        Map<String, SortedSet<DeploymentVersionObject>> index = new HashMap<>();
        for (DeploymentVersionObject dvo : get()) {
            SortedSet<DeploymentVersionObject> versions = index.get(dvo.getTargetID());
            if (versions == null) {
                // store all DeploymentVersions in ascending order (oldest version first)...
                versions = new TreeSet<>(this.versionComparator);
                index.put(dvo.getTargetID(), versions);
            }
            versions.add(dvo);
        }
        return index;
    }

    /**
     * @return the maximum number of deployment versions to retain per target, or -1 if no limit is imposed.
     */
    private int getDeploymentVersionLimit() {
        return getRepositoryConfiguration().getDeploymentVersionLimit();
    }

    /**
     * Purges old deployment versions for each target.
     */
    private void purgeOldDeploymentVersions(int deploymentVersionLimit) {
        Map<String, SortedSet<DeploymentVersionObject>> index = createDeploymentVersionIndex();
        for (Map.Entry<String, SortedSet<DeploymentVersionObject>> entry : index.entrySet()) {
            SortedSet<DeploymentVersionObject> versions = entry.getValue();
            while (versions.size() > deploymentVersionLimit) {
                DeploymentVersionObject head = versions.first();
                // We can be called while unmarshalling the database, hence we need to ensure that we do not use the
                // public API as this one throws an exception while this repository is busy. See ACE-449.
                internalRemove(head);
                versions.remove(head);
            }
        }
    }
}
