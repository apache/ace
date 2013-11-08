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
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.AssociationManager;

import com.vaadin.data.Item;

/**
 * Provides an object panel for displaying distributions.
 */
public abstract class DistributionsPanel extends BaseObjectPanel<DistributionObject, DistributionRepository, FeatureObject, TargetObject> {

    /**
     * Creates a new {@link DistributionsPanel} instance.
     * 
     * @param associations
     *            the assocation-holder object;
     * @param associationRemover
     *            the helper for removing associations.
     */
    public DistributionsPanel(AssociationHelper associations, AssociationManager associationRemover) {
        super(associations, associationRemover, "Distribution", UIExtensionFactory.EXTENSION_POINT_VALUE_DISTRIBUTION, true, DistributionObject.class);
    }

    @Override
    protected Feature2DistributionAssociation doCreateLeftSideAssociation(String featureId, String distributionId) {
        return m_associationManager.createFeature2DistributionAssociation(featureId, distributionId);
    }

    @Override
    protected Distribution2TargetAssociation doCreateRightSideAssociation(String distribution, String target) {
        return m_associationManager.createDistribution2TargetAssociation(distribution, target);
    }

    @Override
    protected boolean doRemoveLeftSideAssociation(FeatureObject feature, DistributionObject object) {
        List<Feature2DistributionAssociation> associations = object.getAssociationsWith(feature);
        for (Feature2DistributionAssociation association : associations) {
            m_associationManager.removeAssociation(association);
        }
        return true;
    }

    @Override
    protected boolean doRemoveRightSideAssociation(DistributionObject object, TargetObject target) {
        List<Distribution2TargetAssociation> associations = object.getAssociationsWith(target);
        for (Distribution2TargetAssociation association : associations) {
            m_associationManager.removeAssociation(association);
        }
        return true;
    }

    @Override
    protected String getDisplayName(DistributionObject object) {
        return object.getName();
    }

    @Override
    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        DistributionObject distribution = (DistributionObject) entity;
        if (DistributionObject.TOPIC_ADDED.equals(topic)) {
            addToTable(distribution);
        }
        if (DistributionObject.TOPIC_REMOVED.equals(topic)) {
            removeFromTable(distribution);
        }
        if (DistributionObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
            update(distribution);
        }
    }

    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return entity instanceof DistributionObject;
    }

    @Override
    protected void populateItem(DistributionObject distribution, Item item) {
        item.getItemProperty(OBJECT_NAME).setValue(distribution.getName());
        item.getItemProperty(OBJECT_DESCRIPTION).setValue(distribution.getDescription());
        item.getItemProperty(ACTION_UNLINK).setValue(createRemoveLinkButton(distribution));
        item.getItemProperty(ACTION_DELETE).setValue(createRemoveItemButton(distribution));
    }
}
