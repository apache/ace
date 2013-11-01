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
package org.apache.ace.useradmin.ui.vaadin;

import org.apache.ace.useradmin.ui.editor.UserDTO;
import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.osgi.service.useradmin.User;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Window;

/**
 * Provides a simple editor for changing the password of the current user.
 */
public class EditUserInfoWindow extends Window {
    private final TextField m_groupField;
    private final TextField m_usernameTextField;
    private final PasswordField m_passwordTextField;
    private final Button m_applyButton;

    private volatile UserDTO m_userDTO;
    private volatile UserEditor m_userUtil;

    /**
     * Creates a new {@link EditUserInfoWindow} instance.
     */
    public EditUserInfoWindow() {
        setCaption("My info");
        setWidth("20%");

        m_usernameTextField = new TextField();
        m_usernameTextField.setEnabled(false);
        m_usernameTextField.setCaption("Username");

        m_passwordTextField = new PasswordField();
        m_passwordTextField.setCaption("Password");
        m_passwordTextField.setImmediate(true);
        m_passwordTextField.addListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                boolean changed = passwordChanged();
                m_applyButton.setEnabled(changed);
            }
        });

        m_groupField = new TextField();
        m_groupField.setEnabled(false);
        m_groupField.setCaption("Role");

        m_applyButton = new Button();
        m_applyButton.setCaption("Apply changes");
        m_applyButton.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                storeUserInfo();
            }
        });

        FormLayout formLayout = new FormLayout();
        formLayout.setMargin(false, false, false, true);
        formLayout.addComponent(m_usernameTextField);
        formLayout.addComponent(m_passwordTextField);
        formLayout.addComponent(m_groupField);
        formLayout.addComponent(m_applyButton);

        addComponent(formLayout);
    }

    @Override
    public void attach() {
        try {
            // Take the logged in user from the main application...
            initializeUserDTO((User) getApplication().getUser());
        }
        finally {
            super.attach();
        }
    }

    /**
     * @param parent
     *            the parent window of this editor, cannot be <code>null</code>.
     */
    public void open(Window parent) {
        // In case this window is already open, close it first...
        close();

        // will show this window on screen, and call attach() above...
        parent.addWindow(this);
        center();

        m_applyButton.setEnabled(false);
    }

    @Override
    protected void close() {
        try {
            m_userDTO = null;
        }
        finally {
            super.close();
        }
    }

    /**
     * Called for each change of the password field.
     * 
     * @return <code>true</code> if the password is valid, <code>false</code> otherwise.
     */
    protected boolean passwordChanged() {
        return isPasswordValid((String) m_passwordTextField.getValue());
    }

    /**
     * Will be called by Felix DM when all dependency are available
     */
    protected void start(org.apache.felix.dm.Component component) {
        close();
    }

    /**
     * Will be called by Felix DM when a dependency isn't available
     */
    protected void stop(org.apache.felix.dm.Component component) {
        close();
    }

    /**
     * @return <code>true</code> iff the user information is successfully stored, <code>false</code> otherwise.
     */
    protected boolean storeUserInfo() {
        String pwd = (String) m_passwordTextField.getValue();
        if (!isPasswordValid(pwd)) {
            showNotification("Password cannot be empty", Notification.TYPE_ERROR_MESSAGE);
            return false;
        }

        try {
            m_userDTO.setPassword(pwd);
            m_userUtil.updateUser(m_userDTO);

            showNotification(String.format("Password for '%s' updated!", m_userDTO.getUsername()), Notification.TYPE_TRAY_NOTIFICATION);

            return true;
        }
        catch (Exception e) {
            showNotification("Failed to store user changes!", Notification.TYPE_ERROR_MESSAGE);
        }

        return false;
    }

    private void initializeUserDTO(User user) {
        m_userDTO = new UserDTO((String) user.getProperties().get("username"), (String) user.getCredentials().get("password"), m_userUtil.getGroup(user).getName());

        m_usernameTextField.setValue(m_userDTO.getUsername());
        m_passwordTextField.setValue(m_userDTO.getPassword());
        m_groupField.setValue(m_userDTO.getGroupname());
    }

    private boolean isPasswordValid(String newPassword) {
        if ((newPassword == null) || "".equals(newPassword.trim())) {
            return false;
        }
        return true;
    }
}
