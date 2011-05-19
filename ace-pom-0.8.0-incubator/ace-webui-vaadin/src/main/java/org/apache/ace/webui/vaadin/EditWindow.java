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
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditWindow extends Window {
    private final Window m_main;
    private TextField m_name;

    public EditWindow(final NamedObject object, Window main, List<UIExtensionFactory> factories) {
        m_main = main;
        setModal(true);
        setCaption("Edit " + object.getName());
        setWidth("500px");

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        m_name = new TextField("name");
        final TextField description = new TextField("description");

        m_name.setValue(object.getName());
        description.setValue(object.getDescription());

        layout.addComponent(m_name);
        layout.addComponent(description);

        TabSheet tabs = new TabSheet();
        tabs.setHeight("350px");
        Map<String, Object> context = new HashMap<String, Object>();
        context.put("object", object);
        for (UIExtensionFactory factory : factories) {
            com.vaadin.ui.Component component = factory.create(context);
            tabs.addTab(component);
        }
        layout.addComponent(tabs);

        Button close = new Button("Ok", new Button.ClickListener() {
            // inline click-listener
            public void buttonClick(Button.ClickEvent event) {
                // close the window by removing it from the parent window
                getParent().removeWindow(EditWindow.this);
                // create the feature
                object.setDescription((String) description.getValue());
            }
        });
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(close);
        layout.setComponentAlignment(close, Alignment.BOTTOM_RIGHT);
    }

    public void show() {
        if (getParent() != null) {
            // window is already showing
            m_main.getWindow().showNotification("Window is already open");
        } else {
            // Open the subwindow by adding it to the parent
            // window
            m_main.getWindow().addWindow(this);
        }
        setRelevantFocus();
    }

    private void setRelevantFocus() {
        m_name.focus();
    }
}

