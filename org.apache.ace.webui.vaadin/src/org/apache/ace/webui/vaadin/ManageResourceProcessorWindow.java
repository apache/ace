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
package org.apache.ace.webui.vaadin;

import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.osgi.framework.Constants;

import com.vaadin.data.Item;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Table;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a dialog for managing resource processors.
 */
abstract class ManageResourceProcessorWindow extends Window {
    private static final String PROPERTY_SYMBOLIC_NAME = "symbolic name";
    private static final String PROPERTY_VERSION = "version";
    private static final String PROPERTY_REMOVE = "remove";

    private final Table m_artifactsTable;

    /**
     * Creates a new ManageResourceProcessorWindow instance.
     */
    public ManageResourceProcessorWindow() {
        super("Resource Processors");

        setModal(true);
        setWidth("50em");
        setCloseShortcut(KeyCode.ESCAPE);

        m_artifactsTable = new Table("Available resource processors");
        m_artifactsTable.addContainerProperty(PROPERTY_SYMBOLIC_NAME, String.class, null);
        m_artifactsTable.addContainerProperty(PROPERTY_VERSION, String.class, null);
        m_artifactsTable.addContainerProperty(PROPERTY_REMOVE, Button.class, null);
        m_artifactsTable.setSizeFull();
        m_artifactsTable.setSelectable(true);
        m_artifactsTable.setMultiSelect(true);
        m_artifactsTable.setImmediate(true);
        m_artifactsTable.setHeight("15em");

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        layout.addComponent(m_artifactsTable);
    }

    /**
     * @param parent
     *            the parent window to show this dialog on top of.
     */
    public void showWindow(Window parent) {
        try {
            // Fill the artifacts table with the data from the OBR...
            populateArtifactTable(m_artifactsTable);

            parent.addWindow(this);
        }
        catch (Exception e) {
            // We've not yet added this window to the given parent, so we cannot use #showErrorNotification here...
            parent.showNotification("Failed to retrieve OBR repository!", "Reason: <br/>" + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
        }
    }

    private Button createRemoveButton(final ArtifactObject rp) {
        Button button = new Button("x");
        button.setStyleName(Reindeer.BUTTON_SMALL);
        button.setDescription("Remove " + rp.getAttribute(Constants.BUNDLE_SYMBOLICNAME));
        button.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                event.getButton().setEnabled(false);

                try {
                    getArtifactRepository().remove(rp);
                    m_artifactsTable.removeItem(rp.getDefinition());
                }
                catch (Exception e) {
                    getParent().showNotification("Failed to delete resource", "Reason: <br/>" + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                }
            }
        });
        return button;
    }

    private void populateArtifactTable(Table artifactsTable) {
        for (ArtifactObject rp : getArtifactRepository().getResourceProcessors()) {
            String bsn = rp.getAttribute(Constants.BUNDLE_SYMBOLICNAME);

            Item item = artifactsTable.addItem(rp.getDefinition());
            item.getItemProperty(PROPERTY_SYMBOLIC_NAME).setValue(bsn);
            item.getItemProperty(PROPERTY_VERSION).setValue(rp.getAttribute(Constants.BUNDLE_VERSION));
            item.getItemProperty(PROPERTY_REMOVE).setValue(createRemoveButton(rp));
        }
    }

    /**
     * @return the artifact repository.
     */
    protected abstract ArtifactRepository getArtifactRepository();
}
