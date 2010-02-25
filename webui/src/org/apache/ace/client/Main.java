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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.AssociationServiceAsync;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;
import org.apache.ace.client.services.TargetServiceAsync;
import org.apache.ace.client.services.AssociationService.AssocationType;

import com.allen_sauer.gwt.dnd.client.PickupDragController;
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
import com.google.gwt.user.client.ui.ToggleButton;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

/**
 * Entry point for the web UI.
 */
public class Main implements EntryPoint {
    private static final int REFRESH_INTERVAL = 2000;
    private StatusLabel m_statusLabel = new StatusLabel();
    private PickupDragController m_dragController = new PickupDragController(RootPanel.get(), false);
    private BundleTable m_bundleTable = new BundleTable(m_statusLabel, m_dragController, this);
    private GroupTable m_groupTable = new GroupTable(m_statusLabel, m_dragController, this);
    private LicenseTable m_licenseTable = new LicenseTable(m_statusLabel, m_dragController, this);
    private TargetTable m_targetTable = new TargetTable(m_statusLabel, m_dragController, this);
    
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
        
        final ToggleButton associationTypeButton = new ToggleButton("STATIC");
        m_assocationService.getAssocationType(new AsyncCallback<AssocationType>() {
            public void onSuccess(AssocationType result) {
                switch (result) {
                case STATIC:
                    associationTypeButton.setDown(false);
                    associationTypeButton.setText("STATIC");
                    break;
                case DYNAMIC:
                    associationTypeButton.setDown(true);
                    associationTypeButton.setText("DYNAMIC");
                    break;
                }
            };
            public void onFailure(Throwable caught) {
            }
        });
        associationTypeButton.addStyleDependentName("add");
        associationTypeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_assocationService.getAssocationType(new AsyncCallback<AssocationType>() {
                    public void onSuccess(AssocationType result) {
                        switch (result) {
                            case STATIC:
                                m_assocationService.setAssocationType(AssocationType.DYNAMIC, null);
                                associationTypeButton.setText("DYNAMIC");
                                break;
                            case DYNAMIC:
                                m_assocationService.setAssocationType(AssocationType.STATIC, null);
                                associationTypeButton.setText("STATIC");
                                break;
                        }
                    };
                    public void onFailure(Throwable caught) {
                    }
                });
            }
        });
        
        // Create some scrollpanels with our tables
        ScrollPanel bundleScrollPanel = new ScrollPanel(m_bundleTable);
        bundleScrollPanel.setStyleName("objectTable");
        ScrollPanel groupScrollPanel = new ScrollPanel(m_groupTable);
        groupScrollPanel.setStyleName("objectTable");
        ScrollPanel licenseScrollPanel = new ScrollPanel(m_licenseTable);
        licenseScrollPanel.setStyleName("objectTable");
        ScrollPanel targetScrollPanel = new ScrollPanel(m_targetTable);
        targetScrollPanel.setStyleName("objectTable");
        
        FlexTable rootPanel = new FlexTable();
        FlexCellFormatter formatter = rootPanel.getFlexCellFormatter();
        rootPanel.setWidth("100%");
        rootPanel.setHeight("100%");
        rootPanel.setHTML(1, 0, "Artifact");
        formatter.setWidth(1, 0, "25%");
        rootPanel.setWidget(2, 0, addBundleButton);
        rootPanel.setWidget(3, 0, bundleScrollPanel);
        formatter.setHeight(3, 0, "90%");
        rootPanel.setHTML(1, 1, "Feature");
        formatter.setWidth(1, 1, "25%");
        rootPanel.setWidget(2, 1, addGroupButton);
        rootPanel.setWidget(3, 1, groupScrollPanel);
        rootPanel.setHTML(1, 2, "Distribution");
        formatter.setWidth(1, 2, "25%");
        rootPanel.setWidget(2, 2, addLicenseButton);
        rootPanel.setWidget(3, 2, licenseScrollPanel);
        rootPanel.setHTML(1, 3, "Target");
        formatter.setWidth(1, 3, "25%");
        rootPanel.setWidget(3, 3, targetScrollPanel);
        rootPanel.setWidget(0, 0, new CheckoutPanel(this));
        formatter.setColSpan(0, 0, 3);
        rootPanel.setWidget(0, 1, associationTypeButton);
        rootPanel.setWidget(4, 0, m_statusLabel);
        formatter.setColSpan(4, 0, 4);
        RootPanel.get("body").add(rootPanel);
        
        m_dragController.setBehaviorDragProxy(true);
        m_dragController.setBehaviorDragStartSensitivity(4);
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
        Descriptor selected = getSelectedObject();
        if (selected == null) {
            List<Descriptor> emptyList = Collections.emptyList();
            highlight(emptyList);
        }
        else {
            m_assocationService.getRelated(selected, new AsyncCallback<Descriptor[]>() {
                public void onFailure(Throwable caught) {
                    // Too bad...
                    Window.alert("Error updating highlights: " + caught);
                }
                public void onSuccess(Descriptor[] result) {
                    highlight(Arrays.asList(result));
                }
            });
        }
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
            setText("Checking server status...");
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
                setText("Connected to server.");
                setStyleName("serverStatusGood");
            }
            else {
                StringBuilderImpl sb = new StringBuilderImpl();
                sb.append("Not connected to server.");
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
