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

import java.util.List;

import org.apache.ace.useradmin.ui.editor.GroupNotFoundException;
import org.apache.ace.useradmin.ui.editor.UserAlreadyExistsException;
import org.apache.ace.useradmin.ui.editor.UserDTO;
import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.apache.ace.useradmin.ui.editor.UserNotFoundException;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.User;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.Select;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a more advanced CRUD-functionality for adding, removing or updating users in ACE.
 */
public class UserAdminWindow extends Window {
    private final Table m_userTable;
    private final Select m_groupSelect;
    private final TextField m_usernameTextField;
    private final PasswordField m_passwordTextField;
    private final Button m_applyButton;
    private final Button m_cancelButton;
    private final Button m_removeUserButton;

    private volatile UserEditor m_userUtil;

    /**
     * Creates a new {@link UserAdminWindow} instance.
     */
    public UserAdminWindow() {
        setCaption("Manage users");
        setWidth("30%");

        m_userTable = new Table();
        m_userTable.setSizeFull();
        m_userTable.setImmediate(true);
        m_userTable.setSelectable(true);
        m_userTable.setSortDisabled(false);
        m_userTable.addContainerProperty("User", UserDTO.class, null);
        m_userTable.addListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                selectUser((UserDTO) m_userTable.getValue());
            }
        });

        VerticalLayout usersList = new VerticalLayout();
        usersList.setSizeFull();
        usersList.addComponent(m_userTable);

        Button addUserButton = new Button("+");
        addUserButton.setStyleName(Reindeer.BUTTON_SMALL);
        addUserButton.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                prepareForNewUser();
            }
        });

        m_removeUserButton = new Button();
        m_removeUserButton.setStyleName(Reindeer.BUTTON_SMALL);
        m_removeUserButton.setCaption("-");
        m_removeUserButton.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                removeSelectedUser((UserDTO) m_userTable.getValue());
            }
        });

        HorizontalLayout addRemoveUserButtons = new HorizontalLayout();
        addRemoveUserButtons.setMargin(true, false, false, false);
        addRemoveUserButtons.setSpacing(true);
        addRemoveUserButtons.addComponent(addUserButton);
        addRemoveUserButtons.addComponent(m_removeUserButton);
        usersList.addComponent(addRemoveUserButtons);

        usersList.setExpandRatio(m_userTable, 1.0f);
        usersList.setExpandRatio(addRemoveUserButtons, 0.0f);

        ValueChangeListener changeListener = new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                m_applyButton.setEnabled(isCurrentFormValid());
                m_cancelButton.setEnabled(true);
            }
        };

        m_usernameTextField = new TextField();
        m_usernameTextField.setCaption("Username");
        m_usernameTextField.setImmediate(true);
        m_usernameTextField.setRequired(true);
        m_usernameTextField.addListener(changeListener);

        m_passwordTextField = new PasswordField();
        m_passwordTextField.setCaption("Password");
        m_passwordTextField.setImmediate(true);
        m_passwordTextField.setRequired(true);
        m_passwordTextField.addListener(changeListener);

        m_groupSelect = new Select();
        m_groupSelect.setCaption("Role");
        m_groupSelect.setImmediate(true);
        m_groupSelect.setNullSelectionAllowed(false);
        m_groupSelect.setRequired(true);
        m_groupSelect.addListener(changeListener);

        FormLayout formLayout = new FormLayout();
        formLayout.addComponent(m_usernameTextField);
        formLayout.addComponent(m_passwordTextField);
        formLayout.addComponent(m_groupSelect);

        m_applyButton = new Button();
        m_applyButton.setCaption("Apply changes");
        m_applyButton.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                storeUserInfo();
            }
        });

        m_cancelButton = new Button();
        m_cancelButton.setEnabled(false);
        m_cancelButton.setCaption("Cancel");
        m_cancelButton.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                selectUser((UserDTO) m_userTable.getValue());
            }
        });

        HorizontalLayout addUserButtons = new HorizontalLayout();
        addUserButtons.setMargin(true, false, false, false);
        addUserButtons.setSpacing(true);
        addUserButtons.addComponent(m_applyButton);
        addUserButtons.addComponent(m_cancelButton);

        formLayout.addComponent(addUserButtons);

        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSizeFull();
        horizontalLayout.setSpacing(true);
        horizontalLayout.addComponent(usersList);
        horizontalLayout.addComponent(formLayout);

        horizontalLayout.setExpandRatio(usersList, 0.35f);
        horizontalLayout.setExpandRatio(formLayout, 0.65f);

        addComponent(horizontalLayout);

        updateState(null, false /* editAllowed */);
    }

    @Override
    public void attach() {
        try {
            populateUserTable();
            populateSelect();
        }
        finally {
            super.attach();
        }
    }

    public void open(Window parent) {
        parent.removeWindow(this);
        parent.addWindow(this);
        center();
    }

    /**
     * @return <code>true</code> if the current form is valid, <code>false</code> otherwise.
     */
    protected boolean isCurrentFormValid() {
        String username = (String) m_usernameTextField.getValue();
        if (username == null || "".equals(username.trim())) {
            return false;
        }
        String password = (String) m_passwordTextField.getValue();
        if (password == null || "".equals(password.trim())) {
            return false;
        }
        String groupName = (String) m_groupSelect.getValue();
        if (groupName == null || "".equals(groupName.trim())) {
            return false;
        }
        return true;
    }

    /**
     * Prepares everything for adding a new user.
     */
    protected void prepareForNewUser() {
        m_userTable.setValue(null);
        m_usernameTextField.focus();

        updateState(null, true /* editAllowed */);
    }

    /**
     * Removes the given user.
     */
    protected void removeSelectedUser(UserDTO user) {
        if (user == null) {
            return;
        }

        try {
            if (m_userTable.removeItem(user)) {
                m_userUtil.removeUser(user);

                showNotification(String.format("User '%s' removed!", user.getUsername()), Notification.TYPE_TRAY_NOTIFICATION);
            }
        }
        catch (UserNotFoundException e) {
            showNotification("Cannot store changes!", "<br>User not found, please refresh.", Notification.TYPE_ERROR_MESSAGE);
        }

        updateState(null, false /* editAllowed */);
    }

    /**
     * Called when the selected user is changed.
     * 
     * @param user
     *            the selected user, can be <code>null</code> in case no user is selected.
     */
    protected void selectUser(UserDTO user) {
        if (user != null) {
            m_usernameTextField.setValue(user.getUsername());
            m_passwordTextField.setValue(user.getPassword());
            m_groupSelect.setValue(user.getGroupname());
        }
        else {
            m_usernameTextField.setValue("");
            m_passwordTextField.setValue("");
            m_groupSelect.setValue(null);
        }

        updateState(user, user != null /* editAllowed */);

        m_cancelButton.setEnabled(false);
    }

    /**
     * Will be called by Felix DM when all dependencies become available.
     */
    protected void start(org.apache.felix.dm.Component component) {
        close();
    }

    /**
     * Will be called by Felix DM when a dependency isn't available.
     */
    protected void stop(org.apache.felix.dm.Component component) {
        close();
    }

    /**
     * 
     */
    protected void storeUserInfo() {
        try {
            String username = (String) m_usernameTextField.getValue();
            String password = (String) m_passwordTextField.getValue();
            String groupName = (String) m_groupSelect.getValue();

            String notification;
            Object itemID;

            UserDTO user = (UserDTO) m_userTable.getValue();
            if (user == null) {
                user = new UserDTO(username, password, groupName);

                m_userUtil.addUser(user);

                notification = String.format("User '%s' created!", user.getUsername());

                itemID = m_userTable.addItem(new Object[] { user }, user);
            }
            else {
                if (!groupName.equals(user.getGroupname())) {
                    user.setGroupname(groupName);
                }
                if (!username.equals(user.getUsername())) {
                    user.setUsername(username);
                }
                if (!password.equals(user.getPassword())) {
                    user.setPassword(password);
                }

                m_userUtil.updateUser(user);

                notification = String.format("User '%s' changed!", user.getUsername());

                itemID = user;
            }

            m_userTable.sort(new Object[] { "User" }, new boolean[] { true });
            m_userTable.setValue(itemID);

            showNotification(notification, Notification.TYPE_TRAY_NOTIFICATION);
            
            updateState(user, true /* editAllowed */);
        }
        catch (UserNotFoundException e) {
            showNotification("Cannot store changes!", "<br>User not found, please refresh.", Notification.TYPE_ERROR_MESSAGE);
            m_usernameTextField.focus();
        }
        catch (GroupNotFoundException e) {
            showNotification("Cannot store changes!", "<br>Group was not found.", Notification.TYPE_ERROR_MESSAGE);
            m_groupSelect.focus();
        }
        catch (UserAlreadyExistsException e) {
            showNotification("Cannot store changes!", "<br>Username already in use.", Notification.TYPE_ERROR_MESSAGE);
            m_usernameTextField.focus();
        }
    }

    private boolean isCurrentUser(UserDTO user) {
        if (user == null) {
            return false;
        }
        User currentUser = (User) getApplication().getUser();
        return currentUser.getProperties().get("username").equals(user.getUsername());
    }

    private void populateSelect() {
        List<Group> grouplist = m_userUtil.getGroups();
        for (Group g : grouplist) {
            m_groupSelect.addItem(g.getName());
        }
        m_userTable.sort(new Object[] { "User" }, new boolean[] { true });
    }

    /**
     * Inserts inital user data into the user table
     */
    private void populateUserTable() {
        m_userTable.removeAllItems();
        List<UserDTO> data = m_userUtil.getData();
        for (UserDTO userDTO : data) {
            m_userTable.addItem(new Object[] { userDTO }, userDTO);
        }
    }

    /**
     * @param user
     */
    private void updateState(UserDTO user, boolean editAllowed) {
        boolean userSelected = (user != null);
        m_applyButton.setEnabled(false);
        m_cancelButton.setEnabled(!userSelected && editAllowed);

        boolean currentUser = isCurrentUser(user);
        m_removeUserButton.setEnabled(userSelected && !currentUser);
        m_usernameTextField.setEnabled(editAllowed && !currentUser);
        m_passwordTextField.setEnabled(editAllowed);
        m_groupSelect.setEnabled(editAllowed && !currentUser);
    }
}
