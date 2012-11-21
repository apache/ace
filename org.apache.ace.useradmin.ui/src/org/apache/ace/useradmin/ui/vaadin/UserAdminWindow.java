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
import org.apache.ace.useradmin.ui.editor.UserNotFoundException;
import org.apache.ace.useradmin.ui.editor.UserEditor;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.User;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.Select;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * UserAdminWindow is the user management window which handles changes made to ACE users. It provides an option to
 * perform CRUD operations on ACE users. The changes are made by calls to userUtilImpl which calls Felix's UserAdmin.
 * 
 */
@SuppressWarnings("serial")
public class UserAdminWindow extends Window {

    private final Table m_userTable;
    private final Select m_groupSelect;
    private final TextField m_usernameTextField;
    private final PasswordField m_passwordTextField;
    private final Button m_addNewUserButton;
    private final Button m_cancelNewUserButton;
    private final Button m_applyButton;
    private final Button m_removeUserButton;
    private ItemClickListener m_itemClickListener;
    private UserDTO m_userDTO;
    private volatile UserEditor m_userUtil;
    private final User m_currentUser;
    private boolean adminMode;

    public UserAdminWindow(User currentUser) {
        m_userTable = new Table();
        m_groupSelect = new Select();
        m_usernameTextField = new TextField();
        m_passwordTextField = new PasswordField();
        m_addNewUserButton = new Button();
        m_cancelNewUserButton = new Button();
        m_applyButton = new Button();
        m_removeUserButton = new Button();
        m_currentUser = currentUser;
    }

    public void init() {
        setCaption("Manage users");
        if (m_userUtil.hasRole(m_currentUser, "editUsers")) {
            adminMode = true;
            getLayout().setSizeFull();
            addComponent(createAdminWindowLayout());
            populateUserTableAndSelect();
        }
        else {
            setCaption("My info");
            addComponent(createUserWindowLayout());
            populateSelect();
            showApplyButton();
            initializeUserDTO();
        }
    }

    /**
     * Will be called when a dependency isn't available
     */
    public void destroy() {
        if (adminMode) {
            getApplication().getMainWindow().showNotification("Oops", "Manage Users function has been disabled", Notification.TYPE_ERROR_MESSAGE);
        }
        else {
            getApplication().getMainWindow().showNotification("Oops", "My info function has been disabled", Notification.TYPE_ERROR_MESSAGE);
        }
        close();
        getApplication().removeWindow(this);
    }

    private void initializeUserDTO() {
        m_userDTO = new UserDTO((String) m_currentUser.getProperties().get("username"), (String) m_currentUser.getCredentials().get("password"), m_userUtil.getGroup(m_currentUser).getName());
        m_usernameTextField.setValue(m_userDTO.getUsername());
        m_passwordTextField.setValue(m_userDTO.getPassword());
        m_groupSelect.setValue(m_userDTO.getGroupname());
        disableUsernameAndGroup();
    }

    /**
     * Creates a new Layout containing the user table and edit form
     * 
     * @returns the manage users window
     */
    private HorizontalLayout createAdminWindowLayout() {
        getWindow().setWidth("40%");
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSizeFull();
        horizontalLayout.addComponent(createFirstColumn());
        horizontalLayout.addComponent(createFormLayout());
        return horizontalLayout;
    }

    /**
     * Create a new Layout containing with only the information of the logged user
     * 
     * @return
     */
    private HorizontalLayout createUserWindowLayout() {
        getWindow().setHeight("25%");
        getWindow().setWidth("20%");
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.addComponent(createFormLayout());
        return horizontalLayout;
    }

    /**
     * Creates the left column containing the user table
     * 
     * @returns the user table column
     */
    private VerticalLayout createFirstColumn() {
        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.setSizeFull();
        verticalLayout.setMargin(false, true, false, false);
        Table table = initTable();
        verticalLayout.addComponent(table);
        HorizontalLayout buttons = createHorizontalButtonLayout();
        verticalLayout.addComponent(buttons);
        verticalLayout.setExpandRatio(table, 1.0f);
        verticalLayout.setExpandRatio(buttons, 0.0f);
        return verticalLayout;
    }

    /**
     * Creates the form containing fields to edit user data
     * 
     * @returns the user edit fields
     */
    private FormLayout createFormLayout() {
        FormLayout formLayout = new FormLayout();
        formLayout.setMargin(false, false, false, true);
        formLayout.addComponent(initUsernameTextField());
        formLayout.addComponent(initPasswordField());
        formLayout.addComponent(initSelect());
        HorizontalLayout addUserButtons = new HorizontalLayout();
        addUserButtons.addComponent(initApplyButton());
        Button initializeAddNewUserButton = initializeAddNewUserButton();
        addUserButtons.addComponent(initializeAddNewUserButton);
        // addUserButtons.setComponentAlignment(initializeAddNewUserButton, Alignment.MIDDLE_CENTER);
        addUserButtons.setSpacing(true);
        addUserButtons.addComponent(initCancelNewUserButton());

        formLayout.addComponent(addUserButtons);
        hideNewUserButtons();
        disableTextFieldsAndSelect();
        return formLayout;
    }

