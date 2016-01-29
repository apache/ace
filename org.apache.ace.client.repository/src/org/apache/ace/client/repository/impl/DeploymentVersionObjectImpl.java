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
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Implementation class for the DeploymentVersionObject. For 'what it does', see DeploymentVersionObject, for 'how it
 * works', see RepositoryObjectImpl.
 */
public class DeploymentVersionObjectImpl extends RepositoryObjectImpl<DeploymentVersionObject> implements DeploymentVersionObject {
    private static final String[] DEFINING_KEYS = new String[] { KEY_TARGETID, KEY_VERSION };

    private final static String XML_NODE = "deploymentversion";
    private final static String ARTIFACTS_XML_NODE = "artifacts";

    private volatile DeploymentArtifact[] m_deploymentArtifacts;

    DeploymentVersionObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier) {
        super(reader, notifier, XML_NODE);
    }

    /**
     * Creates a new <code>DeploymentVersionObjectImpl</code>.
     * 
     * @param attributes
     *            A map of attributes; must include <code>KEY_TARGETID</code>, <code>KEY_VERSION</code>.
     * @param deploymentArtifacts
     *            A (possibly empty) array of DeploymentArtifacts.
     * @param notifier
     *            A change notifier to be used by this object.
     */
    DeploymentVersionObjectImpl(Map<String, String> attributes, ChangeNotifier notifier) {
        super(checkAttributes(attributes, DEFINING_KEYS, new boolean[] { false, false }), notifier, XML_NODE);
    }

    DeploymentVersionObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier) {
        super(checkAttributes(attributes, DEFINING_KEYS, new boolean[] { false, false }), tags, notifier, XML_NODE);
    }

    public DeploymentArtifact[] getDeploymentArtifacts() {
        DeploymentArtifact[] artifacts;
        synchronized (this) {
            artifacts = m_deploymentArtifacts;
        }
        if (artifacts == null) {
            throw new IllegalStateException("This object is not fully initialized yet.");
        }
        return artifacts.clone();
    }

    public String getTargetID() {
        return getAttribute(KEY_TARGETID);
    }

    public String getVersion() {
        return getAttribute(KEY_VERSION);
    }

    @Override
    public String toString() {
        return "DeploymentVersionObject[target=" + getTargetID() + ",version=" + getVersion() + "]";
    }

    @Override
    String[] getDefiningKeys() {
        return DEFINING_KEYS;
    }

    void setDeploymentArtifacts(DeploymentArtifact[] deploymentArtifacts) {
        if (deploymentArtifacts == null) {
            throw new IllegalArgumentException("The argument should not be null.");
        }
        synchronized (this) {
            DeploymentArtifact[] old = m_deploymentArtifacts;
            if (old != null) {
                throw new IllegalStateException("Deployment artifacts are already set; this can only be done once.");
            }
            m_deploymentArtifacts = deploymentArtifacts;
        }
    }

    @Override
    protected void readCustom(HierarchicalStreamReader reader) {
        List<DeploymentArtifact> result = new ArrayList<>();
        reader.moveDown();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            DeploymentArtifactImpl deploymentArtifactImpl = new DeploymentArtifactImpl(reader);
            result.add(deploymentArtifactImpl);
            reader.moveUp();
        }
        setDeploymentArtifacts(result.toArray(new DeploymentArtifact[result.size()]));
        reader.moveUp();
    }

    @Override
    protected void writeCustom(HierarchicalStreamWriter writer) {
        DeploymentArtifact[] artifacts;
        synchronized (this) {
            artifacts = m_deploymentArtifacts;
        }

        if (artifacts == null) {
            throw new IllegalStateException("This object is not fully initialized, so it cannot be serialized.");
        }
        writer.startNode(ARTIFACTS_XML_NODE);
        for (DeploymentArtifact da : artifacts) {
            ((DeploymentArtifactImpl) da).marshal(writer);
        }
        writer.endNode();
    }
}
