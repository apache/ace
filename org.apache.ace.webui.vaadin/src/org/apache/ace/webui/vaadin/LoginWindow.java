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

import org.osgi.service.log.LogService;

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * Provides a simple login dialog.
 */
public class LoginWindow extends Window {

    public interface LoginFunction {
        boolean login(String name, String password);
    }

    private volatile LogService m_log;

    private TextField m_name;
    private PasswordField m_password;
    private Button m_loginButton;
    private LoginFunction m_loginFunction;

    public LoginWindow(final LogService log, final LoginFunction loginFunction) {
        super("Apache ACE Login");

        m_log = log;
        m_loginFunction = loginFunction;

        setResizable(false);
        setClosable(false);
        setModal(true);
        setWidth("15em");

        m_name = new TextField("Name", "");
        m_name.setImmediate(true);

        m_password = new PasswordField("Password", "");
        m_password.setImmediate(true);

        m_loginButton = new Button("Login");
        m_loginButton.setImmediate(true);
        // Allow enter to be used to login directly...
        m_loginButton.setClickShortcut(KeyCode.ENTER);
        // Highlight this button as the default one...
        m_loginButton.addStyleName("primary");

        m_loginButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                final Button button = event.getButton();
                button.setEnabled(false);

                try {
                    String username = (String) m_name.getValue();
                    String password = (String) m_password.getValue();

                    if (m_loginFunction.login(username, password)) {
                        m_log.log(LogService.LOG_INFO, "Apache Ace WebUI succesfull login by user: " + username);

                        closeWindow();
                    }
                    else {
                        m_log.log(LogService.LOG_WARNING, "Apache Ace WebUI invalid username or password entered.");

                        getParent().showNotification("Invalid username or password!");
                        setRelevantFocus();
                    }
                }
                finally {
                    button.setEnabled(true);
                }
            }
        });

        final VerticalLayout content = (VerticalLayout) getContent();
        content.setSpacing(true);
        content.setMargin(true);
        content.setSizeFull();

        content.addComponent(m_name);
        content.addComponent(m_password);
        content.addComponent(m_loginButton);

        content.setComponentAlignment(m_loginButton, Alignment.BOTTOM_CENTER);

        setRelevantFocus();
    }

    /**
     * Gives the username field the current focus.
     */
    void setRelevantFocus() {
        m_name.focus();
        m_name.selectAll();
    }

    /**
     * Closes this login window.
     */
    public void closeWindow() {
        getParent().removeWindow(this);
    }
}