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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * Provides a generic editor for repository objects.
 */
public abstract class EditWindow extends Window {

    protected final TextField m_name;
    protected final TextField m_description;

    /**
     * @param object
     * @param factories
     */
    public EditWindow(String caption, NamedObject object, List<UIExtensionFactory> factories) {
        setModal(true);
        setWidth("50em");
        setCaption(caption);

        m_name = new TextField("Name", object.getName());
        m_name.setReadOnly(true);
        m_name.setWidth("100%");

        m_description = new TextField("Description", object.getDescription());
        m_description.setWidth("100%");

        initDialog(object, factories);
    }

    /**
     * Shows this dialog on screen.
     *
     * @param window the parent window to show this dialog on, cannot be <code>null</code>.
     */
    public void show(Window parent) {
        if (getParent() != null) {
            // window is already showing
            parent.showNotification("Window is already open!");
        }
        else {
            parent.addWindow(this);
        }
        setRelevantFocus();
    }

    /**
     * Called when the {@link #onOk(String, String)} method failed with an exception.
     *
     * @param e the exception to handle, never <code>null</code>.
     */
    protected abstract void handleError(Exception e);

    /**
     * @param object
     * @param factories
     */
    protected void initDialog(NamedObject object, List<UIExtensionFactory> factories) {
        VerticalLayout fields = new VerticalLayout();
        fields.setSpacing(true);
        fields.addComponent(m_name);
        fields.addComponent(m_description);

        TabSheet tabs = new TabSheet();
        tabs.setHeight("350px");
        tabs.setWidth("100%");
        tabs.setVisible(!factories.isEmpty());

        Map<String, Object> context = new HashMap<>();
        context.put("object", object.getObject());
        populateContext(context);

        for (UIExtensionFactory factory : factories) {
            try {
                Component tabComp = factory.create(context);
                if (tabComp != null) {
                    tabs.addTab(tabComp);
                }
            }
            catch (Throwable ex) {
                // We ignore extension factories that throw exceptions
                // TODO: log this or something
                ex.printStackTrace();
            }
        }

        Button okButton = new Button("Ok", new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                try {
                    onOk((String) m_name.getValue(), (String) m_description.getValue());
                    close();
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
                close();
            }
        });
        cancelButton.setClickShortcut(KeyCode.ESCAPE);

        HorizontalLayout buttonBar = new HorizontalLayout();
        buttonBar.setSpacing(true);
        buttonBar.addComponent(okButton);
        buttonBar.addComponent(cancelButton);

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);
        layout.addComponent(fields);
        layout.addComponent(tabs);
        layout.addComponent(buttonBar);

        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.setComponentAlignment(buttonBar, Alignment.BOTTOM_RIGHT);

        m_name.focus();
    }

    protected Map<String, Object> populateContext(Map<String, Object> context) {
        return context;
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
