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

import org.apache.ace.client.repository.RepositoryObject;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;

public class TagTableEntry {

    public interface ChangeListener {
        public void changed(TagTableEntry entry);
    }

    private final TextField m_keyField = new TextField(null, "");
    private final TextField m_valueField = new TextField(null, "");
    private final RepositoryObject m_repoObject;

    private volatile String m_lastKey = null;
    private volatile Object m_id = null;
    private volatile ChangeListener m_listener = null;

    public TagTableEntry(RepositoryObject repoObject) {
        m_repoObject = repoObject;
        m_keyField.setImmediate(true);
        m_keyField.setInputPrompt("key");
        m_keyField.setWidth("100%");
        m_keyField.addListener(new ValueChangeListener() {
            public void valueChange(ValueChangeEvent event) {
                keyChanged();
            }
        });
        m_valueField.setImmediate(true);
        m_valueField.setWidth("100%");
        m_valueField.setInputPrompt("value");
        m_valueField.addListener(new ValueChangeListener() {
            public void valueChange(ValueChangeEvent event) {
                valueChanged();
            }
        });
    }

    public TagTableEntry(RepositoryObject repoObject, String key, String value) {
        this(repoObject);

        m_keyField.setValue(key);
        m_valueField.setValue(value);
        m_lastKey = key;
    }

    public Object addTo(final Table table) {
        Button deleteButton = new Button() {
            @Override
            public boolean isEnabled() {
                return super.isEnabled() && m_id != null;
            }
        };
        deleteButton.setCaption("x");
        deleteButton.setStyleName("small");
        deleteButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                removeFrom(table);
            }
        });

        m_id = table.addItem(new Object[] { m_keyField, m_valueField, deleteButton }, null);
        return m_id;
    }

    public void removeFrom(Table table) {
        Object id = m_id;
        if (id != null) {
            table.removeItem(id);
            if ((m_lastKey != null) && (m_lastKey.trim().length() > 0)) {
                m_repoObject.removeTag(m_lastKey);
            }
            ChangeListener listener = m_listener;
            if (listener != null) {
                listener.changed(this);
            }
        }
    }

    public void setListener(ChangeListener listener) {
        m_listener = listener;
    }

    private void keyChanged() {
        if (m_lastKey == null) {
            m_lastKey = (String) m_keyField.getValue();
        }
        else {
            try {
                m_repoObject.addTag(m_lastKey, null);
            }
            catch (IllegalArgumentException e) {
                m_keyField.setValue("");
                m_keyField.setInputPrompt("invalid key, try again");
                m_lastKey = null;
                m_keyField.focus();
                return;
            }
        }
        m_lastKey = (String) m_keyField.getValue();
        set(m_lastKey, (String) m_valueField.getValue());
    }

    private void valueChanged() {
        set(m_lastKey, (String) m_valueField.getValue());
    }

    private void set(String key, String value) {
        if ((key != null) && (key.trim().length() > 0)) {
            if ((value != null) && (value.trim().length() > 0)) {
                try {
                    m_repoObject.addTag(key, value); // TODO changing the tag that often is probably not a good idea (especially if nothing changed)
                }
                catch (IllegalArgumentException e) {
                    m_keyField.setValue("");
                    m_keyField.setInputPrompt("invalid key, try again");
                    m_keyField.focus();
                    return;
                }
                
                ChangeListener listener = m_listener;
                if (listener != null) {
                    listener.changed(this);
                }
            }
        }
    }
}
