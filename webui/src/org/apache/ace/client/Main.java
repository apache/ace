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

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;

/**
 * Entry point for the web ui.
 */
public class Main implements EntryPoint {
    private static final int REFRESH_INTERVAL = 2000;
    private StatusLabel m_statusLabel = new StatusLabel();
    private TargetTable m_targetTable = new TargetTable(m_statusLabel);
    
    /**
     * Interface for the columns, that they can use to indicate their status of
     * communication with the server.
     */
    interface StatusHandler {
        void handleFail(Class<?> table);
        void handleSuccess(Class<?> table);
    }
    
    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        // Create a scrollpanel with our only table in it
        ScrollPanel scrollPanel = new ScrollPanel(m_targetTable);
        scrollPanel.setHeight("30em");
        scrollPanel.setStyleName("objectTable");
        RootPanel.get("targetColumnContainer").add(scrollPanel);
        
        // Set a time to regularly update the UI
        Timer refreshTimer = new Timer() {
            @Override
            public void run() {
                updateUI();
            }
        };
        refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
        
        // Put our status label in the lower left corner
        RootPanel.get("serverStatusLabel").add(m_statusLabel);
    }
    
    /**
     * Triggers an update of UI.
     */
    private void updateUI() {
        m_targetTable.updateTable();
    }
    
    /**
     * Label that can be used a s {@link StatusHandler} for the tables. Will report
     * a successful connection when all components are happy.
     */
    private static class StatusLabel extends Label implements StatusHandler {
        private final Map<Class<?>, Boolean> m_statuses = new HashMap<Class<?>, Boolean>();
        
        public StatusLabel() {
            setText("checking server status...");
        }

        public void handleFail(Class<?> table) {
            m_statuses.put(table, false);
            updateStatus();
        }

        public void handleSuccess(Class<?> table) {
            m_statuses.put(table, true);
            updateStatus();
        }
        
        private void updateStatus() {
            boolean allOk = true;
            
            for (boolean b : m_statuses.values()) {
                allOk &= b;
            }
            
            if (allOk) {
                setText("Server connection all happy.");
                setStyleName("serverStatusGood");
            }
            else {
                setText("Error communicating with server.");
                setStyleName("serverStatusBad");
            }
        }
    }
    
    
}
