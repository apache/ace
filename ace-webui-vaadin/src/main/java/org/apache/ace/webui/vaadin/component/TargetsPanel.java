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
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.Associations;

import com.vaadin.data.Item;
import com.vaadin.terminal.Resource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.HorizontalLayout;

/**
 *
 */
public abstract class TargetsPanel extends BaseObjectPanel<StatefulTargetObject, StatefulTargetRepository> {

    private static final String REGISTRATION_STATE_ICON = "regStateIcon";
    private static final String PROVISIONING_STATE_ICON = "provStateIcon";
    private static final String STORE_STATE_ICON = "storeStateIcon";

    /**
     * Creates a new {@link TargetsPanel} instance.
     * 
     * @param associations the assocation-holder object.
     */
    public TargetsPanel(Associations associations) {
        super(associations, "Target", UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET, true /* hasEdit */);
    }

    @Override
    protected Button createRemoveItemButton(StatefulTargetObject object) {
        Button b = super.createRemoveItemButton(object);
        b.setEnabled(object.isRegistered());
        return b;
    }

    /**
     * {@inheritDoc}
     */
    protected void defineTableColumns() {
        addContainerProperty(WORKING_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);

        addContainerProperty(OBJECT_NAME, String.class, null);

        addContainerProperty(REGISTRATION_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(STORE_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(PROVISIONING_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);

        addContainerProperty(ACTIONS, HorizontalLayout.class, null);

        setColumnWidth(WORKING_STATE_ICON, ICON_WIDTH);
        setColumnWidth(REGISTRATION_STATE_ICON, ICON_WIDTH);
        setColumnWidth(STORE_STATE_ICON, ICON_WIDTH);
        setColumnWidth(PROVISIONING_STATE_ICON, ICON_WIDTH);
    }

    /**
     * {@inheritDoc}
     */
    protected void doHandleEvent(String topic, StatefulTargetObject statefulTarget, org.osgi.service.event.Event event) {
        if (StatefulTargetObject.TOPIC_ADDED.equals(topic)) {
            add(statefulTarget);
        }
        if (StatefulTargetObject.TOPIC_REMOVED.equals(topic)) {
            remove(statefulTarget);
        }
        if (StatefulTargetObject.TOPIC_CHANGED.equals(topic) || StatefulTargetObject.TOPIC_STATUS_CHANGED.equals(topic)
            || StatefulTargetObject.TOPIC_AUDITEVENTS_CHANGED.equals(topic)
            || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
            update(statefulTarget);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean doRemoveLeftSideAssociation(StatefulTargetObject object, RepositoryObject other) {
        List<Distribution2TargetAssociation> associations = object.getAssociationsWith((DistributionObject) other);
        for (Distribution2TargetAssociation association : associations) {
            removeAssocation(association);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected WorkingState getWorkingState(RepositoryObject object) {
        final StatefulTargetObject statefulTarget = (StatefulTargetObject) object;
        if (statefulTarget.isRegistered()) {
            return super.getWorkingState(statefulTarget.getTargetObject());
        }
        return WorkingState.Unchanged;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return entity instanceof StatefulTargetObject;
    }

    /**
     * {@inheritDoc}
     */
    protected void populateItem(StatefulTargetObject object, Item item) {
        item.getItemProperty(WORKING_STATE_ICON).setValue(getWorkingStateIcon(object));

        item.getItemProperty(OBJECT_NAME).setValue(object.getID());

        item.getItemProperty(REGISTRATION_STATE_ICON).setValue(getRegistrationStateIcon(object));
        item.getItemProperty(STORE_STATE_ICON).setValue(getStoreStateIcon(object));
        item.getItemProperty(PROVISIONING_STATE_ICON).setValue(getProvisioningStateIcon(object));

        item.getItemProperty(ACTIONS).setValue(createActionButtons(object));
    }

    /**
     * @param association
     */
    protected abstract void removeAssocation(Distribution2TargetAssociation association);

    /**
     * @param object
     * @return
     */
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
