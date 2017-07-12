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

import static org.osgi.service.log.LogService.LOG_INFO;
import static org.osgi.service.log.LogService.LOG_WARNING;

import java.util.Map;

import org.osgi.service.log.LogService;

import com.vaadin.event.FieldEvents;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Label;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a simple login dialog.
 */
public class LoginWindow extends Window {
    /**
     *
     */
    public static interface LoginFunction {
        boolean login(String name, String password);
    }

    private final LogService m_log;
    private final LoginFunction m_loginFunction;

    private final Label m_additionalInfo;

    /**
     * Creates a new {@link LoginWindow} instance.
     *
     * @param log
     *            the log service to use;
     * @param loginFunction
     *            the login callback to use.
     */
    public LoginWindow(LogService log, LoginFunction loginFunction) {
        super("Apache ACE Login");

        m_log = log;
        m_loginFunction = loginFunction;

        setResizable(false);
        setClosable(false);
        setModal(true);
        setWidth("20em");

        m_additionalInfo = new Label("");
        m_additionalInfo.setImmediate(true);
        m_additionalInfo.setStyleName("alert");
        m_additionalInfo.setHeight("1.2em");
        // Ensures the information message disappears when starting typing...
        FieldEvents.TextChangeListener changeListener = new FieldEvents.TextChangeListener() {
            @Override
            public void textChange(TextChangeEvent event) {
                m_additionalInfo.setValue("");
            }
        };

        final TextField nameField = new TextField("Name", "");
        nameField.addListener(changeListener);
        nameField.setImmediate(true);
        nameField.setWidth("100%");

        final PasswordField passwordField = new PasswordField("Password", "");
        passwordField.addListener(changeListener);
        passwordField.setImmediate(true);
        passwordField.setWidth("100%");

        Button loginButton = new Button("Login");
        loginButton.setImmediate(true);
        // Allow enter to be used to login directly...
        loginButton.setClickShortcut(KeyCode.ENTER);
        // Highlight this button as the default one...
        loginButton.addStyleName(Reindeer.BUTTON_DEFAULT);

        loginButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                Button button = event.getButton();
                button.setEnabled(false);

                try {
                    String username = (String) nameField.getValue();
                    String password = (String) passwordField.getValue();

                    if (m_loginFunction.login(username, password)) {
                        m_log.log(LOG_INFO, "Apache Ace WebUI succesfull login by user: " + username);

                        closeWindow();
                    }
                    else {
                        m_log.log(LOG_WARNING, "Apache Ace WebUI invalid username or password entered.");

                        m_additionalInfo.setValue("Invalid username or password!");

                        nameField.focus();
                        nameField.selectAll();
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

        content.addComponent(nameField);
        content.addComponent(passwordField);
        content.addComponent(m_additionalInfo);
        content.addComponent(loginButton);

        content.setComponentAlignment(loginButton, Alignment.BOTTOM_CENTER);

        nameField.focus();
    }

    /**
     * Shows this login window on screen.
     *
     * @param parent
     *            the parent window, cannot be <code>null</code>.
     */
    public void openWindow(Window parent) {
        parent.addParameterHandler(this);
        parent.addWindow(this);

        center();
    }

    /**
     * Closes this login window.
     */
    public void closeWindow() {
        getParent().removeParameterHandler(this);
        close();
    }

    @Override
    public void handleParameters(Map<String, String[]> parameters) {
        if (parameters.containsKey("sessionTimedOut")) {
            m_additionalInfo.setValue("Session timed out!");
        }
        super.handleParameters(parameters);
    }
}
