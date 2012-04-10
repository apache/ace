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

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

public abstract class GenericAddWindow extends Window {

    protected final TextField m_name;
    protected final TextField m_description;

    public GenericAddWindow(String caption) {
        setModal(true);
        setWidth("15em");
        setCaption(caption);

        m_name = new TextField("Name");
        m_name.setWidth("100%");

        m_description = new TextField("Description");
        m_description.setWidth("100%");

        initDialog();
    }

    /**
     * Shows this dialog on screen.
     * 
     * @param window the parent window to show this dialog on, cannot be <code>null</code>.
     */
    public void show(final Window window) {
        if (getParent() != null) {
            // window is already showing
            window.showNotification("Window is already open");
        }
        else {
            // Open the subwindow by adding it to the parent window
            window.addWindow(this);
        }
        setRelevantFocus();
    }

    /**
     * Closes this dialog by removing it from the parent window.
     */
    protected void closeDialog() {
        // close the window by removing it from the parent window
        getParent().removeWindow(this);
    }

    /**
     * Called when the {@link #onOk(String, String)} method failed with an exception.
     * 
     * @param e the exception to handle, never <code>null</code>.
     */
    protected abstract void handleError(Exception e);

    /**
     * Initializes this dialog by placing all components on it.
     */
    protected void initDialog() {
        VerticalLayout fields = new VerticalLayout();
        fields.setSpacing(true);
        fields.addComponent(m_name);
        fields.addComponent(m_description);

        Button okButton = new Button("Ok", new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                try {
                    onOk((String) m_name.getValue(), (String) m_description.getValue());
                    closeDialog();
                }
                catch (Exception e) {
                    handleError(e);
                }
            }
        });
        // Allow enter to be used to close this dialog with enter directly...
        okButton.setClickShortcut(KeyCode.ENTER);
        okButton.addStyleName("primary");

        Button cancelButton = new Button("Cancel", new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                closeDialog();
            }
        });

        HorizontalLayout buttonBar = new HorizontalLayout();
        buttonBar.setSpacing(true);
        buttonBar.addComponent(okButton);
        buttonBar.addComponent(cancelButton);

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);
        layout.addComponent(fields);
        layout.addComponent(buttonBar);

        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.setComponentAlignment(buttonBar, Alignment.BOTTOM_RIGHT);
    }

    /**
     * Called when the user acknowledges this window by pressing Ok.
     * 
     * @param name the value of the name field;
     * @param description the value of the description field.
     * @throws Exception in case the creation failed.
     */
    protected abstract void onOk(String name, String description) throws Exception;

    /**
     * Sets the focus to the name field.
     */
    private void setRelevantFocus() {
        m_name.focus();
    }
}