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
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;
import org.apache.ace.client.services.TargetServiceAsync;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.impl.StringBuilderImpl;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

/**
 * Entry point for the web UI.
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
        // Session test, makes sure we trigger the creation of a single session
        // before we continue asynchronously getting stuff from the server.
        // This ensures we don't end up with 4 or 5 sessions per client. Still
        // test code because we invoke an arbitrary service. ;)
        TargetServiceAsync ts = GWT.create(TargetService.class);
        ts.getTargets(new AsyncCallback<TargetDescriptor[]>() {

            public void onFailure(Throwable caught) {
                createUI();
//                Window.alert("Callback failed, not updating UI...");
            }

            public void onSuccess(TargetDescriptor[] result) {
                createUI();
                // Set a timer to regularly update the UI
                Timer refreshTimer = new Timer() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                };
                refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
            }});
        
        
    }

    private void createUI() {
        Button addBundleButton = new Button("+");
        addBundleButton.addStyleDependentName("add");
        addBundleButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                new AddBundleDialog(Main.this).show();
            }
        });

        Button addGroupButton = new Button("+");
        addGroupButton.addStyleDependentName("add");
        addGroupButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_groupTable.addNew();
            }
        });
        
        Button addLicenseButton = new Button("+");
        addLicenseButton.addStyleDependentName("add");
        addLicenseButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_licenseTable.addNew();
            }
        });
        
        // Create some scrollpanels with our tables
        ScrollPanel bundleScrollPanel = new ScrollPanel(m_bundleTable);
//        bundleScrollPanel.setHeight("30em");
        bundleScrollPanel.setStyleName("objectTable");
        ScrollPanel groupScrollPanel = new ScrollPanel(m_groupTable);
//        groupScrollPanel.setHeight("30em");
        groupScrollPanel.setStyleName("objectTable");
        ScrollPanel licenseScrollPanel = new ScrollPanel(m_licenseTable);
//        licenseScrollPanel.setHeight("30em");
        licenseScrollPanel.setStyleName("objectTable");
        ScrollPanel targetScrollPanel = new ScrollPanel(m_targetTable);
//        targetScrollPanel.setHeight("30em");
        targetScrollPanel.setStyleName("objectTable");
        
        // Create the association buttons
        Button b2g = new Button("<->");
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
        g2l.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                GroupDescriptor group = m_groupTable.getCheckedObject();
                LicenseDescriptor license = m_licenseTable.getCheckedObject();
                m_assocationService.link(group, license, new AsyncCallback<Void>() {
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
        
        FlexTable rootPanel = new FlexTable();
        FlexCellFormatter formatter = rootPanel.getFlexCellFormatter();
        rootPanel.setWidth("100%");
        rootPanel.setHeight("100%");
        rootPanel.setHTML(1, 0, "Bundle");
        formatter.setWidth(1, 0, "25%");
        rootPanel.setWidget(2, 0, addBundleButton);
        rootPanel.setWidget(2, 1, b2g);
        formatter.setStyleName(2, 1, "fixedColumn");
        rootPanel.setWidget(3, 0, bundleScrollPanel);
        formatter.setHeight(3, 0, "90%");
        rootPanel.setHTML(1, 2, "Group");
        formatter.setWidth(1, 2, "25%");
        rootPanel.setWidget(2, 2, addGroupButton);
        rootPanel.setWidget(2, 3, g2l);
        formatter.setStyleName(2, 3, "fixedColumn");
        rootPanel.setWidget(3, 2, groupScrollPanel);
        rootPanel.setHTML(1, 4, "License");
        formatter.setWidth(1, 4, "25%");
        rootPanel.setWidget(2, 4, addLicenseButton);
        rootPanel.setWidget(2, 5, l2t);
        formatter.setStyleName(2, 5, "fixedColumn");
        rootPanel.setWidget(3, 4, licenseScrollPanel);
        rootPanel.setHTML(1, 6, "Target");
        formatter.setWidth(1, 6, "25%");
        rootPanel.setWidget(3, 6, targetScrollPanel);
        rootPanel.setWidget(0, 0, new CheckoutPanel(this));
        formatter.setColSpan(0, 0, 7);
        rootPanel.setWidget(4, 0, m_statusLabel);
        formatter.setColSpan(4, 0, 7);
        RootPanel.get("body").add(rootPanel);
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
                Window.alert("Error updating highlights: " + caught);
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
