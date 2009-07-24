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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.AssociationServiceAsync;
import org.apache.ace.client.services.Descriptor;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.impl.StringBuilderImpl;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
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
    private BundleTable m_bundleTable = new BundleTable(m_statusLabel, this);
    private GroupTable m_groupTable = new GroupTable(m_statusLabel, this);
    private LicenseTable m_licenseTable = new LicenseTable(m_statusLabel, this);
    private TargetTable m_targetTable = new TargetTable(m_statusLabel, this);
    
    AssociationServiceAsync m_assocationService = GWT.create(AssociationService.class);
    
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
                new AddBundleDialog(Main.this).show();
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
        
        // Create the association buttons
        Button b2g = new Button("<->");
        RootPanel.get("b2gButton").add(b2g);
        b2g.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_assocationService.link(m_bundleTable.getCheckedObject(), m_groupTable.getCheckedObject(), new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error creating association: " + caught);
                    }
                    public void onSuccess(Void result) {
                        updateHighlight();
                    }
                });
            }
        });
        
        Button g2l = new Button("<->");
        RootPanel.get("g2lButton").add(g2l);
        g2l.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_assocationService.link(m_groupTable.getCheckedObject(), m_licenseTable.getCheckedObject(), new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error creating association: " + caught);
                    }
                    public void onSuccess(Void result) {
                        updateHighlight();
                    }
                });
            }
        });
        Button l2t = new Button("<->");
        RootPanel.get("l2tButton").add(l2t);
        l2t.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_assocationService.link(m_licenseTable.getCheckedObject(), m_targetTable.getCheckedObject(), new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error creating association: " + caught);
                    }
                    public void onSuccess(Void result) {
                        updateHighlight();
                    }
                });
            }
        });
        
        // Set a timer to regularly update the UI
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
     * Triggers the updating of the highlight
     */
    void updateHighlight() {
        m_assocationService.getRelated(getSelectedObject(), new AsyncCallback<Descriptor[]>() {
            public void onFailure(Throwable caught) {
                // Too bad...
            }
            public void onSuccess(Descriptor[] result) {
                highlight(Arrays.asList(result));
            }
        });
    }
    
    /**
     * Helper method to delegate the highlights to the tables.
     */
    private void highlight(List<Descriptor> descriptors) {
        m_bundleTable.highlight(descriptors);
        m_groupTable.highlight(descriptors);
        m_licenseTable.highlight(descriptors);
        m_targetTable.highlight(descriptors);
    }
    
    /**
     * Finds the currently selected object; if no object is selected, <code>null</code> will be returned.
     */
    Descriptor getSelectedObject() {
        if (m_bundleTable.getSelectedObject() != null) {
            return m_bundleTable.getSelectedObject();
        }
        else if (m_groupTable.getSelectedObject() != null) {
            return m_groupTable.getSelectedObject();
        }
        else if (m_licenseTable.getSelectedObject() != null) {
            return m_licenseTable.getSelectedObject();
        }
        else if (m_targetTable.getSelectedObject() != null) {
            return m_targetTable.getSelectedObject();
        }
        return null;
    }
    
    /**
     * Makes sure there is only one selected item at a time.
     */
    void deselectOthers(Descriptor descriptor) {
        m_bundleTable.deselectOthers(descriptor);
        m_groupTable.deselectOthers(descriptor);
        m_licenseTable.deselectOthers(descriptor);
        m_targetTable.deselectOthers(descriptor);
    }
    
    /**
     * Label that can be used a s {@link StatusHandler} for the tables. Will report
     * a successful connection when all components are happy.
     */
    private static class StatusLabel extends Label implements StatusHandler {
        private final Map<String, Boolean> m_statuses = new HashMap<String, Boolean>();
        
        /**
         * Indicates whether there should be detailed information about a broken connection.
         */
        private static final boolean VERBOSE = true;
        
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
                if (VERBOSE) {
                    for (Map.Entry<String, Boolean> entry : m_statuses.entrySet()) {
                        if (!entry.getValue()) {
                            sb.append(" (" + entry.getKey() + ")");
                        }
                    }
                }
                setText(sb.toString());
                setStyleName("serverStatusBad");
            }
        }
    }
}
