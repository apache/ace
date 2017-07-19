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
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.event.Action;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * Provides a generic tag-editor for artifacts, features, distributions and targets.
 */
public class ACETagEditorExtension implements UIExtensionFactory {

    /**
     * {@inheritDoc}
     */
    public Component create(Map<String, Object> context) {
        final RepositoryObject sgo = getRepositoryObjectFromContext(context);

        Component editor;
        if (sgo instanceof StatefulTargetObject) {
            StatefulTargetObject statefulTarget = (StatefulTargetObject) sgo;
            if (statefulTarget.isRegistered()) {
                editor = createTagEditor(sgo);
            }
            else {
                editor = new Label("This target is not yet registered, so you cannot add tags.");
            }
        }
        else {
            editor = createTagEditor(sgo);
        }

        VerticalLayout result = new VerticalLayout();
        result.setCaption("Tag Editor");

        result.addComponent(editor);

        result.setComponentAlignment(editor, Alignment.MIDDLE_CENTER);

        return result;
    }

    /**
     * Creates a tag editor component for the given repository object.
     *
     * @param object
     *            the repository object to create the tag editor for, cannot be <code>null</code>.
     * @return a tag editor component, never <code>null</code>.
     */
    private Component createTagEditor(final RepositoryObject object) {
        final Table table = new Table();
        table.setWidth("100%");

        table.addContainerProperty("Tag", TextField.class, null);
        table.addContainerProperty("Value", TextField.class, null);
        table.addContainerProperty("Remove", Button.class, null, "", null, Table.ALIGN_CENTER);
        table.setEditable(false);

        table.setColumnExpandRatio("Tag", 1.0f);
        table.setColumnExpandRatio("Value", 1.0f);
        table.setColumnExpandRatio("Remove", 0.2f);

        final Map<Object, TagTableEntry> idToKey = new HashMap<>();
        Enumeration<String> keys = object.getTagKeys();
        while (keys.hasMoreElements()) {
            String keyString = keys.nextElement();
            String valueString = object.getTag(keyString);
            if ((valueString != null) && (valueString.trim().length() != 0)) {
                TagTableEntry tte = new TagTableEntry(object, keyString, valueString);
                idToKey.put(tte.addTo(table), tte);
            }
        }

        final TagTableEntry tte = new TagTableEntry(object);
        idToKey.put(tte.addTo(table), tte);

        tte.setListener(new TagTableEntry.ChangeListener() {
            private volatile TagTableEntry m_lastEntry = tte;

            public void changed(TagTableEntry entry) {
                TagTableEntry ntte = new TagTableEntry(object);
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

        return table;
    }

    private RepositoryObject getRepositoryObjectFromContext(Map<String, Object> context) {
        return (RepositoryObject) context.get("object");
    }
}
