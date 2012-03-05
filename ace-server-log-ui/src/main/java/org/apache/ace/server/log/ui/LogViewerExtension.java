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
package org.apache.ace.server.log.ui;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.osgi.service.log.LogService;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.VerticalLayout;

/**
 * Provides a simple AuditLog viewer for targets.
 */
public class LogViewerExtension implements UIExtensionFactory {
    
    private static final String CAPTION = "LogViewer";

    private static final String COL_TIME = "Time";
    private static final String COL_TYPE = "Type";
    private static final String COL_PROPERTIES = "Properties";
    
    private static final String FILL_AREA = "100%";
    
    private volatile LogStore m_store;
    private volatile LogService m_logService;

    /** contains a mapping of event type to a string representation of that type. */
    private final Map<Integer, String> m_eventTypeMapping = new HashMap<Integer, String>();

    /**
     * {@inheritDoc}
     */
    public Component create(Map<String, Object> context) {
        RepositoryObject object = getRepositoryObjectFromContext(context);
        if (object instanceof StatefulGatewayObject && !((StatefulGatewayObject) object).isRegistered()) {
            VerticalLayout result = new VerticalLayout();
            result.setCaption(CAPTION);
            result.addComponent(new Label("This target is not yet registered, so it has no log."));
            return result;
        }

        Table table = new Table();
        table.setWidth(FILL_AREA);
        table.setHeight(FILL_AREA);
        table.setCaption(CAPTION);
        table.addContainerProperty(COL_TIME, Date.class, null);
        table.addContainerProperty(COL_TYPE, String.class, null);
        table.addContainerProperty(COL_PROPERTIES, TextArea.class, null);
        table.setColumnExpandRatio(COL_PROPERTIES, 1);
        try {
            fillTable(object, table);
        }
        catch (IOException ex) {
            m_logService.log(LogService.LOG_WARNING, "Log viewer failed!", ex);
        }
        return table;
    }

    /**
     * Fills the table with all log entries for the given repository object.
     * 
     * @param object the repository object to get the log for, cannot be <code>null</code>;
     * @param table the table to fill, cannot be <code>null</code>.
     * @throws IOException in case of I/O problems accessing the log store.
     */
    private void fillTable(RepositoryObject object, Table table) throws IOException {
        String id = object.getAttribute(GatewayObject.KEY_ID);
        List<LogDescriptor> desc = m_store.getDescriptors(id);
        if (desc != null) {
            for (LogDescriptor log : desc) {
                for (LogEvent event : m_store.get(log)) {
                    table.addItem(
                        new Object[] { new Date(event.getTime()), getEventType(event), getProperties(event) }, null);
                }
            }
        }
    }

    /**
     * Creates a {@link TextArea} with a dump of the given event's properties.
     * 
     * @param event the event to create a textarea for, cannot be <code>null</code>.
     * @return a {@link TextArea} instance, never <code>null</code>.
     */
    private TextArea getProperties(LogEvent event) {
        Dictionary props = event.getProperties();

        TextArea area = new TextArea("", dumpProperties(props));
        area.setWidth(FILL_AREA);
        area.setRows(props.size());
        area.setWordwrap(false);
        area.setReadOnly(true);
        area.setImmediate(true);
        return area;
    }

    /**
     * Dumps the given dictionary to a string by placing all key,value-pairs on a separate line.
     * 
     * @param dict the dictionary to dump, may be <code>null</code>.
     * @return a string dump of all properties in the given dictionary, never <code>null</code>.
     */
    private String dumpProperties(Dictionary dict) {
        StringBuilder sb = new StringBuilder();
        if (dict != null) {
            Enumeration keys = dict.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement().toString();
                String value = dict.get(key).toString();

                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(key).append(": ").append(value);
            }
        }
        return sb.toString();
    }

    /**
     * Returns a string representation of the given event's type.
     * 
     * @param event the event to get the type for, cannot be <code>null</code>.
     * @return a string representation of the event's type, never <code>null</code>.
     */
    private String getEventType(LogEvent event) {
        if (m_eventTypeMapping.isEmpty()) {
            // Lazily create a mapping of value -> name of all event-types...
            for (Field f : AuditEvent.class.getFields()) {
                if (((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && (f.getType() == Integer.TYPE)) {
                    try {
                        Integer value = (Integer) f.get(null);
                        m_eventTypeMapping.put(value, f.getName());
                    }
                    catch (IllegalAccessException e) {
                        // Should not happen, as all fields are public on an interface;
                        // otherwise we simply ignore this field...
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

    private RepositoryObject getRepositoryObjectFromContext(Map<String, Object> context) {
        Object contextObject = context.get("object");
        if (contextObject == null) {
            throw new IllegalStateException("No context object found");
        }
        // It looks like there is some bug (or some other reason that escapes
        // me) why ace is using either the object directly or wraps it in a
        // NamedObject first.
        // Its unclear when it does which so for now we cater for both.
        return (contextObject instanceof NamedObject ? ((NamedObject) contextObject).getObject()
            : (RepositoryObject) contextObject);
    }
}
