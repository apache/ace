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
import org.apache.ace.webui.vaadin.AssociationRemover;
import org.apache.ace.webui.vaadin.Associations;

import com.vaadin.data.Item;

/**
 * Provides an object panel for displaying artifacts.
 */
public abstract class ArtifactsPanel extends BaseObjectPanel<ArtifactObject, ArtifactRepository> {

    /**
     * Creates a new {@link ArtifactsPanel} instance.
     * 
     * @param associations the assocation-holder object;
     * @param associationRemover the helper for removing associations.
     */
    public ArtifactsPanel(Associations associations, AssociationRemover associationRemover) {
        super(associations, associationRemover, "Artifact", UIExtensionFactory.EXTENSION_POINT_VALUE_ARTIFACT, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doRemoveRightSideAssociation(ArtifactObject object, RepositoryObject other) {
        List<Artifact2FeatureAssociation> associations = object.getAssociationsWith((FeatureObject) other);
        for (Artifact2FeatureAssociation association : associations) {
            m_associationRemover.removeAssociation(association);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        ArtifactObject artifact = (ArtifactObject) entity;
        if (ArtifactObject.TOPIC_ADDED.equals(topic)) {
            add(artifact);
        }
        if (ArtifactObject.TOPIC_REMOVED.equals(topic)) {
            remove(artifact);
        }
        if (ArtifactObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
            update(artifact);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return (entity instanceof ArtifactObject) && !isResourceProcessor((ArtifactObject) entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateItem(ArtifactObject artifact, Item item) {
        item.getItemProperty(WORKING_STATE_ICON).setValue(getWorkingStateIcon(artifact));
        item.getItemProperty(OBJECT_NAME).setValue(artifact.getName());
        item.getItemProperty(OBJECT_DESCRIPTION).setValue(artifact.getDescription());
        item.getItemProperty(ACTIONS).setValue(createActionButtons(artifact));
    }

    /**
     * Returns whether or not the given artifact is actually a resource processor.
     * 
     * @param artifact the artifact to test, cannot be <code>null</code>.
     * @return <code>true</code> if the given artifact is a resource processor, <code>false</code> otherwise.
     */
    private boolean isResourceProcessor(ArtifactObject artifact) {
        return artifact.getAttribute(BundleHelper.KEY_RESOURCE_PROCESSOR_PID) != null;
    }
}
