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

import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;

import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.domain.NamedTargetObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a generic editor for repository objects.
 */
public class EditWindow extends Window {

    private final TextField m_name;
    private final TextField m_description;

    /**
     * @param object
     * @param factories
     */
    public EditWindow(final NamedObject object, List<UIExtensionFactory> factories) {
        setModal(true);
        setCaption("Edit " + object.getName());
        setWidth("500px");

        m_name = new TextField("Name", object.getName());
        m_name.setReadOnly(object instanceof NamedTargetObject);
        m_name.setWidth("100%");

        m_description = new TextField("Description", object.getDescription());
        m_description.setWidth("100%");

        VerticalLayout fields = new VerticalLayout();
        fields.setSpacing(true);
        fields.addComponent(m_name);
        fields.addComponent(m_description);

        TabSheet tabs = new TabSheet();
        tabs.setHeight("350px");
        tabs.setWidth("100%");
        tabs.setVisible(!factories.isEmpty());

        Map<String, Object> context = new HashMap<String, Object>();
        context.put("object", object);

        for (UIExtensionFactory factory : factories) {
            try {
                tabs.addTab(factory.create(context));
            }
            catch (Throwable ex) {
                // We ignore extension factories that throw exceptions
                // TODO: log this or something
                ex.printStackTrace();
            }
        }

        Button okButton = new Button("Ok", new Button.ClickListener() {
            public void buttonClick(Button.ClickEvent event) {
                if (object instanceof NamedTargetObject) {
                    // do nothing
                }
                else {
                    object.setDescription((String) m_description.getValue());
                }

                closeDialog();
            }
        });

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
        layout.addComponent(tabs);
        layout.addComponent(buttonBar);

        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.setComponentAlignment(buttonBar, Alignment.BOTTOM_RIGHT);
    }

    /**
     * @param parent
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
     * Closes this dialog by removing it from the parent window.
     */
    void closeDialog() {
        // close the window by removing it from the parent window
        getParent().removeWindow(this);
    }

    /**
     * Sets the focus to the name field.
     */
    private void setRelevantFocus() {
        m_name.focus();
    }
}
