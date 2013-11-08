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

import java.util.Collection;
import java.util.List;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.ApprovalState;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.domain.NamedObjectFactory;
import org.apache.ace.webui.vaadin.AssociationManager;

import com.vaadin.data.Item;
import com.vaadin.terminal.Resource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Embedded;

/**
 * Provides an object panel for displaying (stateful) targets.
 */
public abstract class TargetsPanel extends BaseObjectPanel<TargetObject, TargetRepository, DistributionObject, RepositoryObject> {

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
        super(associations, associationRemover, "Target", UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET, true, TargetObject.class);
    }

    /**
     * Approves all selected targets in a single go.
     * 
     * @return <code>true</code> if at least one target of the current selection was approved, <code>false</code>
     *         otherwise.
     */
    public boolean approveSelectedTargets() {
        Collection<?> selection = (Collection<?>) getValue();

        boolean result = false;
        for (Object itemID : selection) {
            StatefulTargetObject sto = getStatefulTargetFromId(itemID);
            if (needsApproval(sto)) {
                sto.approve();
                result = true;
            }
        }

        return result;
    }

    /**
     * Tests whether a target with the given identifier needs approval.
     * 
     * @param itemID
     *            the identifier of the object that should be checked, cannot be <code>null</code>.
     * @return <code>true</code> if the target represented by the given identifier needs approval, <code>false</code>
     *         otherwise.
     */
    public boolean isItemApproveNeeded(Object itemID) {
        return needsApproval(getStatefulTargetFromId(itemID));
    }

    /**
     * Tests whether a target with the given identifier needs be registered.
     * 
     * @param itemID
     *            the identifier of the object that should be checked, cannot be <code>null</code>.
     * @return <code>true</code> if the target represented by the given identifier needs registration,
     *         <code>false</code> otherwise.
     */
    public boolean isItemRegistrationNeeded(Object itemID) {
        return needsRegistration(getStatefulTargetFromId(itemID));
    }

    /**
     * Called to populate this table, which is overridden in order to show unregistered targets (if needed).
     */
    public void populate() {
        removeAllItems();
        // All unregistered items aren't yet present in our TargetRepo, so we must add them explicitly...
        for (StatefulTargetObject object : getStatefulTargetRepository().get()) {
            if (!object.isRegistered()) {
                addToTable(object);
            }
        }
        for (TargetObject object : getRepository().get()) {
            addToTable(object);
        }
        // Ensure the table is properly sorted...
        sort();
    }

    /**
     * Registers all selected targets in a single go.
     * 
     * @return <code>true</code> if at least one target of the current selection was registered, <code>false</code>
     *         otherwise.
     */
    public boolean registerSelectedTargets() {
        Collection<?> selection = (Collection<?>) getValue();

        boolean result = false;
        for (Object itemID : selection) {
            StatefulTargetObject sto = getStatefulTargetFromId(itemID);
            if (needsRegistration(sto)) {
                sto.register();
                result = true;
            }
        }

        return result;
    }

    protected void defineTableColumns() {
        addContainerProperty(ICON, Resource.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(OBJECT_NAME, String.class, null);
        addContainerProperty(REGISTRATION_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(STORE_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(PROVISIONING_STATE_ICON, Embedded.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(ACTION_UNLINK, Button.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(ACTION_DELETE, Button.class, null, "", null, ALIGN_CENTER);

        setColumnWidth(ICON, ICON_WIDTH);
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
    protected Distribution2TargetAssociation doCreateLeftSideAssociation(String distributionId, String targetId) {
        return m_associationManager.createDistribution2TargetAssociation(distributionId, targetId);
    }

    @Override
    protected boolean doRemoveLeftSideAssociation(DistributionObject distribution, TargetObject target) {
        List<Distribution2TargetAssociation> associations = target.getAssociationsWith(distribution);
        for (Distribution2TargetAssociation association : associations) {
            m_associationManager.removeAssociation(association);
        }
        return true;
    }

    @Override
    protected String getDisplayName(TargetObject object) {
        return object.getID();
    }

    protected abstract StatefulTargetRepository getStatefulTargetRepository();

    @Override
    protected WorkingState getWorkingState(RepositoryObject object) {
        final StatefulTargetObject statefulTarget = asStatefulTargetObject(object);
        if (statefulTarget != null && statefulTarget.isRegistered()) {
            return super.getWorkingState(statefulTarget.getTargetObject());
        }
        return WorkingState.Unchanged;
    }

    @Override
    protected void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event) {
        if (entity instanceof StatefulTargetObject) {
            StatefulTargetObject target = (StatefulTargetObject) entity;
            if (StatefulTargetObject.TOPIC_ADDED.equals(topic)) {
                addToTable(target);
            }
            if (StatefulTargetObject.TOPIC_REMOVED.equals(topic)) {
                removeFromTable(target);
            }
            if (StatefulTargetObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
                update(target);
            }
        }
        else {
            TargetObject target = (TargetObject) entity;
            if (TargetObject.TOPIC_ADDED.equals(topic)) {
                addToTable(target);
            }
            if (TargetObject.TOPIC_REMOVED.equals(topic)) {
                removeFromTable(target);
            }
            if (TargetObject.TOPIC_CHANGED.equals(topic) || RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic)) {
                update(target);
            }
        }
    }

    @Override
    protected void handleItemDoubleClick(Object itemId) {
        StatefulTargetObject object = getStatefulTargetFromId(itemId);

        NamedObject namedObject = NamedObjectFactory.getNamedObject(object);
        if (namedObject != null) {
            showEditWindow(namedObject);
        }
    }

    @Override
    protected void handleItemRemoveObject(Object itemId) {
        StatefulTargetObject object = getStatefulTargetFromId(itemId);
        if (object != null) {
            getStatefulTargetRepository().remove(object);
        }
    }

    @Override
    protected boolean isSupportedEntity(RepositoryObject entity) {
        return (entity instanceof StatefulTargetObject) || (entity instanceof TargetObject);
    }

    protected void populateItem(TargetObject target, Item item) {
        StatefulTargetObject sto = asStatefulTargetObject(target);

        item.getItemProperty(OBJECT_NAME).setValue(target.getID());
        if (sto != null) {
            item.getItemProperty(REGISTRATION_STATE_ICON).setValue(getRegistrationStateIcon(sto));
            item.getItemProperty(STORE_STATE_ICON).setValue(getStoreStateIcon(sto));
            item.getItemProperty(PROVISIONING_STATE_ICON).setValue(getProvisioningStateIcon(sto));
        }
        item.getItemProperty(ACTION_UNLINK).setValue(createRemoveLinkButton(target));
        item.getItemProperty(ACTION_DELETE).setValue(createRemoveItemButton(target));
    }

    /**
     * Adds a given repository object to this table.
     * 
     * @param object
     *            the repository object to add, cannot be <code>null</code>.
     */
    private void addToTable(StatefulTargetObject object) {
        String itemId = object.getDefinition();

        Item item = addItem(itemId);
        if (item != null) {
            populateItem(object, item);
        }

        setChildrenAllowed(itemId, false);
    }

    private StatefulTargetObject asStatefulTargetObject(RepositoryObject entity) {
        if (entity instanceof StatefulTargetObject) {
            return (StatefulTargetObject) entity;
        }
        return getStatefulTargetRepository().get(((TargetObject) entity).getDefinition());
    }

    private String getDisplayName(StatefulTargetObject object) {
        return object.getID();
    }

    private Embedded getProvisioningStateIcon(StatefulTargetObject object) {
        String name = object.getProvisioningState().name();
        Resource res = createIconResource("target_provisioning_" + name);
        return createIcon(name, res);
    }

    private Embedded getRegistrationStateIcon(StatefulTargetObject object) {
        String name = object.getRegistrationState().name();
        Resource res = createIconResource("target_" + name);
        return createIcon(name, res);
    }

    /**
     * @param itemID
     *            the target definition of the stateful target representation to get, cannot be <code>null</code>.
     * @return the stateful target representation, can be <code>null</code>.
     */
    private StatefulTargetObject getStatefulTargetFromId(Object itemID) {
        return getStatefulTargetRepository().get((String) itemID);
    }

    private Embedded getStoreStateIcon(StatefulTargetObject object) {
        String name = object.getStoreState().name();
        Resource res = createIconResource("target_store_" + name);
        return createIcon(name, res);
    }

    private boolean needsApproval(StatefulTargetObject target) {
        return (target != null) && !target.getAutoApprove() && target.needsApprove() && (target.getApprovalState() != ApprovalState.Approved);
    }

    private boolean needsRegistration(StatefulTargetObject target) {
        return (target != null) && !target.isRegistered();
    }

    private void populateItem(StatefulTargetObject statefulTarget, Item item) {
        item.getItemProperty(OBJECT_NAME).setValue(statefulTarget.getID());
        item.getItemProperty(REGISTRATION_STATE_ICON).setValue(getRegistrationStateIcon(statefulTarget));
        item.getItemProperty(STORE_STATE_ICON).setValue(getStoreStateIcon(statefulTarget));
        item.getItemProperty(PROVISIONING_STATE_ICON).setValue(getProvisioningStateIcon(statefulTarget));
        item.getItemProperty(ACTION_UNLINK).setValue(createRemoveLinkButton(statefulTarget, getDisplayName(statefulTarget)));
        item.getItemProperty(ACTION_DELETE).setValue(createRemoveItemButton(statefulTarget, getDisplayName(statefulTarget)));
    }

    /**
     * Updates a given repository object in this table.
     * 
     * @param object
     *            the repository object to update, cannot be <code>null</code>.
     */
    private void update(StatefulTargetObject object) {
        if (object != null) {
            String definition = object.getDefinition();
            if (definition != null) {
                Item item = getItem(definition);
                if (item != null) {
                    populateItem(object, item);
                }
            }
        }
    }
}
