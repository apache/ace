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

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.AssociationManager;

import com.vaadin.data.Item;
import com.vaadin.terminal.Resource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Embedded;

/**
 * Provides an object panel for displaying (stateful) targets.
 */
public abstract class TargetsPanel extends BaseObjectPanel<StatefulTargetObject, StatefulTargetRepository, DistributionObject, RepositoryObject> {

    private static final String REGISTRATION_STATE_ICON = "regStateIcon";
    private static final String PROVISIONING_STATE_ICON = "provStateIcon";
    private static final String STORE_STATE_ICON = "storeStateIcon";

    /**
     * Creates a new {@link TargetsPanel} instance.
     * 
     * @param associations
     *            the assocation-holder object;
     * @param associationRemover
     *            the helper for removing associations.
     */
    public TargetsPanel(AssociationHelper associations, AssociationManager associationRemover) {
        super(associations, associationRemover, "Target", UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET, true /* hasEdit */);
    }

    protected void defineTableColumns() {
        addContainerProperty(ICON, Resource.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(OBJECT_NAME, String.class, null);
        addContainerProperty(REGISTRATION_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(STORE_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(PROVISIONING_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(ACTION_UNLINK, Button.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(ACTION_DELETE, Button.class, null, "", null, ALIGN_CENTER);

        setColumnWidth(ICON, FIXED_COLUMN_WIDTH);
        setColumnWidth(ACTION_UNLINK, FIXED_COLUMN_WIDTH);
        setColumnWidth(ACTION_DELETE, FIXED_COLUMN_WIDTH);
        setColumnWidth(REGISTRATION_STATE_ICON, ICON_WIDTH);
        setColumnWidth(STORE_STATE_ICON, ICON_WIDTH);
        setColumnWidth(PROVISIONING_STATE_ICON, ICON_WIDTH);

        setColumnCollapsible(ICON, false);
        setColumnCollapsible(ACTION_UNLINK, false);
        setColumnCollapsible(ACTION_DELETE, false);
    }

    @Override
    protected boolean doCreateLeftSideAssociation(DistributionObject distribution, StatefulTargetObject target) {
        m_associationManager.createDistribution2TargetAssociation(distribution, target);
        return true;
    }

    @Override
    protected boolean doRemoveLeftSideAssociation(DistributionObject distribution, StatefulTargetObject target) {
        List<Distribution2TargetAssociation> associations = target.getAssociationsWith(distribution);
        for (Distribution2TargetAssociation association : associations) {
            m_associationManager.removeAssociation(association);
        }
        return true;
    }

    @Override
    protected String getDisplayName(StatefulTargetObject object) {
        return object.getID();
    }

    @Override
    protected WorkingState getWorkingState(RepositoryObject object) {
        final StatefulTargetObject statefulTarget = (StatefulTargetObject) object;
        if (statefulTarget.isRegistered()) {
            return super.getWorkingState(statefulTarget.getTargetObject());
        }
        return WorkingState.Unchanged;
    }

    @Override
    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        StatefulTargetObject statefulTarget = asStatefulTargetObject(entity);
        if (StatefulTargetObject.TOPIC_ADDED.equals(topic)) {
            add(statefulTarget);
        }
        if (StatefulTargetObject.TOPIC_REMOVED.equals(topic)) {
            remove(statefulTarget);
        }
        if (topic.endsWith("CHANGED")) {
            update(statefulTarget);
        }
    }

    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return (entity instanceof StatefulTargetObject) || (entity instanceof TargetObject);
    }

    protected void populateItem(StatefulTargetObject target, Item item) {
        item.getItemProperty(OBJECT_NAME).setValue(target.getID());
        item.getItemProperty(REGISTRATION_STATE_ICON).setValue(getRegistrationStateIcon(target));
        item.getItemProperty(STORE_STATE_ICON).setValue(getStoreStateIcon(target));
        item.getItemProperty(PROVISIONING_STATE_ICON).setValue(getProvisioningStateIcon(target));
        item.getItemProperty(ACTION_UNLINK).setValue(new RemoveLinkButton(target));
        item.getItemProperty(ACTION_DELETE).setValue(createRemoveItemButton(target));
    }

    /**
     * 
     * @param entity
     * @return
     */
    private StatefulTargetObject asStatefulTargetObject(RepositoryObject entity) {
        if (entity instanceof StatefulTargetObject) {
            return (StatefulTargetObject) entity;
        }
        return getFromId(((TargetObject) entity).getDefinition());
    }

    private RemoveItemButton createRemoveItemButton(StatefulTargetObject object) {
        RemoveItemButton b = new RemoveItemButton(object);
        b.setEnabled(object.isRegistered());
        return b;
    }

    private Embedded getProvisioningStateIcon(StatefulTargetObject object) {
        String name = object.getProvisioningState().name();
        Resource res = createIconResource("target_provisioning_" + name);
        return createIcon(name, res);
    }

    /**
     * @param object
     * @return
     */
    private Embedded getRegistrationStateIcon(StatefulTargetObject object) {
        String name = object.getRegistrationState().name();
        Resource res = createIconResource("target_" + name);
        return createIcon(name, res);
    }

    /**
     * @param object
     * @return
     */
    private Embedded getStoreStateIcon(StatefulTargetObject object) {
        String name = object.getStoreState().name();
        Resource res = createIconResource("target_store_" + name);
        return createIcon(name, res);
    }
}
