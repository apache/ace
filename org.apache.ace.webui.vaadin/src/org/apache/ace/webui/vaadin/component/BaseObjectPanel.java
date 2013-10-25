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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.domain.NamedObjectFactory;
import org.apache.ace.webui.vaadin.AssociationRemover;
import org.apache.ace.webui.vaadin.EditWindow;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventHandler;

import com.vaadin.data.Item;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.terminal.Resource;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Button;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.Table;
import com.vaadin.ui.TreeTable;
import com.vaadin.ui.Window.Notification;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a custom table for displaying artifacts, features and so on.
 */
abstract class BaseObjectPanel<REPO_OBJ extends RepositoryObject, REPO extends ObjectRepository<REPO_OBJ>> extends TreeTable implements EventHandler {

    /**
     * Provides a generic remove item button.
     */
    private class RemoveItemButton extends Button {
        public RemoveItemButton(final REPO repository, final REPO_OBJ object) {
            super("x");
            setStyleName(Reindeer.BUTTON_SMALL);
            setDescription("Delete " + getDisplayName(object));

            addListener(new Button.ClickListener() {
                public void buttonClick(ClickEvent event) {
                    try {
                        repository.remove(object);
                    }
                    catch (Exception e) {
                        // ACE-246: notify user when the removal failed!
                        getWindow().showNotification("Failed to remove item!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }
                }
            });
        }
    }

    /**
     * Provides a generic remove-link (or association) button.
     */
    private class RemoveLinkButton extends Button {
        public RemoveLinkButton(final REPO_OBJ object, final Table toLeft, final Table toRight) {
            super("-");
            setStyleName(Reindeer.BUTTON_SMALL);
            setData(object.getDefinition());
            setDescription("Unlink " + getDisplayName(object));
            // Only enable this button when actually selected...
            setEnabled(false);

            addListener(new Button.ClickListener() {
                public void buttonClick(ClickEvent event) {
                    Set<?> selection = m_associations.getActiveSelection();
                    if (selection != null) {
                        if (m_associations.isActiveTable(toLeft)) {
                            for (Object item : selection) {
                                RepositoryObject selected = m_associations.lookupInActiveSelection(item);
                                removeLeftSideAssociation(object, selected);
                            }
                        }
                        else if (m_associations.isActiveTable(toRight)) {
                            for (Object item : selection) {
                                RepositoryObject selected = m_associations.lookupInActiveSelection(item);
                                removeRightSideAssocation(object, selected);
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Provides a small container for {@link UIExtensionFactory} instances.
     */
    private static class UIExtensionFactoryHolder implements Comparable<UIExtensionFactoryHolder> {
        private final ServiceReference m_serviceRef;
        private final WeakReference<UIExtensionFactory> m_extensionFactory;

        public UIExtensionFactoryHolder(ServiceReference serviceRef, UIExtensionFactory extensionFactory) {
            m_serviceRef = serviceRef;
            m_extensionFactory = new WeakReference<UIExtensionFactory>(extensionFactory);
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(UIExtensionFactoryHolder other) {
            ServiceReference thatServiceRef = other.m_serviceRef;
            ServiceReference thisServiceRef = m_serviceRef;
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
    /** Empirically determined (most common width appears to be 36px). */
    protected static final int FIXED_COLUMN_WIDTH = 36;

    private final AssociationHelper m_associations;
    protected final AssociationRemover m_associationRemover;

    private final List<UIExtensionFactoryHolder> m_extensionFactories;
    private final String m_extensionPoint;

    private Table m_leftTable;
    private Table m_rightTable;

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
    public BaseObjectPanel(final AssociationHelper associations, final AssociationRemover associationRemover,
        final String name, final String extensionPoint, final boolean hasEdit) {
        super(name + "s");

        m_associations = associations;
        m_associationRemover = associationRemover;
        m_extensionFactories = new ArrayList<UIExtensionFactoryHolder>();
        m_extensionPoint = extensionPoint;

        defineTableColumns();

        setSizeFull();
        setCellStyleGenerator(m_associations.createCellStyleGenerator(this));
        setSelectable(true);
        setMultiSelect(true);
        setImmediate(true);
        setDragMode(TableDragMode.MULTIROW);
        setColumnCollapsingAllowed(true);

        setItemIconPropertyId(ICON);
        setHierarchyColumn(ICON);

        if (hasEdit) {
            addListener(new ItemClickListener() {
                public void itemClick(ItemClickEvent event) {
                    if (event.isDoubleClick()) {
                        RepositoryObject object = getFromId((String) event.getItemId());

                        NamedObject namedObject = NamedObjectFactory.getNamedObject(object);
                        if (namedObject != null) {
                            showEditWindow(namedObject);
                        }
                    }
                }
            });
        }
    }

    /**
     * Called by the dependency manager in case a new {@link UIExtensionFactory} is registered.
     * 
     * @param ref
     *            the service reference of the new extension;
     * @param factory
     *            the extension instance itself.
     */
    public final void addExtension(ServiceReference ref, UIExtensionFactory factory) {
        synchronized (m_extensionFactories) {
            m_extensionFactories.add(new UIExtensionFactoryHolder(ref, factory));
        }
        populate();
    }

    /**
     * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
     */
    public final void handleEvent(org.osgi.service.event.Event event) {
        final RepositoryObject entity = (RepositoryObject) event.getProperty(RepositoryObject.EVENT_ENTITY);
        final String topic = event.getTopic();

        synchronized (getApplication()) {
            if (isSupportedEntity(entity)) {
                try {
                    handleEvent(topic, entity, event);
                }
                finally {
                    refreshRenderedCells();
                }
            }
            else if (RepositoryAdmin.TOPIC_REFRESH.equals(topic) || RepositoryAdmin.TOPIC_LOGIN.equals(topic)) {
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
            .setInstanceBound(true)
            .setService(UIExtensionFactory.class, "(" + UIExtensionFactory.EXTENSION_POINT_KEY + "=" + m_extensionPoint + ")")
            .setCallbacks("addExtension", "removeExtension"));
    }

    /**
     * Called to populate this table.
     */
    public void populate() {
        removeAllItems();
        for (REPO_OBJ object : getAllRepositoryObjects()) {
            add(object);
        }
    }

    /**
     * Called by the dependency manager in case a {@link UIExtensionFactory} is unregistered.
     * 
     * @param ref
     *            the service reference of the extension;
     * @param factory
     *            the extension instance itself.
     */
    public final void removeExtension(ServiceReference ref, UIExtensionFactory factory) {
        synchronized (m_extensionFactories) {
            m_extensionFactories.remove(new UIExtensionFactoryHolder(ref, factory));
        }
        populate();
    }

    /**
     * Sets the left-side table, that defines the left-hand side of the assocations of the entities.
     * 
     * @param leftTable
     *            the table to set, can be <code>null</code>.
     */
    public final void setLeftTable(Table leftTable) {
        m_leftTable = leftTable;
    }

    /**
     * Sets the right-side table, that defines the right-hand side of the assocations of the entities.
     * 
     * @param rightTable
     *            the table to set, can be <code>null</code>.
     */
    public final void setRightTable(Table rightTable) {
        m_rightTable = rightTable;
    }

    /**
     * Removes the left-hand side associations for a given repository object.
     * 
     * @param object
     *            the repository object to remove the left-hand side associations;
     * @param other
     *            the (left-hand side) repository object to remove the associations for.
     */
    final void removeLeftSideAssociation(REPO_OBJ object, RepositoryObject other) {
        if (doRemoveLeftSideAssociation(object, other)) {
            m_associations.removeAssociatedItem(object);
            refreshRowCache();
            if (m_leftTable != null) {
                m_leftTable.refreshRowCache();
            }
        }
    }

    /**
     * Removes the right-hand side associations for a given repository object.
     * 
     * @param object
     *            the repository object to remove the right-hand side associations;
     * @param other
     *            the (right-hand side) repository object to remove the associations for.
     */
    final void removeRightSideAssocation(REPO_OBJ object, RepositoryObject other) {
        if (doRemoveRightSideAssociation(object, other)) {
            m_associations.removeAssociatedItem(object);
            refreshRowCache();
            if (m_rightTable != null) {
                m_rightTable.refreshRowCache();
            }
        }
    }

    /**
     * Adds a given repository object to this table.
     * 
     * @param object
     *            the repository object to add, cannot be <code>null</code>.
     */
    protected void add(REPO_OBJ object) {
        Item item = addItem(object.getDefinition());
        if (item != null) {
            setChildrenAllowed(object.getDefinition(), false);

            populateItem(object, item);
            setItemIcon(object);
        }
    }

    protected void updateItemIcon(Object itemId) {
        REPO_OBJ obj = getFromId((String) itemId);
        setItemIcon(obj);
    }

    protected void setItemIcon(REPO_OBJ object) {
        if (object != null) {
            Resource icon = getWorkingStateIcon(object);
            setItemIcon(object.getDefinition(), icon);
        }
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
     * Factory method to create a remove-item button.
     * 
     * @param object
     *            the repository object to create the remove-item button for, cannot be <code>null</code>.
     * @return a button, can be <code>null</code> if removal of this repository object is not supported.
     */
    protected Button createRemoveItemButton(REPO_OBJ object) {
        return new RemoveItemButton(getRepository(), object);
    }

    /**
     * Factory method to create a remove-link button.
     * 
     * @param object
     *            the repository object to create the remove-link button for, cannot be <code>null</code>.
     * @return a button, can be <code>null</code> if remove-link is not supported.
     */
    protected Button createUnlinkButton(REPO_OBJ object) {
        return new RemoveLinkButton(object, m_leftTable, m_rightTable);
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

        setColumnWidth(ACTION_UNLINK, FIXED_COLUMN_WIDTH);
        setColumnWidth(ACTION_DELETE, FIXED_COLUMN_WIDTH);
        setColumnWidth(ICON, FIXED_COLUMN_WIDTH);

        setColumnCollapsible(ICON, false);
        setColumnCollapsible(ACTION_UNLINK, false);
        setColumnCollapsible(ACTION_DELETE, false);
    }

    /**
     * Does the actual removal of the left-hand side associations for a given repository object.
     * 
     * @param object
     *            the repository object to remove the left-hand side associations;
     * @param other
     *            the (left-hand side) repository object to remove the associations for.
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    protected boolean doRemoveLeftSideAssociation(REPO_OBJ object, RepositoryObject other) {
        return m_leftTable != null;
    }

    /**
     * Does the actual removal of the right-hand side associations for a given repository object.
     * 
     * @param object
     *            the repository object to remove the right-hand side associations;
     * @param other
     *            the (right-hand side) repository object to remove the associations for.
     * @return <code>true</code> if the associations were removed, <code>false</code> if not.
     */
    protected boolean doRemoveRightSideAssociation(REPO_OBJ object, RepositoryObject other) {
        return m_rightTable != null;
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
    protected final REPO_OBJ getFromId(String id) {
        return getRepository().get(id);
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
     * Removes a given repository object from this table.
     * 
     * @param object
     *            the repository object to remove, cannot be <code>null</code>.
     */
    protected void remove(REPO_OBJ object) {
        removeItem(object.getDefinition());
    }

    /**
     * Updates a given repository object in this table.
     * 
     * @param object
     *            the repository object to update, cannot be <code>null</code>.
     */
    protected void update(REPO_OBJ object) {
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

    /**
     * Returns all repository objects.
     * 
     * @return an {@link Iterable} with all repository objects, never <code>null</code>.
     */
    private Iterable<REPO_OBJ> getAllRepositoryObjects() {
        return getRepository().get();
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
            extensions = new ArrayList<UIExtensionFactory>(m_extensionFactories.size());
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

    /**
     * Shows an edit window for the given named object.
     * 
     * @param object
     *            the named object to edit;
     * @param main
     *            the main window to use.
     */
    private void showEditWindow(NamedObject object) {
        List<UIExtensionFactory> extensions = getExtensionFactories();
        createEditor(object, extensions).show(getParent().getWindow());
    }
}
