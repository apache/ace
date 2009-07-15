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
package org.apache.ace.client;

import java.util.HashMap;
import java.util.Map;

import org.apache.ace.client.Main.StatusHandler;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.ToggleButton;

/**
 * Basic table for using a valueobject per row.
 */
public abstract class ObjectTable<T> extends FlexTable {
    private final StatusHandler m_handler;
    
    private final Map<T, ToggleButton> m_widgets = new HashMap<T, ToggleButton>();
    
    /**
     * This callback is used for all 'get*' calls.
     */
    private AsyncCallback<T[]> m_asyncCallback = new AsyncCallback<T[]>() {
        public void onFailure(Throwable caught) {
            m_handler.handleFail(getTableID());
        }
        public void onSuccess(T[] result) {
            m_handler.handleSuccess(getTableID());
            int row = 0;
            // Create a button for every element, and reuse buttons for the ones we already know.
            for (T t : result) {
                ToggleButton button = m_widgets.get(t);
                if (button == null) {
                    button = new ToggleButton();
                    button.addClickHandler(m_buttonGroup);
                    m_widgets.put(t, button);
                }
                button.setText(getText(t));
                if (getRowCount() <= row || !getWidget(row, 0).equals(button)) {
                    // Setting the widget again might screw up focus
                    setWidget(row, 0, button);
                }
                row++;
            }
            while (row < getRowCount()) {
                // Looks like we removed something...
                removeRow(row);
            }
        }
    };
    
    /**
     * Pops up all other buttons in the same group when one gets clicked;
     * this way, we end up with a single selected button.
     */
    private final ClickHandler m_buttonGroup = new ClickHandler() {
        public void onClick(ClickEvent event) {
            for (ToggleButton w : m_widgets.values()) {
                if (!w.equals(event.getSource())) {
                    w.setDown(false);
                }
            }
        }
    };

    public ObjectTable(StatusHandler handler) {
        m_handler = handler;
    }
    
    /**
     * Finds the currently selected object, or <code>null</code> if none is found.
     */
    T getSelectedObject() {
        for (Map.Entry<T, ToggleButton> entry : m_widgets.entrySet()) {
            if (entry.getValue() instanceof ToggleButton && ((ToggleButton) entry.getValue()).isDown()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Updates the contents of this table
     */
    void updateTable() {
        callService(m_asyncCallback);
    }
    
    /**
     * Interprets the given value object for some column.
     */
    protected abstract String getText(T object);
    
    /**
     * Gets a unique ID for this table.
     * @return
     */
    protected abstract String getTableID();
    
    /**
     * Invokes the necessary service call to get the latest
     * set of value objects from the server, passing the given callback.
     */
    protected abstract void callService(AsyncCallback<T[]> callback);
}
