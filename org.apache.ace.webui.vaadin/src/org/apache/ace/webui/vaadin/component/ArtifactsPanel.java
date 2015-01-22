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
package org.apache.ace.webui.vaadin.component;

import java.util.List;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.AssociationManager;
import org.osgi.framework.Constants;

import com.vaadin.data.Item;

/**
 * Provides an object panel for displaying artifacts.
 */
public abstract class ArtifactsPanel extends BaseObjectPanel<ArtifactObject, ArtifactRepository, RepositoryObject, FeatureObject> {
    private final double m_cacheRate;
    private final int m_pageLength;

    /**
     * Creates a new {@link ArtifactsPanel} instance.
     * 
     * @param associations
     *            the assocation-holder object;
     * @param associationMgr
     *            the helper for creating/removing associations.
     */
    public ArtifactsPanel(AssociationHelper associations, AssociationManager associationMgr, double cacheRate, int pageLength) {
        super(associations, associationMgr, "Artifact", UIExtensionFactory.EXTENSION_POINT_VALUE_ARTIFACT, true, ArtifactObject.class);

        m_cacheRate = cacheRate;
        m_pageLength = pageLength;

        setCacheRate(m_cacheRate);
        setPageLength(m_pageLength);
    }

    @Override
    public void populate() {
        super.populate();
        // For some reason, we need to explicitly set these two properties as Vaadin seems to loose their values
        // somewhere...
        setCacheRate(m_cacheRate);
        setPageLength(m_pageLength);
    }

    @Override
    protected void defineTableColumns() {
        super.defineTableColumns();

        setColumnCollapsed(OBJECT_DESCRIPTION, true);
    }

    @Override
    protected Artifact2FeatureAssociation doCreateRightSideAssociation(String artifactId, String featureId) {
        return m_associationManager.createArtifact2FeatureAssociation(artifactId, featureId);
    }

    @Override
    protected boolean doRemoveRightSideAssociation(ArtifactObject object, FeatureObject other) {
        List<Artifact2FeatureAssociation> associations = object.getAssociationsWith(other);
        for (Artifact2FeatureAssociation association : associations) {
            m_associationManager.removeAssociation(association);
        }
        return true;
    }

    @Override
    protected String getDisplayName(ArtifactObject artifact) {
        String bsn = artifact.getAttribute(Constants.BUNDLE_SYMBOLICNAME);
        if (bsn != null) {
            return getVersion(artifact);
        }
        return artifact.getName();
    }

    @Override
    protected String getParentDisplayName(ArtifactObject artifact) {
        String bsn = artifact.getAttribute(Constants.BUNDLE_SYMBOLICNAME);
        if (bsn != null) {
            return bsn;
        }
        return artifact.getName();
    }

    @Override
    protected String getParentId(ArtifactObject artifact) {
        String bsn = artifact.getAttribute(Constants.BUNDLE_SYMBOLICNAME);
        if (bsn != null) {
            return bsn;
        }
        return null;
    }

    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        ArtifactObject artifact = (ArtifactObject) entity;
        if (ArtifactObject.TOPIC_ADDED.equals(topic)) {
            addToTable(artifact);
        }
        if (ArtifactObject.TOPIC_REMOVED.equals(topic)) {
            removeFromTable(artifact);
        }
        if (ArtifactObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
            update(artifact);
        }
    }

    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return (entity instanceof ArtifactObject) && !isResourceProcessor((ArtifactObject) entity);
    }

    @Override
    protected void populateItem(ArtifactObject artifact, Item item) {
        item.getItemProperty(OBJECT_NAME).setValue(getDisplayName(artifact));
        item.getItemProperty(OBJECT_DESCRIPTION).setValue(artifact.getDescription());
        item.getItemProperty(ACTION_UNLINK).setValue(createRemoveLinkButton(artifact));
        item.getItemProperty(ACTION_DELETE).setValue(createRemoveItemButton(artifact));
    }

    private String getVersion(ArtifactObject artifact) {
        String bv = artifact.getAttribute(Constants.BUNDLE_VERSION);
        if (bv != null) {
            return bv;
        }
        return "";
    }

    /**
     * Returns whether or not the given artifact is actually a resource processor.
     * 
     * @param artifact
     *            the artifact to test, cannot be <code>null</code>.
     * @return <code>true</code> if the given artifact is a resource processor, <code>false</code> otherwise.
     */
    private boolean isResourceProcessor(ArtifactObject artifact) {
        return artifact.getAttribute(BundleHelper.KEY_RESOURCE_PROCESSOR_PID) != null;
    }
}
