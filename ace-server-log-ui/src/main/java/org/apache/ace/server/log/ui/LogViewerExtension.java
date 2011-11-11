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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.server.log.store.LogStore;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.VerticalLayout;

public class LogViewerExtension implements UIExtensionFactory {
    private volatile LogStore m_store;

    public Component create(Map<String, Object> context) {
        RepositoryObject object = getRepositoryObjectFromContext(context);
        Table table = new Table();
        table.setWidth("100%");
        table.setHeight("100%");
        table.setCaption("LogViewer");
        table.addContainerProperty("Time", Date.class, null);
        table.addContainerProperty("Type", String.class, null);
        table.addContainerProperty("Properties", TextArea.class, null);
        table.setColumnExpandRatio("Properties", 1);
        try {
            if (object instanceof StatefulGatewayObject) {
                StatefulGatewayObject statefulTarget = (StatefulGatewayObject) object;
                if (statefulTarget.isRegistered()) {
                    String id = object.getAttribute(GatewayObject.KEY_ID);
                    List<LogDescriptor> desc = m_store.getDescriptors(id);
                    if (desc != null) {
                        for (LogDescriptor log : desc) {
                            for (LogEvent event : m_store.get(log)) {
                                Dictionary props = event.getProperties();
                                Enumeration keys = props.keys();
                                Set<String> keySet = new TreeSet<String>();
                                while (keys.hasMoreElements()) {
                                    keySet.add(keys.nextElement().toString());
                                }
                                Iterator<String> keyIter = keySet.iterator();
                                String value = "";
                                String propString = "";
                                String prepend = "";
                                while (keyIter.hasNext()) {
                                    String key = keyIter.next();
                                    value = props.get(key).toString();
                                    propString += prepend + key + ": " + value;
                                    prepend = "\n";
                                }
                                TextArea area = new TextArea("", propString);
                                area.setWidth("100%");
                                area.setRows(props.size());
                                area.setWordwrap(false);
                                area.setReadOnly(true);
                                area.setImmediate(true);
                                String type = Integer.toString(event.getType());
                                for (Field f : AuditEvent.class.getFields()) {
                                    if (((f.getModifiers() & Modifier.STATIC) == Modifier.STATIC) && (f.getType() == Integer.TYPE)) {
                                        if (((Integer) f.get(null)).intValue() == event.getType()) {
                                            type = f.getName();
                                            break;
                                        }
                                    }
                                }
                                table.addItem(new Object[] { new Date(event.getTime()), type, area }, null);
                            }
                        }
                    }
                }
                else {
                    VerticalLayout result = new VerticalLayout();
                    result.setCaption("VerifyResolve");
                    result.addComponent(new Label("This target is not yet registered, so it has no log."));
                    return result;
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return table;
    }

    private RepositoryObject getRepositoryObjectFromContext(Map<String, Object> context) {
        Object contextObject = context.get("object");
        if (contextObject == null) {
            throw new IllegalStateException("No context object found");
        }
        // It looks like there is some bug (or some other reason that escapes
        // me)
        // why ace is using either the object directly or wraps it in a
        // NamedObject first.
        // Its unclear when it does which so for now we cater for both.
        return ((RepositoryObject) (contextObject instanceof NamedObject ? ((NamedObject) contextObject).getObject() : contextObject));
    }
}