    /**
     * Creates a Layout containing the + and - button for addition and removal of users
     * 
     * @returns the button layout containing + and - buttons
     */
    private HorizontalLayout createHorizontalButtonLayout() {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setMargin(true, false, true, false);
        horizontalLayout.setSpacing(true);
        horizontalLayout.addComponent(createAddButton());
        horizontalLayout.addComponent(createRemoveButton());
        return horizontalLayout;
    }

    /**
     * Initializes the user table
     * 
     * @returns the usertable
     */
    private Table initTable() {
        m_userTable.setSizeFull();
        m_userTable.setSelectable(true);
        m_userTable.setPageLength(0);
        m_userTable.addContainerProperty("User", UserDTO.class, null);
        m_userTable.setSortDisabled(false);
        m_userTable.addListener(createUserTableSelectListener());
        return m_userTable;
    }

    /**
     * Creates a ClickListener to update the table selection
     * 
     * @returns a user table selection listener
     */
    private ItemClickListener createUserTableSelectListener() {
        m_itemClickListener = new ItemClickListener() {

            @Override
            public void itemClick(ItemClickEvent event) {
                enableTextFieldsAndSelect();
                hideNewUserButtons();
                showApplyButton();
                UserDTO user = (UserDTO) event.getItem().getItemProperty("User").getValue();
                m_userTable.select(user);
                m_userDTO = new UserDTO(user.getUsername(), user.getPassword(), user.getGroupname());
                m_usernameTextField.setValue(user.getUsername());
                m_passwordTextField.setValue(user.getPassword());
                m_groupSelect.setValue(user.getGroupname());
                checkSameUser(m_userDTO.getUsername());
            }
        };
        return m_itemClickListener;
    }

    /**
     * Inserts inital user data into the user table
     */
    private void populateUserTableAndSelect() {
        m_userTable.removeAllItems();
        List<UserDTO> data = m_userUtil.getData();
        for (UserDTO userDTO : data) {
            m_userTable.addItem(new Object[] { userDTO }, userDTO);
        }
        populateSelect();
    }

    private void populateSelect() {
        List<Group> grouplist = m_userUtil.getGroups();
        for (Group g : grouplist) {
            m_groupSelect.addItem(g.getName());
        }
        m_userTable.sort(new Object[] { "User" }, new boolean[] { true });
    }

    /**
     * Creates the + button for creation of new users
     * 
     * @returns the + button
     */
    private Button createAddButton() {
        Button b = new Button("+");
        b.setWidth("4em");
        b.addListener(new ClickListener() {

            @Override
            public void buttonClick(final ClickEvent event) {
                enableTextFieldsAndSelect();
                m_usernameTextField.setValue("");
                m_passwordTextField.setValue("");
                m_groupSelect.setValue(null);
                m_userTable.select(null);
                m_usernameTextField.focus();
                showNewUserButtons();
            }
        });
        return b;
    }

