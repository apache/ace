package org.apache.ace.webui.vaadin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.domain.NamedArtifactObject;
import org.apache.ace.webui.domain.NamedDistributionObject;
import org.apache.ace.webui.domain.NamedFeatureObject;
import org.apache.ace.webui.domain.NamedTargetObject;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.CellStyleGenerator;

public class Associations {
    private List<RepositoryObject> m_associatedItems = new ArrayList<RepositoryObject>();
    private List<RepositoryObject> m_relatedItems = new ArrayList<RepositoryObject>();
    private Table m_activeTable;
    private Set<?> m_activeSelection;
    private SelectionListener m_activeSelectionListener;

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
        return m_activeSelectionListener.lookup(item);
    }

    public void addAssociatedItems(List items) {
        m_associatedItems.addAll(items);
    }

    public void addRelatedItems(List items) {
        m_relatedItems.addAll(items);
    }

    public CellStyleGenerator createCellStyleGenerator() {
        return new CellStyleGenerator() {
            public String getStyle(Object itemId, Object propertyId) {
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
                }
                return null;
            }

            public boolean equals(Object itemId, RepositoryObject object) {
                return (getNamedObject(object).getDefinition().equals(itemId));
            }
        };
    }

    public NamedObject getNamedObject(RepositoryObject object) {
        if (object instanceof ArtifactObject) {
            return new NamedArtifactObject((ArtifactObject) object);
        }
        else if (object instanceof GroupObject) {
            return new NamedFeatureObject((GroupObject) object);
        }
        else if (object instanceof LicenseObject) {
            return new NamedDistributionObject((LicenseObject) object);
        }
        else if (object instanceof StatefulGatewayObject) {
            return new NamedTargetObject((StatefulGatewayObject) object);
        }
        else if (object instanceof GatewayObject) {
            return new NamedTargetObject((GatewayObject) object);
        }
        return null;
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
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction', starting with a list of objects
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(List<FROM> from,
        Class<TO> toClass) {
        List<TO> result = new ArrayList<TO>();
        for (RepositoryObject o : from) {
            result.addAll(getRelated(o, toClass));
        }
        return result;
    }

    public class SelectionListener implements Table.ValueChangeListener {
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

                    for (Table t : m_tablesToRefresh) {
                        t.requestRepaint();
                    }
                }
            }
        }

        public RepositoryObject lookup(Object value) {
            RepositoryObject object = null;
            if (value instanceof String) {
                object = m_repository.get((String) value);
                if (object instanceof StatefulGatewayObject) {
                    StatefulGatewayObject sgo = (StatefulGatewayObject) object;
                    if (sgo.isRegistered()) {
                        object = sgo.getGatewayObject();
                    }
                    else {
                        object = null;
                    }
                }
            }
            return object;
        }

    }

    public SelectionListener createSelectionListener(final Table table,
        final ObjectRepository<? extends RepositoryObject> repository, final Class[] left, final Class[] right,
        final Table[] tablesToRefresh) {
        return new SelectionListener(table, repository, left, right, tablesToRefresh);
    }
}
