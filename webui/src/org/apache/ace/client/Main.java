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
import com.google.gwt.core.client.impl.StringBuilderImpl;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;

/**
 * Entry point for the web ui.
 */
public class Main implements EntryPoint {
    private static final int REFRESH_INTERVAL = 2000;
    private StatusLabel m_statusLabel = new StatusLabel();
    private BundleTable m_bundleTable = new BundleTable(m_statusLabel);
    private GroupTable m_groupTable = new GroupTable(m_statusLabel);
    private LicenseTable m_licenseTable = new LicenseTable(m_statusLabel);
    private TargetTable m_targetTable = new TargetTable(m_statusLabel);
    
    /**
     * Interface for the columns, that they can use to indicate their status of
     * communication with the server.
     */
    interface StatusHandler {
        void handleFail(String table);
        void handleSuccess(String table);
    }
    
    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        // Add the header panels
        Button addBundleButton = new Button("+");
        addBundleButton.addStyleDependentName("add");
        RootPanel.get("bundlesHeader").add(addBundleButton);
        addBundleButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                new AddBundleDialog().show();
            }
        });

        Button addGroupButton = new Button("+");
        addGroupButton.addStyleDependentName("add");
        RootPanel.get("groupsHeader").add(addGroupButton);
        addGroupButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_groupTable.addNew();
            }
        });
        
        Button addLicenseButton = new Button("+");
        addLicenseButton.addStyleDependentName("add");
        RootPanel.get("licensesHeader").add(addLicenseButton);
        addLicenseButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_licenseTable.addNew();
            }
        });
        
        // Create some scrollpanels with our tables
        ScrollPanel scrollPanel = new ScrollPanel(m_bundleTable);
        scrollPanel.setHeight("30em");
        scrollPanel.setStyleName("objectTable");
        RootPanel.get("bundleColumnContainer").add(scrollPanel);
        scrollPanel = new ScrollPanel(m_groupTable);
        scrollPanel.setHeight("30em");
        scrollPanel.setStyleName("objectTable");
        RootPanel.get("groupColumnContainer").add(scrollPanel);
        scrollPanel = new ScrollPanel(m_licenseTable);
        scrollPanel.setHeight("30em");
        scrollPanel.setStyleName("objectTable");
        RootPanel.get("licenseColumnContainer").add(scrollPanel);
        scrollPanel = new ScrollPanel(m_targetTable);
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
        
        // Add our checkout panel
        RootPanel.get("buttonPanel").add(new CheckoutPanel(this));
    }
    
    /**
     * Triggers an update of UI.
     */
    void updateUI() {
        m_bundleTable.updateTable();
        m_groupTable.updateTable();
        m_licenseTable.updateTable();
        m_targetTable.updateTable();
    }
    
    /**
     * Label that can be used a s {@link StatusHandler} for the tables. Will report
     * a successful connection when all components are happy.
     */
    private static class StatusLabel extends Label implements StatusHandler {
        private final Map<String, Boolean> m_statuses = new HashMap<String, Boolean>();
        
        public StatusLabel() {
            setText("checking server status...");
        }

        public void handleFail(String table) {
            m_statuses.put(table, false);
            updateStatus();
        }

        public void handleSuccess(String table) {
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
                StringBuilderImpl sb = new StringBuilderImpl();
                sb.append("Error communicating with server.");
                for (Map.Entry<String, Boolean> entry : m_statuses.entrySet()) {
                    if (!entry.getValue()) {
                        sb.append(" (" + entry.getKey() + ")");
                    }
                }
                setText(sb.toString());
                setStyleName("serverStatusBad");
            }
        }
    }
    
    
}
