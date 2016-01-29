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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.domain.NamedObjectFactory;
import org.apache.ace.webui.vaadin.AssociationManager;
import org.apache.ace.webui.vaadin.EditWindow;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventHandler;

import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.event.Transferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.TargetDetails;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.event.dd.acceptcriteria.Or;
import com.vaadin.terminal.Resource;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.CellStyleGenerator;
import com.vaadin.ui.TreeTable;
import com.vaadin.ui.Window.Notification;

/**
 * Provides a custom table for displaying artifacts, features and so on.
 */
abstract class BaseObjectPanel<REPO_OBJ extends RepositoryObject, REPO extends ObjectRepository<REPO_OBJ>, LEFT_ASSOC_REPO_OBJ extends RepositoryObject, RIGHT_ASSOC_REPO_OBJ extends RepositoryObject> extends TreeTable implements EventHandler,
    CellStyleGenerator, ValueChangeListener {

    /**
     * Drop handler for associations.
     */
    private class AssociationDropHandler implements DropHandler {

        public void drop(DragAndDropEvent event) {
            Transferable transferable = event.getTransferable();

            TargetDetails targetDetails = event.getTargetDetails();
            if (!(transferable instanceof Table.TableTransferable) || !(targetDetails instanceof Table.AbstractSelectTargetDetails)) {
                return;
            }

            Table.TableTransferable tt = (Table.TableTransferable) transferable;
            Table.AbstractSelectTargetDetails ttd = (Table.AbstractSelectTargetDetails) targetDetails;

            // get the active selection, but only if we drag from the same table
            Set<?> selection = m_associations.isActiveTable(tt.getSourceComponent()) ? m_associations.getActiveSelection() : null;

            if (tt.getSourceComponent().equals(m_leftTable)) {
                if (selection != null) {
                    for (Object item : selection) {
                        createLeftSideAssociation(item, ttd.getItemIdOver());
                    }
                }
                else {
                    createLeftSideAssociation(tt.getItemId(), ttd.getItemIdOver());
                }
            }
            else if (tt.getSourceComponent().equals(m_rightTable)) {
                if (selection != null) {
                    for (Object item : selection) {
                        createRightSideAssociation(ttd.getItemIdOver(), item);
                    }
                }
                else {
                    createRightSideAssociation(ttd.getItemIdOver(), tt.getItemId());
                }
            }
        }

        public AcceptCriterion getAcceptCriterion() {
            return new Or(VerticalLocationIs.MIDDLE);
        }
    }

    private static enum Direction {
        BOTH, LEFT, RIGHT;

        boolean isGoLeft() {
            return this == BOTH || this == LEFT;
        }

        boolean isGoRight() {
            return this == BOTH || this == RIGHT;
        }
    }

    /**
     * Provides a small container for {@link UIExtensionFactory} instances.
     */
    private static class UIExtensionFactoryHolder implements Comparable<UIExtensionFactoryHolder> {
        private final ServiceReference<UIExtensionFactory> m_serviceRef;
        private final WeakReference<UIExtensionFactory> m_extensionFactory;

        public UIExtensionFactoryHolder(ServiceReference<UIExtensionFactory> serviceRef, UIExtensionFactory extensionFactory) {
            m_serviceRef = serviceRef;
            m_extensionFactory = new WeakReference<>(extensionFactory);
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(UIExtensionFactoryHolder other) {
            ServiceReference<UIExtensionFactory> thatServiceRef = other.m_serviceRef;
            ServiceReference<UIExtensionFactory> thisServiceRef = m_serviceRef;
            // Sort in reverse order so that the highest rankings come first...
            return thatServiceRef.compareTo(thisServiceRef);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof UIExtensionFactoryHolder)) {
                return false;
            }
            UIExtensionFactoryHolder other = (UIExtensionFactoryHolder) obj;
            return m_serviceRef.equals(other.m_serviceRef);
        }

        /**
         * @return the {@link UIExtensionFactory}, can be <code>null</code> if it has been GC'd before this method call.
         */
        public UIExtensionFactory getUIExtensionFactory() {
            return m_extensionFactory.get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return m_serviceRef.hashCode() ^ m_extensionFactory.hashCode();
        }
    }

    protected static final String ICON = "icon";
    protected static final String OBJECT_NAME = "name";
    protected static final String OBJECT_DESCRIPTION = "description";
    protected static final String ACTION_UNLINK = "unlink";
    protected static final String ACTION_DELETE = "delete";

    protected static final int ICON_HEIGHT = 16;
    protected static final int ICON_WIDTH = 16;
    /** Empirically determined (most common width appears to be 30px). */
    protected static final int FIXED_COLUMN_WIDTH = 30;

    protected final AssociationHelper m_associations;
    protected final AssociationManager m_associationManager;
    protected final Class<REPO_OBJ> m_entityType;

    private final List<UIExtensionFactoryHolder> m_extensionFactories;
    private final String m_extensionPoint;

    protected BaseObjectPanel<LEFT_ASSOC_REPO_OBJ, ?, ?, ?> m_leftTable;
    protected BaseObjectPanel<RIGHT_ASSOC_REPO_OBJ, ?, ?, ?> m_rightTable;

    /**
     * Creates a new {@link BaseObjectPanel} instance.
     * 
     * @param associations
     *            the associations for this panel;
     * @param associationRemover
     *            the association remove to use for removing associations;
     * @param name
     *            the name of this panel;
     * @param extensionPoint
     *            the extension point to listen for;
     * @param hasEdit
     *            <code>true</code> if double clicking an row in this table should show an editor, <code>false</code> to
     *            disallow editing.
     */
    public BaseObjectPanel(AssociationHelper associations, AssociationManager associationRemover, String name, String extensionPoint, boolean hasEdit, Class<REPO_OBJ> entityType) {
        super(name + "s");

        m_associations = associations;
        m_associationManager = associationRemover;
        m_extensionFactories = new ArrayList<>();
        m_extensionPoint = extensionPoint;
        m_entityType = entityType;

        setSizeFull();
        setAnimationsEnabled(false);
        setCellStyleGenerator(this);
        setSelectable(true);
        setMultiSelect(true);
        setImmediate(true);
        setDragMode(TableDragMode.MULTIROW);
        setColumnCollapsingAllowed(true);

        defineTableColumns();

        setItemIconPropertyId(ICON);
        setHierarchyColumn(ICON);

        setSortAscending(true);
        setSortContainerPropertyId(OBJECT_NAME);

        if (hasEdit) {
            addListener(new ItemClickListener() {
                public void itemClick(ItemClickEvent event) {
                    if (event.isDoubleClick()) {
                        handleItemDoubleClick(event.getItemId());
                    }
                }
            });
        }

        addListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                Property property = event.getProperty();
                if (BaseObjectPanel.this == property) {
                    updateActiveTable();
                }
            }
        });
    }

    /**
     * Called by the dependency manager in case a new {@link UIExtensionFactory} is registered.
     * 
     * @param ref
     *            the service reference of the new extension;
     * @param factory
     *            the extension instance itself.
     */
    public final void addExtension(ServiceReference<UIExtensionFactory> ref, UIExtensionFactory factory) {
        synchronized (m_extensionFactories) {
            m_extensionFactories.add(new UIExtensionFactoryHolder(ref, factory));
        }
    }

    @Override
    public String getStyle(Object itemId, Object propertyId) {
        Item item = getItem(itemId);

        if (propertyId == null) {
            updateItemIcon(itemId);

            // no propertyId, styling row
            if (m_associations.isAssociated(itemId)) {
                return "associated";
            }
            if (m_associations.isRelated(itemId)) {
                return "related";
            }

            // Try to highlight the parent of any dynamic link...
            Collection<?> children = getChildren(itemId);
            if (children != null && !children.isEmpty()) {
                Set<?> activeSelection = m_associations.getActiveSelection();
                if (Collections.disjoint(children, activeSelection)) {
                    // Not in the active selection, check whether we've got an associated or related child...
                    for (Object child : children) {
                        if (m_associations.isAssociated(child)) {
                            return "associated-parent";
                        }
                        if (m_associations.isRelated(child)) {
                            return "related-parent";
                        }
                    }
                }
                else {
                    // one of the children is selected...
                    return "associated-parent";
                }
            }
        }
        else if (OBJECT_DESCRIPTION.equals(propertyId)) {
            return "description";
        }
        else if (ACTION_UNLINK.equals(propertyId)) {
            Button unlinkButton = (Button) item.getItemProperty(propertyId).getValue();
            if (unlinkButton != null) {
                boolean enabled = m_associations.isAssociated(itemId);

                unlinkButton.setEnabled(enabled);
            }
        }
        return null;
    }

    /**
     * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
     */
    public final void handleEvent(org.osgi.service.event.Event event) {
        final RepositoryObject entity = (RepositoryObject) event.getProperty(RepositoryObject.EVENT_ENTITY);
        final String topic = event.getTopic();

        synchronized (getApplication()) {
            if (isSupportedEntity(entity)) {
                handleEvent(topic, entity, event);
            }
            else if (RepositoryAdmin.TOPIC_LOGIN.equals(topic)) {
                populate();
            }
        }
    }

    /**
     * Called by the dependency manager upon initialization of this component.
     * 
     * @param component
     *            the component representing this object.
     */
    public void init(Component component) {
        populate();

        DependencyManager dm = component.getDependencyManager();
        component.add(dm
            .createServiceDependency()
            .setService(UIExtensionFactory.class, "(" + UIExtensionFactory.EXTENSION_POINT_KEY + "=" + m_extensionPoint + ")")
            .setCallbacks("addExtension", "removeExtension"));
    }

    /**
     * Removes all current items and (re)populates this table.
     */
    public void populate() {
        removeAllItems();
        for (REPO_OBJ object : getAllRepositoryObjects()) {
            addToTable(object);
        }
        // Ensure the table is properly sorted...
        sort();
    }

    /**
     * Called by the dependency manager in case a {@link UIExtensionFactory} is unregistered.
     * 
     * @param ref
     *            the service reference of the extension;
     * @param factory
     *            the extension instance itself.
     */
    public final void removeExtension(ServiceReference<UIExtensionFactory> ref, UIExtensionFactory factory) {
        synchronized (m_extensionFactories) {
            m_extensionFactories.remove(new UIExtensionFactoryHolder(ref, factory));
        }
    }

    /**
     * Sets the tables that are associated this this panel.
     * 
     * @param leftTable
     *            the left-side table to associate with, can be <code>null</code>;
     * @param rightTable
     *            the right-side table to associate with, can be <code>null</code>.
     */
    public final void setAssociatedTables(BaseObjectPanel<LEFT_ASSOC_REPO_OBJ, ?, ?, ?> leftTable, BaseObjectPanel<RIGHT_ASSOC_REPO_OBJ, ?, ?, ?> rightTable) {
        m_leftTable = leftTable;
        m_rightTable = rightTable;

        setDropHandler(new AssociationDropHandler());
    }

    /**
     * Creates the left-hand side associations for a given repository object.
     * 
     * @param leftObjectId
     *            the (left-hand side) repository object to create the associations for.
     * @param rightObjectId
     *            the repository object to create the left-hand side associations;
     */
    final void createLeftSideAssociation(Object leftObjectId, Object rightObjectId) {
        Association<LEFT_ASSOC_REPO_OBJ, REPO_OBJ> association = doCreateLeftSideAssociation(String.valueOf(leftObjectId), String.valueOf(rightObjectId));
        if (association != null) {
            m_leftTable.recalculateRelations(Direction.RIGHT);

            // Request the focus again...
            focus();
        }
    }

    /**
     * Creates the right-hand side associations for a given repository object.
     * 
     * @param leftObjectId
     *            the repository object to create the right-hand side associations;
     * @param rightObjectId
     *            the (right-hand side) repository object to create the associations for.
     */
    final void createRightSideAssociation(Object leftObjectId, Object rightObjectId) {
        Association<REPO_OBJ, RIGHT_ASSOC_REPO_OBJ> association = doCreateRightSideAssociation(String.valueOf(leftObjectId), String.valueOf(rightObjectId));
        if (association != null) {
            m_rightTable.recalculateRelations(Direction.LEFT);

            // Request the focus again...
            focus();
        }
    }

    /**
     * Recalculates all relations.
     */
    final void recalculateRelations(Direction direction) {
        Set<String> associated = new HashSet<>();
        Set<String> related = new HashSet<>();
        collectRelations(direction, associated, related);

        m_associations.updateRelations(associated, related);

        refreshAllRowCaches(direction);
    }

    /**
     * Removes the left-hand side associations for a given repository object.
     * 
     * @param leftObject
     *            the (left-hand side) repository object to remove the associations for.
     * @param rightObject
     *            the repository object to remove the left-hand side associations;
     */
    final void removeLeftSideAssociation(LEFT_ASSOC_REPO_OBJ leftObject, REPO_OBJ rightObject) {
        if (doRemoveLeftSideAssociation(leftObject, rightObject)) {
            m_associations.clear();

            m_leftTable.recalculateRelations(Direction.RIGHT);

            // Request the focus again...
            focus();
        }
    }

    /**
     * Removes the right-hand side associations for a given repository object.
     * 
     * @param leftObject
     *            the repository object to remove the right-hand side associations;
     * @param rightObject
     *            the (right-hand side) repository object to remove the associations for.
     */
    final void removeRightSideAssocation(REPO_OBJ leftObject, RIGHT_ASSOC_REPO_OBJ rightObject) {
        if (doRemoveRightSideAssociation(leftObject, rightObject)) {
            m_associations.clear();

            m_rightTable.recalculateRelations(Direction.LEFT);

            // Request the focus again...
            focus();
        }
    }

    /**
     * Updates the active table and recalculates all relations.
     */
    final void updateActiveTable() {
        m_associations.clear();
        m_associations.updateActiveTable(this);
        recalculateRelations(Direction.BOTH);
        // request the focus...
        focus();
    }

    /**
     * Adds a given repository object to this table.
     * 
     * @param object
     *            the repository object to add, cannot be <code>null</code>.
     */
    protected final void addToTable(REPO_OBJ object) {
        String itemId = object.getDefinition();
        String parentId = getParentId(object);

        if ((parentId != null) && !containsId(parentId)) {
            Item parentItem = addItem(parentId);
            if (parentItem != null) {
                populateParentItem(object, parentId, parentItem);
            }
        }

        Item item = addItem(itemId);
        if (item != null) {
            populateItem(object, item);
        }

        if (parentId != null) {
            setParent(itemId, parentId);
            setCollapsed(parentId, true);
            setItemIcon(object);
        }

        setChildrenAllowed(itemId, false);

        // Request the focus again...
        focus();
    }

    /**
     * Collects the item-IDs of the directly associated entities and the related entities based on the current
     * selection.
     * 
     * @param associated
     *            the collection with associated item-IDs, will be filled by this method;
     * @param related
     *            the collection with related item-IDs, will be filled by this method.
     */
    protected final void collectRelations(Direction direction, Collection<String> associated, Collection<String> related) {
        Set<?> value = (Set<?>) getValue();
        List<REPO_OBJ> selection = new ArrayList<>();
        for (Object itemID : value) {
            REPO_OBJ obj = getFromId(itemID);
            if (obj != null) {
                selection.add(obj);
            }
        }

        collectRelations(direction, selection, new HashSet<Class<?>>(), associated, related);
    }

    protected abstract EditWindow createEditor(NamedObject object, List<UIExtensionFactory> extensions);

    /**
     * Factory method to create an embeddable icon.
     * 
     * @param name
     *            the name of the icon to use (is also used as tooltip text);
     * @param res
     *            the resource denoting the actual icon.
     * @return an embeddable icon, never <code>null</code>.
     */
    protected Embedded createIcon(String name, Resource res) {
        Embedded embedded = new Embedded(name, res);
        embedded.setType(Embedded.TYPE_IMAGE);
        embedded.setDescription(name);
        embedded.setHeight(ICON_HEIGHT + "px");
        embedded.setWidth(ICON_WIDTH + "px");
        return embedded;
    }

    /**
     * Factory method to create an icon resource.
     * 
     * @param iconName
     *            the base name of the icon to use, it will be appended with '.png'.
     * @return a {@link Resource} denoting the icon.
     */
    protected ThemeResource createIconResource(String iconName) {
        return new ThemeResource("icons/" + iconName.toLowerCase() + ".png");
    }

    /**
     * Creates a remove-item button for the given repository object.
     * 
     * @param object
     *            the object to create a remove-item button, cannot be <code>null</code>.
     * @return a remove-item button, never <code>null</code>.
     */
    protected final Button createRemoveItemButton(REPO_OBJ object) {
        return createRemoveItemButton(object, getDisplayName(object));
    }

    /**
     * Creates a remove-item button for the given repository object.
     * 
     * @param object
     *            the object to create a remove-item button, cannot be <code>null</code>;
     * @param displayName
     *            the display name for the description of the button, cannot be <code>null</code>.
     * @return a remove-item button, never <code>null</code>.
     */
    protected final Button createRemoveItemButton(RepositoryObject object, String displayName) {
        Button result = new Button();
        result.setIcon(createIconResource("trash"));
        result.setData(object.getDefinition());
        result.setStyleName("small tiny");
        result.setDescription("Delete " + displayName);
        result.setDisableOnClick(true);

        result.addListener(new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent event) {
                try {
                    handleItemRemoveObject(event.getButton().getData());
                }
                catch (Exception e) {
                    // ACE-246: notify user when the removal failed!
                    getWindow().showNotification("Failed to remove item!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                }
            }
        });

        return result;
    }

    /**
     * Creates a remove-link button for the given repository object.
     * 
     * @param object
     *            the object to create a remove-link button, cannot be <code>null</code>.
     * @return a remove-link button, never <code>null</code>.
     */
    protected final Button createRemoveLinkButton(REPO_OBJ object) {
        return createRemoveLinkButton(object, getDisplayName(object));
    }

    /**
     * Creates a remove-link button for the given repository object.
     * 
     * @param object
     *            the object to create a remove-link button, cannot be <code>null</code>;
     * @param displayName
     *            the display name for the description of the button, cannot be <code>null</code>.
     * @return a remove-link button, never <code>null</code>.
     */
    protected final Button createRemoveLinkButton(RepositoryObject object, String displayName) {
        Button result = new Button();
        result.setIcon(createIconResource("unlink"));
        result.setStyleName("small tiny");
        result.setData(object.getDefinition());
        result.setDescription("Unlink " + displayName);
        // Only enable this button when actually selected...
        result.setEnabled(false);
        result.setDisableOnClick(true);

        result.addListener(new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent event) {
                handleItemRemoveLink(event.getButton().getData());
            }

        });

        return result;
    }

    /**
     * Defines the table columns for this panel.
     */
    protected void defineTableColumns() {
        addContainerProperty(ICON, Resource.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(OBJECT_NAME, String.class, null);
        addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
        addContainerProperty(ACTION_UNLINK, Button.class, null, "", null, ALIGN_CENTER);
        addContainerProperty(ACTION_DELETE, Button.class, null, "", null, ALIGN_CENTER);

        setColumnWidth(ICON, ICON_WIDTH);
        setColumnWidth(ACTION_UNLINK, FIXED_COLUMN_WIDTH);
        setColumnWidth(ACTION_DELETE, FIXED_COLUMN_WIDTH);

        setColumnCollapsible(ICON, false);
        setColumnCollapsible(ACTION_UNLINK, false);
        setColumnCollapsible(ACTION_DELETE, false);
    }

    /**
     * Does the actual creation of the left-hand side associations for a given repository object.
     * 
     * @param leftObjectId
     *            the (left-hand side) object ID to create the associations for.
     * @param rightObjectId
     *            the object ID to craete the left-hand side associations;
     * @return the created {@link Association}, or <code>null</code> if the association could not be created.
     */
    protected Association<LEFT_ASSOC_REPO_OBJ, REPO_OBJ> doCreateLeftSideAssociation(String leftObjectId, String rightObjectId) {
        return null;
    }

    /**
     * Does the actual creation of the right-hand side associations for a given repository object.
     * 
     * @param leftObjectId
     *            the object ID to create the right-hand side associations;
     * @param rightObjectId
     *            the (right-hand side) object ID to create the associations for.
     * @return the created {@link Association}, or <code>null</code> if the association could not be created.
     */
    protected Association<REPO_OBJ, RIGHT_ASSOC_REPO_OBJ> doCreateRightSideAssociation(String leftObjectId, String rightObjectId) {
        return null;
    }

    /**
     * Does the actual removal of the left-hand side associations for a given repository object.
     * 
     * @param leftObject
     *            the (left-hand side) repository object to remove the associations for.
     * @param rightObject
     *            the repository object to remove the left-hand side associations;
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    protected boolean doRemoveLeftSideAssociation(LEFT_ASSOC_REPO_OBJ leftObject, REPO_OBJ rightObject) {
        return m_leftTable != null;
    }

    /**
     * Does the actual removal of the right-hand side associations for a given repository object.
     * 
     * @param leftObject
     *            the repository object to remove the right-hand side associations;
     * @param rightObject
     *            the (right-hand side) repository object to remove the associations for.
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    protected boolean doRemoveRightSideAssociation(REPO_OBJ leftObject, RIGHT_ASSOC_REPO_OBJ rightObject) {
        return m_rightTable != null;
    }

    /**
     * Returns all repository objects.
     * 
     * @return an {@link Iterable} with all repository objects, never <code>null</code>.
     */
    protected final Iterable<REPO_OBJ> getAllRepositoryObjects() {
        return getRepository().get();
    }

    /**
     * Returns a user-friendly name for a given repository object.
     * 
     * @param object
     *            the repository object to get the display name for, cannot be <code>null</code>.
     * @return the display name, never <code>null</code>.
     */
    protected abstract String getDisplayName(REPO_OBJ object);

    /**
     * Converts a table-id back to a concrete {@link RepositoryObject}.
     * 
     * @param id
     *            the identifier of the {@link RepositoryObject}, cannot be <code>null</code>.
     * @return a {@link RepositoryObject} instance for the given ID, can be <code>null</code> in case no such object is
     *         found.
     */
    protected final REPO_OBJ getFromId(Object id) {
        return getRepository().get((String) id);
    }

    /**
     * @param object
     * @return a display name for the parent of the given repository object, cannot be <code>null</code>.
     */
    protected String getParentDisplayName(REPO_OBJ object) {
        return object.getDefinition();
    }

    /**
     * Determines the parent Id of a given repository object.
     * 
     * @param object
     *            the repository object to determine the parent for, cannot be <code>null</code>.
     * @return the ID of the parent for the given repository object, or <code>null</code> in case no parent could be
     *         determined.
     */
    protected String getParentId(REPO_OBJ object) {
        return null;
    }

    /**
     * Returns the actual repository for objects.
     * 
     * @return the actual repository for obtaining the repository objects, cannot be <code>null</code>.
     */
    protected abstract REPO getRepository();

    /**
     * Returns the repository administrator.
     * 
     * @return the repository admin, never <code>null</code>.
     */
    protected abstract RepositoryAdmin getRepositoryAdmin();

    /**
     * Determines the working state for the given repository object.
     * 
     * @param object
     *            the repository object to determine the working state for, cannot be <code>null</code>.
     * @return the working state for the given repository object, never <code>null</code>.
     */
    protected WorkingState getWorkingState(RepositoryObject object) {
        return getRepositoryAdmin().getWorkingState(object);
    }

    /**
     * Helper method to return the working state icon for the given repository object.
     * 
     * @param object
     *            the repository object to get the icon for, cannot be <code>null</code>.
     * @return an icon representing the working state of the given repository object, never <code>null</code>.
     */
    protected Resource getWorkingStateIcon(RepositoryObject object) {
        String name = getWorkingState(object).name();
        return createIconResource("resource_workingstate_" + name);
    }

    /**
     * @param topic
     *            the topic of the event;
     * @param entity
     *            the entity of the event;
     * @param event
     *            the original event.
     * 
     * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
     */
    protected abstract void handleEvent(String topic, RepositoryObject entity, org.osgi.service.event.Event event);

    /**
     * Called whenever the user double clicks on a row.
     * 
     * @param itemId
     *            the row/item ID of the double clicked item.
     */
    protected void handleItemDoubleClick(Object itemId) {
        RepositoryObject object = getFromId(itemId);

        NamedObject namedObject = NamedObjectFactory.getNamedObject(object);
        if (namedObject != null) {
            showEditWindow(namedObject);
        }
    }

    /**
     * Called by the remove-link button to remove a link.
     * 
     * @param itemID
     *            the ID of the item to remove from the repository, cannot be <code>null</code>.
     */
    protected void handleItemRemoveLink(Object itemID) {
        Set<?> selection = m_associations.getActiveSelection();
        if (selection != null) {
            if (m_associations.isActiveTable(m_leftTable)) {
                for (Object itemId : selection) {
                    removeLeftSideAssociation(m_leftTable.getFromId(itemId), getFromId(itemID));
                }
            }
            else if (m_associations.isActiveTable(m_rightTable)) {
                for (Object itemId : selection) {
                    removeRightSideAssocation(getFromId(itemID), m_rightTable.getFromId(itemId));
                }
            }
        }
    }

    /**
     * Called by the remove-item button to remove a repository object from the repository.
     * 
     * @param itemID
     *            the ID of the item to remove from the repository, cannot be <code>null</code>.
     */
    protected void handleItemRemoveObject(Object itemID) {
        getRepository().remove(getFromId(itemID));
    }

    /**
     * Returns whether the given {@link RepositoryObject} can be handled by this panel.
     * 
     * @param entity
     *            the entity to test, cannot be <code>null</code>.
     * @return <code>true</code> if the entity is supported by this panel, <code>false</code> if not.
     */
    protected abstract boolean isSupportedEntity(RepositoryObject entity);

    /**
     * Populates the given table item with information from the given repository object.
     * 
     * @param object
     *            the repository object to take the information from, cannot be <code>null</code>;
     * @param item
     *            the table item to populate, cannot be <code>null</code>.
     */
    protected abstract void populateItem(REPO_OBJ object, Item item);

    /**
     * Populates the given table item with information about the parent for a given repository object.
     * 
     * @param object
     *            the repository object to take the information from, cannot be <code>null</code>;
     * @param parentId
     *            the ID of the parent, cannot be <code>null</code>;
     * @param item
     *            the table item to populate, cannot be <code>null</code>.
     */
    protected void populateParentItem(REPO_OBJ object, String parentId, Item item) {
        item.getItemProperty(OBJECT_NAME).setValue(getParentDisplayName(object));
        item.getItemProperty(OBJECT_DESCRIPTION).setValue("");
        // XXX add unlink button when we can correctly determine dynamic links...
        // item.getItemProperty(ACTION_UNLINK).setValue(new RemoveLinkButton(object));
        // we *must* set a non-null icon for the parent as well to ensure that the tree-table open/collapse icon is
        // rendered properly...
        setItemIcon(parentId, createIconResource("resource_workingstate_unchanged"));
    }

    protected final void refreshAllRowCaches(Direction direction) {
        if (direction.isGoLeft()) {
            BaseObjectPanel<?, ?, ?, ?> ptr = this;
            while (ptr != null) {
                ptr.refreshRowCache();
                ptr = ptr.m_leftTable;
            }
        }
        if (direction.isGoRight()) {
            BaseObjectPanel<?, ?, ?, ?> ptr = this;
            while (ptr != null) {
                ptr.refreshRowCache();
                ptr = ptr.m_rightTable;
            }
        }
    }

    /**
     * Removes a given repository object from this table.
     * 
     * @param object
     *            the repository object to remove, cannot be <code>null</code>.
     */
    protected final void removeFromTable(RepositoryObject object) {
        String itemID = object.getDefinition();
        Object parentID = getParent(itemID);

        if (removeItem(itemID)) {
            if ((parentID != null) && !hasChildren(parentID)) {
                removeItem(parentID);
            }
        }

        // Request the focus again...
        focus();
    }

    protected final void setItemIcon(REPO_OBJ object) {
        if (object != null) {
            Resource icon = getWorkingStateIcon(object);
            setItemIcon(object.getDefinition(), icon);
        }
    }

    /**
     * Shows an edit window for the given named object.
     * 
     * @param object
     *            the named object to edit;
     * @param main
     *            the main window to use.
     */
    protected final void showEditWindow(NamedObject object) {
        List<UIExtensionFactory> extensions = getExtensionFactories();
        createEditor(object, extensions).show(getParent().getWindow());
    }

    /**
     * Updates a given repository object in this table.
     * 
     * @param object
     *            the repository object to update, cannot be <code>null</code>.
     */
    protected final void update(REPO_OBJ object) {
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

    protected final void updateItemIcon(Object itemId) {
        setItemIcon(getFromId(itemId));
    }

    private void collectRelations(Direction direction, List<REPO_OBJ> selection, Set<Class<?>> seenTypes, Collection<String> associated, Collection<String> related) {
        // We've already visited this entity...
        seenTypes.add(m_entityType);

        if (direction.isGoLeft() && m_leftTable != null && !seenTypes.contains(m_leftTable.m_entityType)) {
            seenTypes.add(m_leftTable.m_entityType);

            for (REPO_OBJ obj : selection) {
                List<LEFT_ASSOC_REPO_OBJ> left = obj.getAssociations(m_leftTable.m_entityType);
                extractDefinitions(associated, left);
                // the associated items of our left-side table are the ones related to us...
                m_leftTable.collectRelations(direction, left, seenTypes, related, related);
            }
        }
        if (direction.isGoRight() && m_rightTable != null && !seenTypes.contains(m_rightTable.m_entityType)) {
            seenTypes.add(m_rightTable.m_entityType);

            for (REPO_OBJ obj : selection) {
                List<RIGHT_ASSOC_REPO_OBJ> right = obj.getAssociations(m_rightTable.m_entityType);
                extractDefinitions(associated, right);
                // the associated items of our right-side table are the ones related to us...
                m_rightTable.collectRelations(direction, right, seenTypes, related, related);
            }
        }
    }

    private void extractDefinitions(Collection<String> defs, List<? extends RepositoryObject> objects) {
        if (defs == null) {
            return;
        }
        for (RepositoryObject obj : objects) {
            defs.add(obj.getDefinition());
        }
    }

    /**
     * @return a list of current extension factories, properly ordered, never <code>null</code>.
     */
    private List<UIExtensionFactory> getExtensionFactories() {
        List<UIExtensionFactory> extensions;
        synchronized (m_extensionFactories) {
            // Sort the list of extension factories...
            Collections.sort(m_extensionFactories);

            // Walk through the holders and fetch the extension factories one by one...
            extensions = new ArrayList<>(m_extensionFactories.size());
            for (UIExtensionFactoryHolder holder : m_extensionFactories) {
                UIExtensionFactory extensionFactory = holder.getUIExtensionFactory();
                // Make sure only to use non-GCd factories...
                if (extensionFactory != null) {
                    extensions.add(extensionFactory);
                }
            }
        }
        return extensions;
    }
}
