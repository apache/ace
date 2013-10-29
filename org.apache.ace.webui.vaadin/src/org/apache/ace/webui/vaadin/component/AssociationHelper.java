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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;

import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.ui.Button;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.CellStyleGenerator;

public class AssociationHelper {
    private List<RepositoryObject> m_associatedItems = new ArrayList<RepositoryObject>();
    private List<RepositoryObject> m_relatedItems = new ArrayList<RepositoryObject>();
    private Table m_activeTable;
    private Set<?> m_activeSelection;
    private SelectionListener m_activeSelectionListener;

    public void addAssociatedItem(RepositoryObject item) {
        m_associatedItems.add(item);
    }
    
    public void removeAssociatedItem(RepositoryObject item) {
        m_associatedItems.remove(item);
    }

    public void clear() {
        m_associatedItems.clear();
        m_relatedItems.clear();
    }

    public boolean isActiveTable(Table table) {
        return (m_activeTable != null) ? m_activeTable.equals(table) : false;
    }

    public Set<?> getActiveSelection() {
        return m_activeSelection;
    }

    public RepositoryObject lookupInActiveSelection(Object item) {
        if (m_activeSelectionListener == null) {
            return null;
        }
        return m_activeSelectionListener.lookup(item);
    }

    public CellStyleGenerator createCellStyleGenerator(final BaseObjectPanel parent) {
        return new CellStyleGenerator() {
            public String getStyle(Object itemId, Object propertyId) {
                Item item = parent.getItem(itemId);

                if (propertyId == null) {
                    // no propertyId, styling row
                    for (RepositoryObject o : m_associatedItems) {
                        if (equals(itemId, o)) {
                            return "associated";
                        }
                    }
                    for (RepositoryObject o : m_relatedItems) {
                        if (equals(itemId, o)) {
                            return "related";
                        }
                    }

                    parent.updateItemIcon(itemId);
                }
                else if (BaseObjectPanel.OBJECT_DESCRIPTION.equals(propertyId)) {
                    return "description";
                }
                else if (BaseObjectPanel.ACTION_UNLINK.equals(propertyId)) {
                    Button unlinkButton = (Button) item.getItemProperty(propertyId).getValue();

                    boolean enabled = false;
                    for (RepositoryObject o : m_associatedItems) {
                        if (equals(itemId, o)) {
                            enabled = true;
                        }
                    }

                    if (unlinkButton != null) {
                        unlinkButton.setEnabled(enabled);
                    }
                }
                return null;
            }

            private boolean equals(Object itemId, RepositoryObject object) {
                if (object == null) {
                    return false;
                }
                else {
                    String definition = object.getDefinition();
                    return definition == null ? false : definition.equals(itemId);
                }
            }
        };
    }

    public SelectionListener createSelectionListener(Table table, ObjectRepository<? extends RepositoryObject> repository, Class[] left, Class[] right, Table[] tablesToRefresh) {
        return new SelectionListener(table, repository, left, right, tablesToRefresh);
    }

    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction'
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(FROM from,
        Class<TO> toClass) {
        // if the SGO is not backed by a GO yet, this will cause an exception
        return from.getAssociations(toClass);
    }

    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction', starting with a list of
     * objects
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(List<FROM> from,
        Class<TO> toClass) {
        List<TO> result = new ArrayList<TO>();
        for (RepositoryObject o : from) {
            result.addAll(getRelated(o, toClass));
        }
        return result;
    }

    private class SelectionListener implements Table.ValueChangeListener {
        private final Table m_table;
        private final Table[] m_tablesToRefresh;
        private final ObjectRepository<? extends RepositoryObject> m_repository;
        private final Class[] m_left;
        private final Class[] m_right;

        public SelectionListener(final Table table, final ObjectRepository<? extends RepositoryObject> repository,
            final Class[] left, final Class[] right, final Table[] tablesToRefresh) {
            m_table = table;
            m_repository = repository;
            m_left = left;
            m_right = right;
            m_tablesToRefresh = tablesToRefresh;
        }

        @SuppressWarnings("unchecked")
        public void valueChange(ValueChangeEvent event) {
            if (m_activeSelection != null && m_activeTable != null) {
                if (!m_activeTable.equals(m_table)) {
                    for (Object val : m_activeSelection) {
                        m_activeTable.unselect(val);
                    }
                    m_table.requestRepaint();
                }
            }

            m_activeSelectionListener = SelectionListener.this;

            // set the active table
            m_activeTable = m_table;

            // in multiselect mode, a Set of itemIds is returned,
            // in singleselect mode the itemId is returned directly
            Set<?> value = (Set<?>) event.getProperty().getValue();

            // remember the active selection too
            m_activeSelection = value;

            if (value != null) {
                clear();

                for (Object val : value) {
                    RepositoryObject lo = lookup(val);
                    if (lo != null) {
                        List related = null;
                        for (int i = 0; i < m_left.length; i++) {
                            if (i == 0) {
                                related = getRelated(lo, m_left[i]);
                                m_associatedItems.addAll(related);
                            }
                            else {
                                related = getRelated(related, m_left[i]);
                                m_relatedItems.addAll(related);
                            }
                        }
                        for (int i = 0; i < m_right.length; i++) {
                            if (i == 0) {
                                related = getRelated(lo, m_right[i]);
                                m_associatedItems.addAll(related);
                            }
                            else {
                                related = getRelated(related, m_right[i]);
                                m_relatedItems.addAll(related);
                            }
                        }
                    }

                    m_table.refreshRowCache();
                    for (Table t : m_tablesToRefresh) {
                        t.refreshRowCache();
                    }
                }
            }
        }

        public RepositoryObject lookup(Object value) {
            RepositoryObject object = null;
            if (value instanceof String) {
                object = m_repository.get((String) value);
                if (object instanceof StatefulTargetObject) {
                    StatefulTargetObject sgo = (StatefulTargetObject) object;
                    if (sgo.isRegistered()) {
                        object = sgo.getTargetObject();
                    }
                    else {
                        object = null;
                    }
                }
            }
            return object;
        }

    }
}
