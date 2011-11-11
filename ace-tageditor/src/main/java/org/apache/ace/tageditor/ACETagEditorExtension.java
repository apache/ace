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
package org.apache.ace.tageditor;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.event.Action;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

public class ACETagEditorExtension implements UIExtensionFactory {

    public Component create(Map<String, Object> context) {
        final RepositoryObject sgo = getRepositoryObjectFromContext(context);
        VerticalLayout result = new VerticalLayout();
        result.setCaption("Tag Editor");
        if (sgo instanceof StatefulGatewayObject) {
            StatefulGatewayObject statefulTarget = (StatefulGatewayObject) sgo;
            if (statefulTarget.isRegistered()) {
                final Table table = new Table();
                table.setWidth("100%");
                table.addContainerProperty("Tag", TextField.class, null);
                table.addContainerProperty("Value", TextField.class, null);
                table.setEditable(false);
                result.addComponent(table);
                result.setComponentAlignment(table, Alignment.MIDDLE_CENTER);
                final Map<Object, TagTableEntry> idToKey = new HashMap<Object, TagTableEntry>();
                Enumeration<String> keys = sgo.getTagKeys();
                while (keys.hasMoreElements()) {
                    String keyString = keys.nextElement();
                    String valueString = sgo.getTag(keyString);
                    if ((valueString != null) && (valueString.trim().length() != 0)) {
                        TagTableEntry tte = new TagTableEntry(sgo, keyString,
                                valueString);
                        idToKey.put(tte.addTo(table), tte);
                    }
                }
                final TagTableEntry tte = new TagTableEntry(sgo);
                idToKey.put(tte.addTo(table), tte);
                tte.setListener(new TagTableEntry.ChangeListener() {
                    private volatile TagTableEntry m_lastEntry = tte;
                    public void changed(TagTableEntry entry) {
                        TagTableEntry ntte = new TagTableEntry(sgo);
                        idToKey.put(ntte.addTo(table), ntte);
                        m_lastEntry.setListener(null);
                        m_lastEntry = ntte;
                        ntte.setListener(this);
                    }
                });
                table.addActionHandler(new Action.Handler() {
                    final Action[] delete = new Action[] { new Action("delete") };
                    public void handleAction(Action action, Object sender, Object target) {
                        idToKey.remove(target).removeFrom(table);
                    }
                    public Action[] getActions(Object target, Object sender) {
                        return delete;
                    }
                });
            }
            else {
                result.addComponent(new Label("This target is not yet registered, so you cannot add tags."));
            }
        }
        return result;
    }

    private RepositoryObject getRepositoryObjectFromContext(
            @SuppressWarnings("rawtypes") Map context) {
        Object contextObject = context.get("object");
        if (contextObject == null) {
            throw new IllegalStateException("No context object found");
        }
        // It looks like there is some bug (or some other reason that escapes
        // me)
        // why ace is using either the object directly or wraps it in a
        // NamedObject first.
        // Its unclear when it does which so for now we cater for both.
        return ((RepositoryObject) (contextObject instanceof NamedObject ? ((NamedObject) contextObject)
                .getObject() : contextObject));
    }
}
