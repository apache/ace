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
import java.util.List;
import java.util.Map;

import org.apache.ace.client.Main.StatusHandler;
import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.AssociationServiceAsync;
import org.apache.ace.client.services.Descriptor;

import com.allen_sauer.gwt.dnd.client.DragContext;
import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.allen_sauer.gwt.dnd.client.drop.DropController;
import com.allen_sauer.gwt.dnd.client.drop.SimpleDropController;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Label;

/**
 * Basic table for using a valueobject per row.
 */
public abstract class ObjectTable<T extends Descriptor> extends FlexTable {
    private final StatusHandler m_handler;
    private final PickupDragController m_dragController;
    private final Main m_main;
    
    private final Map<T, ObjectPanel> m_panels = new HashMap<T, ObjectPanel>();
    
    private AssociationServiceAsync m_associationService = GWT.create(AssociationService.class);
    
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
                ObjectPanel panel = m_panels.get(t);
                if (panel == null) {
                    panel = new ObjectPanel(t);
                    m_panels.put(t, panel);
                }
                panel.setText(getText(t));
                if (getRowCount() <= row || !getWidget(row, 0).equals(panel)) {
                    // Setting the widget again might screw up focus
                    setWidget(row, 0, panel);
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
     * Sole constructor for this class; all subclasses must delegate to this one.
     */
    public ObjectTable(StatusHandler handler, PickupDragController dragController, Main main) {
        m_handler = handler;
        m_dragController = dragController;
        m_main = main;
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
    protected abstract void getDescriptors(AsyncCallback<T[]> callback);

    /**
     * Removes the given object from the repository.
     */
    protected abstract void remove(T object, AsyncCallback<Void> callback);
    
    /**
     * Links an object of ours with some other, that has been dropped onto our object.
     */
    protected abstract void link(T object, Descriptor other, AsyncCallback<Void> callback);
    
    /**
     * States whether removal of this object is allowed.
     */
    protected boolean canDelete() {
        return true;
    }
    
    /**
     * Finds the currently selected object, or <code>null</code> if none is found.
     */
    public T getSelectedObject() {
        for (Map.Entry<T, ObjectPanel> entry : m_panels.entrySet()) {
            if (entry.getValue().isSelected()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Updates the contents of this table
     */
    public void updateTable() {
        getDescriptors(m_asyncCallback);
    }
    
    /**
     * Highlights the given objects, if they are in this table.
     */
    public void highlight(List<Descriptor> descriptors) {
        for (Map.Entry<T, ObjectPanel> entry : m_panels.entrySet()) {
            if (descriptors.contains(entry.getKey())) {
                entry.getValue().addStyleDependentName("related");
            }
            else {
                entry.getValue().removeStyleDependentName("related");
            }
        }
    }
    
    /**
     * Deselects all objects, except for the one given.
     */
    public void deselectOthers(Descriptor descriptor) {
        for (Map.Entry<T, ObjectPanel> entry : m_panels.entrySet()) {
            if (!entry.getKey().equals(descriptor)) {
                entry.getValue().setSelected(false);
            }
        }
    }

    /**
     * {@link ObjectPanel} represents exactly one Descriptor. It will handle selection, and can
     * unlink and delete the related object.
     */
    private class ObjectPanel extends FocusPanel {
        private final Label m_label;
        private boolean m_selected;
        private final T m_object;

        public ObjectPanel(final T object) {
            m_object = object;
            DockPanel mainPanel = new DockPanel();

            m_label = new Label(getText(object));
            m_label.setStylePrimaryName("objectpaneltext");
            mainPanel.add(m_label, DockPanel.WEST);

            if (canDelete()) {
                Button delete = new Button("x");
                delete.setTitle("Delete this object");
                mainPanel.add(delete, DockPanel.EAST);
                delete.addClickHandler(new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        ObjectTable.this.remove(object, new AsyncCallback<Void>() {
                            public void onFailure(Throwable caught) {
                                Window.alert("Error deleting object");
                            }
                            public void onSuccess(Void result) {
                                m_main.updateUI();
                            }
                        });
                    }
                });
            }
            
            Button unlink = new Button("-");
            unlink.setTitle("Unlink this object from the current selection");
            mainPanel.add(unlink, DockPanel.EAST);
            unlink.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    m_associationService.unlink(object, m_main.getSelectedObject(), new AsyncCallback<Void>() {
                        public void onFailure(Throwable caught) {
                            Window.alert("Error breaking link");
                        }
                        public void onSuccess(Void result) {
                            m_main.updateHighlight();
                        }
                    });
                    event.stopPropagation(); // we don't want the panel to get the click
                }
            });
            
            add(mainPanel);
            
            setStylePrimaryName("objectpanel");
            
            addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    setSelected(true);
                    m_main.deselectOthers(object);
                    m_main.updateHighlight();
                }
            });
            
            m_dragController.makeDraggable(this);
            DropController dropController = new SimpleDropController(this) {
                @Override
                public void onDrop(DragContext context) {
                    Object other = ((ObjectPanel) context.draggable).getObject();
                    if (other instanceof Descriptor) {
                        link(object, (Descriptor) other, new AsyncCallback<Void>() {
                            public void onFailure(Throwable caught) {
                                Window.alert("Error linking objects.");
                            }
                            public void onSuccess(Void result) {
                                // Hurrah!
                                m_main.updateHighlight();
                            }
                        });
                    }
                }
            };
            m_dragController.registerDropController(dropController);
        }
        
        public T getObject() {
            return m_object;
        }
        
        public boolean isSelected() {
            return m_selected;
        }
        
        public void setSelected(boolean selected) {
            m_selected = selected;
            if (selected) {
                addStyleDependentName("selected");
                m_label.addStyleDependentName("selected");
            }
            else {
                removeStyleDependentName("selected");
                m_label.removeStyleDependentName("selected");
            }
        }
        
        public void setText(String text) {
            m_label.setText(text);
        }
    }
}
