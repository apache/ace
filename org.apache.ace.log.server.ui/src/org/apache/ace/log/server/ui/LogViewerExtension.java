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
package org.apache.ace.log.server.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.webui.UIExtensionFactory;
import org.osgi.service.log.LogService;

import com.vaadin.data.Container.Filterable;
import com.vaadin.data.Property;
import com.vaadin.data.util.filter.SimpleStringFilter;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

//import com.vaadin.data.util.

/**
 * Provides a simple AuditLog viewer for targets.
 */
public class LogViewerExtension implements UIExtensionFactory {

    private static final String CAPTION = "LogViewer";

    private static final String COL_TIME = "Time";
    private static final String COL_TYPE = "Type";
    private static final String COL_PROPERTIES = "Properties";

    private static final String FILL_AREA = "100%";
    private Table m_table;

    private volatile LogStore m_store;
    private volatile LogService m_logService;

    /**
     * contains a mapping of event type to a string representation of that type.
     */
    private final Map<Integer, String> m_eventTypeMapping = new HashMap<>();

    /**
     * {@inheritDoc}
     */
    public Component create(Map<String, Object> context) {
        StatefulTargetObject target = getRepositoryObjectFromContext(context);
        if (!target.isRegistered()) {
            VerticalLayout result = new VerticalLayout();
            result.setCaption(CAPTION);
            result.addComponent(new Label("This target is not yet registered, so it has no log."));
            return result;
        }

        m_table = new Table() {
            @Override
            protected String formatPropertyValue(Object rowId, Object colId, Property property) {
                DateFormat formatter = SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, getApplication().getLocale());
                if (COL_TIME.equals(colId)) {
                    return formatter.format(property.getValue());
                }
                return super.formatPropertyValue(rowId, colId, property);
            }
        };
        m_table.setWidth(FILL_AREA);
        m_table.setHeight(FILL_AREA);
        m_table.addContainerProperty(COL_TIME, Date.class, null, "Time", null, null);
        m_table.addContainerProperty(COL_TYPE, String.class, null, "Type", null, null);
        m_table.addContainerProperty(COL_PROPERTIES, TextArea.class, null, "Properties", null, null);

        m_table.setColumnExpandRatio(COL_PROPERTIES, 2);
        m_table.setColumnExpandRatio(COL_TYPE, 1);
        m_table.setColumnExpandRatio(COL_TIME, 1);
        m_table.setColumnCollapsingAllowed(true);

        try {
            fillTable(target, m_table);
            // Sort on time in descending order...
            m_table.setSortAscending(false);
            m_table.setSortContainerPropertyId(COL_TIME);
        }
        catch (IOException ex) {
            m_logService.log(LogService.LOG_WARNING, "Log viewer failed!", ex);
        }

        TextField tf = makeTextField(COL_TYPE);
        TextField pf = makeTextField(COL_PROPERTIES);

        HorizontalLayout filters = new HorizontalLayout();
        filters.setSpacing(true);
        filters.addComponent(tf);
        filters.addComponent(pf);

        // main holds the two components:
        VerticalLayout main = new VerticalLayout();
        main.setCaption(CAPTION);
        main.setSpacing(true);

        main.addComponent(filters);
        main.addComponent(m_table);
        return main;
    }

    /**
     * Returns a string representation of the given event's type.
     *
     * @param event
     *            the event to get the type for, cannot be <code>null</code>.
     * @return a string representation of the event's type, never <code>null</code>.
     */
    final String getEventType(Event event) {
        if (m_eventTypeMapping.isEmpty()) {
            // Lazily create a mapping of value -> name of all event-types...
            for (Field f : AuditEvent.class.getFields()) {
                if (((f.getModifiers() & Modifier.STATIC) != 0) && (f.getType() == Integer.TYPE)) {
                    try {
                        Integer value = (Integer) f.get(null);
                        m_eventTypeMapping.put(value, normalize(f.getName()));
                    }
                    catch (IllegalAccessException e) {
                        // Should not happen, as all fields are public on an
                        // interface; otherwise we simply ignore this field...
                        m_logService.log(LogService.LOG_DEBUG, "Failed to access public field of interface?!", e);
                    }
                }
            }
        }

        String type = m_eventTypeMapping.get(event.getType());
        if (type == null) {
            type = Integer.toString(event.getType());
        }

        return type;
    }

    /**
     * Creates a {@link TextArea} with a dump of the given event's properties.
     *
     * @param event
     *            the event to create a textarea for, cannot be <code>null</code>.
     * @return a {@link TextArea} instance, never <code>null</code>.
     */
    final TextArea getProperties(Event event) {
        Map<String, String> props = event.getProperties();

        TextArea area = new TextArea("", dumpProperties(props));
        area.setWidth(FILL_AREA);
        area.setRows(props.size());
        area.setWordwrap(false);
        area.setReadOnly(true);
        area.setImmediate(true);
        return area;
    }

    final Date getTime(Event event) {
        return new Date(event.getTime());
    }

    /**
     * Dumps the given dictionary to a string by placing all key,value-pairs on a separate line.
     *
     * @param props
     *            the dictionary to dump, may be <code>null</code>.
     * @return a string dump of all properties in the given dictionary, never <code>null</code>.
     */
    private String dumpProperties(Map<String, String> props) {
        StringBuilder sb = new StringBuilder();
        if (props != null) {
            for (String key : props.keySet()) {
                String value = props.get(key);

                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(key).append(": ").append(value);
            }
        }
        return sb.toString();
    }

    /**
     * Fills the table with all log entries for the given repository object.
     *
     * @param object
     *            the repository object to get the log for, cannot be <code>null</code>;
     * @param table
     *            the table to fill, cannot be <code>null</code>.
     * @throws IOException
     *             in case of I/O problems accessing the log store.
     */
    private void fillTable(RepositoryObject object, Table table) throws IOException {
        String id = object.getAttribute(TargetObject.KEY_ID);
        List<Descriptor> desc = m_store.getDescriptors(id);
        if (desc != null) {
            for (Descriptor log : desc) {
                for (Event event : m_store.get(log)) {
                    table.addItem(new Object[] { getTime(event), getEventType(event), getProperties(event) }, null);
                }
            }
        }
    }

    private StatefulTargetObject getRepositoryObjectFromContext(Map<String, Object> context) {
        return (StatefulTargetObject) context.get("object");
    }

    private TextField makeTextField(final String colType) {
        TextField t = new TextField(colType);

        t.addListener(new TextChangeListener() {
            SimpleStringFilter filter = null;

            public void textChange(TextChangeEvent event) {
                Filterable f = (Filterable) m_table.getContainerDataSource();

                // Remove old filter
                if (filter != null) {
                    f.removeContainerFilter(filter);
                }
                // Set new filter for the "Name" column
                filter = new SimpleStringFilter(colType, event.getText(), true /* ignoreCase */, false /* onlyMatchPrefix */);

                f.addContainerFilter(filter);
            }
        });

        return t;
    }

    private String normalize(String input) {
        return input.toLowerCase().replaceAll("_", " ");
    }
}