    /**
     * Initializes the - button for removal of existing users
     * 
     * @returns the - button
     */
    private Button createRemoveButton() {
        m_removeUserButton.setCaption("-");
        m_removeUserButton.setWidth("4em");
        m_removeUserButton.addListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                try {
                    if (m_userTable.getValue() == null) {
                        return;
                    }
                    m_userUtil.removeUser(m_userDTO);
                    m_userTable.removeItem(m_userTable.getValue());
                    m_usernameTextField.setValue("");
                    m_passwordTextField.setValue("");
                    m_groupSelect.select(null);
                    disableTextFieldsAndSelect();
                }
                catch (UserNotFoundException e) {
                    showUserNotFoundWarning();
                }
            }
        });
        return m_removeUserButton;
    }

    /**
     * Initializes the apply button to save changes after editing
     * 
     * @returns the apply button
     */
    private Button initApplyButton() {
        m_applyButton.setCaption("Apply");
        m_applyButton.addListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                String usernameTextFieldInput = (String) m_usernameTextField.getValue();
                if (usernameTextFieldInput == null || "".equals(usernameTextFieldInput)) {
                    getWindow().showNotification("Username cannot be empty", Window.Notification.TYPE_WARNING_MESSAGE);
                    return;
                }
                String groupName = (String) m_groupSelect.getValue();
                if (groupName == null || "".equals(groupName)) {

                    getWindow().showNotification("Role cannot be empty", Window.Notification.TYPE_WARNING_MESSAGE);
                    return;
                }
                String passwordTextFieldInput = (String) m_passwordTextField.getValue();
                if (passwordTextFieldInput == null || "".equals(passwordTextFieldInput)) {
                    getWindow().showNotification("Password cannot be empty", Window.Notification.TYPE_WARNING_MESSAGE);
                    return;
                }
                try {

                    if (m_userDTO.isUpdated()) {
                        m_userUtil.storeUserDTO(m_userDTO);
                        if (adminMode) {
                            UserDTO userDTO = new UserDTO(m_userDTO.getUsername(), m_userDTO.getPassword(), m_userDTO.getGroupname());
                            m_userTable.removeItem(m_userTable.getValue());
                            m_userTable.addItem(new Object[] { userDTO }, userDTO);
                            m_userTable.sort(new Object[] { "User" }, new boolean[] { true });
                            m_itemClickListener.itemClick(new ItemClickEvent((Component) event.getSource(), m_userTable.getItem(userDTO), null, null, null));
                        }
                        getWindow().showNotification("User updated");

                    }
                }
                catch (UserNotFoundException e) {
                    showUserNotFoundWarning();
                }
                catch (GroupNotFoundException e) {
                    showGroupNotFoundWarning();
                }
                catch (UserAlreadyExistsException e) {
                    showUserAlreadyExistsWarning();
                }
            }
        });
        return m_applyButton;
    }

    /**
     * Initializes the cancel button which cancels the addition of a new user
     * 
     * @returns the cancel button
     */
    private Button initCancelNewUserButton() {
        m_cancelNewUserButton.setCaption("Cancel");
        m_cancelNewUserButton.addListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                hideNewUserButtons();
                m_userTable.select(null);
                disableTextFieldsAndSelect();
            }
        });
        return m_cancelNewUserButton;
    }

    /**
     * Initializes the add button which checks if the input is valid, and then saves a new user to UserAdmin
     * 
     * @returns the add button
     */
    private Button initializeAddNewUserButton() {
        m_addNewUserButton.setCaption("Add");
        m_addNewUserButton.setWidth("5em");
        m_addNewUserButton.addListener(new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {
                String username = (String) m_usernameTextField.getValue();
                String password = (String) m_passwordTextField.getValue();
                String group = (String) m_groupSelect.getValue();
                if (username == null || "".equals(username) || password == null || "".equals(password) || group == null || "".equals(group)) {
                    showEmptyInputWarning();
                }
                else {
                    try {
                        UserDTO userDTO = new UserDTO(username, password, group);
                        m_userUtil.addUser(userDTO);
                        m_userTable.addItem(new Object[] { userDTO }, userDTO);
                        m_userTable.sort(new Object[] { "User" }, new boolean[] { true });
                        m_userTable.select(username);
                        m_itemClickListener.itemClick(new ItemClickEvent((Component) event.getSource(), m_userTable.getItem(userDTO), null, null, null));
                    }
                    catch (GroupNotFoundException e) {
                        showGroupNotFoundWarning();
                    }
                    catch (UserAlreadyExistsException e) {
                        showUserAlreadyExistsWarning();
                    }
                }
            }
        });
        return m_addNewUserButton;
    }

    /**
     * Initializes the username text field
     * 
     * @returns the username text field
     */
    private TextField initUsernameTextField() {
        m_usernameTextField.setCaption("Username: ");
        m_usernameTextField.setImmediate(true);
        m_usernameTextField.addListener(new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                UserDTO user = (UserDTO) m_userTable.getValue();
                if (user == null) {
                    return;
                }
                if (m_userUtil.getUser(user.getUsername()) == null) {
                    return;
                }
                String usernameTextFieldInput = (String) m_usernameTextField.getValue();
                if (user.getUsername().equals(usernameTextFieldInput)) {
                    if (!m_userDTO.getUsername().equals(usernameTextFieldInput)) {
                        m_userDTO.setUsername(usernameTextFieldInput);
                        m_userDTO.setUsernameChanged(false);
                    }
                    return;
                }
                m_userDTO.setUsername(usernameTextFieldInput);
            }
        });
        return m_usernameTextField;
    }

    /**
     * Initializes the password field
     * 
     * @returns the password field
     */
    private PasswordField initPasswordField() {
        m_passwordTextField.setCaption("Password: ");
        m_passwordTextField.setImmediate(true);
        m_passwordTextField.addListener(new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                UserDTO user = m_userDTO;
                if (user == null) {
                    return;
                }
                User result = m_userUtil.getUser(user.getPreviousUsername());
                if (result == null) {
                    return;
                }
                String password = (String) result.getCredentials().get("password");
                String passwordTextFieldInput = (String) m_passwordTextField.getValue();
                if (password.equals(passwordTextFieldInput)) {
                    if (!m_userDTO.getPassword().equals(passwordTextFieldInput)) {
                        m_userDTO.setPassword(passwordTextFieldInput);
                        m_userDTO.setPasswordChanged(false);
                    }
                    return;
                }
                m_userDTO.setPassword(passwordTextFieldInput);
            }
        });
        return m_passwordTextField;
    }

    /**
     * Initializes the group select box
     * 
     * @returns the group select
     */
    private Select initSelect() {
        m_groupSelect.setCaption("Role: ");
        m_groupSelect.setImmediate(true);
        m_groupSelect.setSizeFull();
        m_groupSelect.setNullSelectionAllowed(false);
        m_groupSelect.addListener(new ValueChangeListener() {

            @Override
            public void valueChange(ValueChangeEvent event) {
                UserDTO user = (UserDTO) m_userTable.getValue();
                if (user == null) {
                    return;
                }
                User result = m_userUtil.getUser(user.getUsername());
                if (result == null) {
                    return;
                }
                Group group = m_userUtil.getGroup(result);
                String groupName = (String) m_groupSelect.getValue();
                if (group.getName().equals(groupName)) {
                    if (!m_userDTO.getGroupname().equals(groupName)) {
                        m_userDTO.setGroupname(groupName);
                        m_userDTO.setGroupChanged(false);
                    }
                    return;
                }
                m_userDTO.setGroupname(groupName);
            }
        });
        return m_groupSelect;
    }

    /**
     * Enables the username, password and group select form
     */
    private void enableTextFieldsAndSelect() {
        m_usernameTextField.setEnabled(true);
        m_passwordTextField.setEnabled(true);
        m_groupSelect.setEnabled(true);
        m_removeUserButton.setEnabled(true);
    }

    /**
     * Disables the username, password and group select form
     */
    private void disableTextFieldsAndSelect() {
        m_usernameTextField.setEnabled(false);
        m_passwordTextField.setEnabled(false);
        m_groupSelect.setEnabled(false);
        m_removeUserButton.setEnabled(false);
        hideApplyButton();
    }

    private void disableUsernameAndGroup() {
        m_usernameTextField.setEnabled(false);
        m_passwordTextField.setEnabled(true);
        m_groupSelect.setEnabled(false);
        m_removeUserButton.setEnabled(false);
    }

    private void checkSameUser(String currentUser) {
        if (m_currentUser.getProperties().get("username").equals(m_userUtil.getUser(currentUser).getProperties().get("username"))) {
            disableUsernameAndGroup();
        }
        else {
            enableTextFieldsAndSelect();
        }
    }

    /**
     * Shows the add and cancel buttons when adding a new user
     */
    private void showNewUserButtons() {
        m_addNewUserButton.setVisible(true);
        m_cancelNewUserButton.setVisible(true);
        m_usernameTextField.setValue("");
        m_passwordTextField.setValue("");
        m_groupSelect.setValue(null);
        m_removeUserButton.setEnabled(false);
        hideApplyButton();
    }

    /**
     * Hides the add and cancel buttons when adding a new user
     */
    private void hideNewUserButtons() {
        m_addNewUserButton.setVisible(false);
        m_cancelNewUserButton.setVisible(false);
        m_usernameTextField.setValue("");
        m_passwordTextField.setValue("");
        m_groupSelect.setValue(null);
    }

    private void showApplyButton() {
        m_applyButton.setVisible(true);
    }

    private void hideApplyButton() {
        m_applyButton.setVisible(false);
    }

    /**
     * Displays an error when the requested user is not found
     */
    private void showUserNotFoundWarning() {
        getWindow().showNotification("Oops:", "<br>User not found, please refresh</br>", Window.Notification.TYPE_WARNING_MESSAGE);
    }

    /**
     * Displays an error that the given username is already in use
     */
    private void showUserAlreadyExistsWarning() {
        getWindow().showNotification("Oops:", "<br>Username already in use</br>", Window.Notification.TYPE_WARNING_MESSAGE);
    }

    /**
     * Displays an error that the selected group is not found
     */
    private void showGroupNotFoundWarning() {
        getWindow().showNotification("Error:", "<br>Group not found, please refresh</br>", Window.Notification.TYPE_WARNING_MESSAGE);
    }

    /**
     * Displays an error message regarding empty input
     */
    private void showEmptyInputWarning() {
        getWindow().showNotification("Oops:", "<br>Username, password & group cannot be empty</br>", Window.Notification.TYPE_WARNING_MESSAGE);
    }
}
