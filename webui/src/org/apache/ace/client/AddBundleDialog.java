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

import java.util.ArrayList;
import java.util.List;

import org.apache.ace.client.services.OBRBundleDescriptor;
import org.apache.ace.client.services.OBRService;
import org.apache.ace.client.services.OBRServiceAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.StackPanel;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * This dialog allows the user to select a bundle from an OBR. Upcoming improvements:
 * <ul>
 * <li>Allow the user to select an OBR</li>
 * <li>Allow file upload to a given OBR</li>
 * </ul>
 */
public class AddBundleDialog extends DialogBox {
    
    OBRServiceAsync m_obrService = GWT.create(OBRService.class);

    AddBundleDialog(final Main main) {
        setText("Add artifact");
        
        final ObjectListBox<OBRBundleDescriptor> obrFiles = new ObjectListBox<OBRBundleDescriptor>();
        obrFiles.setVisibleItemCount(8);
        
        m_obrService.getBundles(new AsyncCallback<OBRBundleDescriptor[]>() {
            public void onFailure(Throwable caught) {
                Window.alert("Error communicating with OBR");
                hide();
            }

            public void onSuccess(OBRBundleDescriptor[] result) {
                for (OBRBundleDescriptor d : result) {
                    obrFiles.addObject(d);
                }
            }
        });
        
        // Put together the stackpanel
        final StackPanel stackPanel = new StackPanel();
        stackPanel.add(obrFiles, "In the OBR");
        
        // Create the button panel
        HorizontalPanel buttonPanel = new HorizontalPanel();
        Button saveButton = new Button("Add");
        saveButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                m_obrService.importBundle(obrFiles.getSelectedObject(), new AsyncCallback<Void>() {
                    public void onFailure(Throwable caught) {
                        Window.alert("Error importing artifact " + obrFiles.getSelectedObject());
                    }
                    public void onSuccess(Void result) {
                        main.updateUI();
                    }
                    
                });
                hide();
            }
        });
        Button cancelButton = new Button("Cancel");
        cancelButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                // Nothing to do.
                hide();
            }
        });

        
        // Put the dialog together
        VerticalPanel content = new VerticalPanel();
        content.add(new Label("Where is your artifact?"));
        content.add(stackPanel);
        
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        content.add(buttonPanel);
        
        add(content);
        
        setSize("350px", "200px");
        center();
        setAnimationEnabled(true);
    }
    
    /**
     * Helper class that allows mapping from the string representation in a listbox
     * to the actual objects.
     */
    private static class ObjectListBox<T> extends ListBox {
        private final List<T> m_objects = new ArrayList<T>(); 
        
        public void addObject(T t) {
            m_objects.add(t);
            addItem(t.toString());
        }
        
        public T getSelectedObject() {
            if (getSelectedIndex() != -1) {
                return m_objects.get(getSelectedIndex());
            }
            return null;
        }
        
        @Override
        public void clear() {
            super.clear();
            m_objects.clear();
        }
    }
    
}
