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
package org.apache.ace.webui.vaadin.component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventHandler;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Window.Notification;

/**
 * Provides the main actions toolbar where one can commit, revert or retrieve changes.
 */
public abstract class MainActionToolbar extends GridLayout implements EventHandler {

    /**
     * Provides a button listener for the logout button.
     */
    private class LogoutButtonListener implements Button.ClickListener, ConfirmationDialog.Callback {

        /**
         * {@inheritDoc}
         */
        public void buttonClick(ClickEvent event) {
            final RepositoryAdmin repoAdmin = getRepositoryAdmin();
            try {
                if (repoAdmin.isModified() && repoAdmin.isCurrent()) {
                    getWindow().addWindow(
                        new ConfirmationDialog("Revert changes?",
                            "The repository is changed. Are you sure you want to loose all local changes?", this));
                }
                else {
                    logout();
                }
            }
            catch (IOException e) {
                getWindow().showNotification("Changes not stored",
                    "Failed to store the changes to the server.<br />Reason: " + e.getMessage(),
                    Notification.TYPE_ERROR_MESSAGE);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void onDialogResult(String buttonName) {
            if (ConfirmationDialog.YES.equals(buttonName)) {
                try {
                    logout();
                }
                catch (IOException e) {
                    handleIOException(e);
                }
            }
        }

        /**
         * @param e the exception to handle.
         */
        private void handleIOException(IOException e) {
            getWindow().showNotification("Warning",
                "There were errors during the logout procedure.<br />Reason: " + e.getMessage(),
                Notification.TYPE_ERROR_MESSAGE);
        }

        /**
         * Does the actual logout of the user.
         * 
         * @throws IOException in case of I/O problems during the logout.
         */
        private void logout() throws IOException {
            getRepositoryAdmin().logout(true /* force */);
            doAfterLogout();
        }
    }

    /**
     * Provides a button listener for the retrieve button.
     */
    private final class RetrieveButtonListener implements Button.ClickListener, ConfirmationDialog.Callback {

        /**
         * {@inheritDoc}
         */
        public void buttonClick(ClickEvent event) {
            final RepositoryAdmin repoAdmin = getRepositoryAdmin();
            try {
                if (repoAdmin.isModified()) {
                    // Warn the user about the possible loss of changes...
                    getWindow().addWindow(
                        new ConfirmationDialog("Retrieve latest changes?",
                            "The repository is changed. Are you sure you want to loose all local changes?", this));
                }
                else {
                    retrieveData();
                }
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void onDialogResult(String buttonName) {
            if (ConfirmationDialog.YES.equals(buttonName)) {
                try {
                    retrieveData();
                }
                catch (IOException e) {
                    handleIOException(e);
                }
            }
        }

        /**
         * @param e the exception to handle.
         */
        private void handleIOException(IOException e) {
            getWindow().showNotification("Retrieve failed",
                "Failed to retrieve the data from the server.<br />Reason: " + e.getMessage(),
                Notification.TYPE_ERROR_MESSAGE);
        }

        /**
         * Does the actual retrieval of the latest version.
         * 
         * @throws IOException in case of I/O problems during the retrieve.
         */
        private void retrieveData() throws IOException {
            getRepositoryAdmin().checkout();
            doAfterRetrieve();
        }
    }

    /**
     * Provides a button listener for the revert button.
     */
    private final class RevertButtonListener implements Button.ClickListener, ConfirmationDialog.Callback {

        /**
         * {@inheritDoc}
         */
        public void buttonClick(ClickEvent event) {
            try {
                if (getRepositoryAdmin().isModified()) {
                    // Revert all changes...
                    getWindow().addWindow(
                        new ConfirmationDialog("Revert changes?",
                            "Are you sure you want to overwrite all local changes?", this));
                }
                else {
                    // Nothing to revert...
                    getWindow().showNotification("Nothing to revert",
                        "There are no local changes that need to be reverted.", Notification.TYPE_WARNING_MESSAGE);
                }
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void onDialogResult(String buttonName) {
            if (ConfirmationDialog.YES.equals(buttonName)) {
                try {
                    revertChanges();
                }
                catch (IOException e) {
                    handleIOException(e);
                }
            }
        }

        /**
         * @param e the exception to handle.
         */
        private void handleIOException(IOException e) {
            getWindow().showNotification("Revert failed",
                "Failed to revert your changes.<br />Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
        }

        /**
         * Does the actual revert of changes.
         * 
         * @throws IOException in case of problems during I/O exception.
         */
        private void revertChanges() throws IOException {
            getRepositoryAdmin().revert();
            doAfterRevert();
        }
    }

    /**
     * Provides a button listener for the store button.
     */
    private final class StoreButtonListener implements Button.ClickListener {

        /**
         * {@inheritDoc}
         */
        public void buttonClick(ClickEvent event) {
            final RepositoryAdmin repoAdmin = getRepositoryAdmin();
            try {
                if (repoAdmin.isModified()) {
                    if (repoAdmin.isCurrent()) {
                        commitChanges();
                    }
                    else {
                        getWindow().showNotification("Changes not stored",
                            "Unable to store your changes; repository changed!", Notification.TYPE_WARNING_MESSAGE);
                    }
                }
                else {
                    getWindow()
                        .showNotification("Nothing to store",
                            "There are no changes that can be stored to the repository.",
                            Notification.TYPE_WARNING_MESSAGE);
                }
            }
            catch (IOException e) {
                getWindow().showNotification("Changes not stored",
                    "Failed to store the changes to the server.<br />Reason: " + e.getMessage(),
                    Notification.TYPE_ERROR_MESSAGE);
            }
        }

        /**
         * Does the actual commit of changes.
         * 
         * @throws IOException in case of I/O problems during the commit.
         */
        private void commitChanges() throws IOException {
            getRepositoryAdmin().commit();
            doAfterCommit();
        }
    }

    private final boolean m_showLogoutButton;

    private Button m_retrieveButton;
    private Button m_storeButton;
    private Button m_revertButton;
    private Button m_logoutButton;

    private final DependencyManager m_manager;
    private final ConcurrentHashMap<ServiceReference, UIExtensionFactory> m_extensions = new ConcurrentHashMap<ServiceReference, UIExtensionFactory>();

    /**
     * Creates a new {@link MainActionToolbar} instance.
     * @param manager 
     * 
     * @param showLogoutButton <code>true</code> if a logout button should be shown, <code>false</code> if it should not.
     */
    public MainActionToolbar(DependencyManager manager, boolean showLogoutButton) {
        super(5, 1);
        m_manager = manager;

        m_showLogoutButton = showLogoutButton;

        setWidth("100%");
        setSpacing(true);
        
        m_manager.add(m_manager.createComponent()
            .setImplementation(this)
            .add(m_manager.createServiceDependency()
                .setService(UIExtensionFactory.class, "(" + UIExtensionFactory.EXTENSION_POINT_KEY + "=" + UIExtensionFactory.EXTENSION_POINT_VALUE_MENU + ")")
                .setCallbacks("add", "remove")
            )
        );

        initComponent();
    }
    
    public void add(ServiceReference ref, UIExtensionFactory factory) {
        m_extensions.put(ref, factory);
    }
    
    public void remove(ServiceReference ref,  UIExtensionFactory factory) {
        m_extensions.remove(ref);
    }

    /**
     * {@inheritDoc}
     */
    public void handleEvent(org.osgi.service.event.Event event) {
        String topic = event.getTopic();
        if (RepositoryAdmin.TOPIC_STATUSCHANGED.equals(topic) || RepositoryAdmin.TOPIC_REFRESH.equals(topic)
            || RepositoryAdmin.TOPIC_LOGIN.equals(topic)) {

            boolean modified = false;
            try {
                modified = getRepositoryAdmin().isModified();
            }
            catch (IOException e) {
                getWindow().showNotification("Communication failed!",
                    "Failed to communicate with the server.<br />Reason: " + e.getMessage(),
                    Notification.TYPE_ERROR_MESSAGE);
            }

            m_storeButton.setEnabled(modified);
            m_revertButton.setEnabled(modified);
        }
    }

    /**
     * Called after a commit/store has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterCommit() throws IOException;

    /**
     * Called after a logout has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterLogout() throws IOException;

    /**
     * Called after a retrieve has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterRetrieve() throws IOException;

    /**
     * Called after a revert has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterRevert() throws IOException;

    /**
     * @return a repository admin instance, never <code>null</code>.
     */
    protected abstract RepositoryAdmin getRepositoryAdmin();

    /**
     * Initializes this component.
     */
    private void initComponent() {
        m_retrieveButton = new Button("Retrieve");
        m_retrieveButton.addListener(new RetrieveButtonListener());
        addComponent(m_retrieveButton, 0, 0);

        m_storeButton = new Button("Store");
        m_storeButton.addListener(new StoreButtonListener());
        addComponent(m_storeButton, 1, 0);

        m_revertButton = new Button("Revert");
        m_revertButton.addListener(new RevertButtonListener());
        addComponent(m_revertButton, 2, 0);

        HorizontalLayout bar = new HorizontalLayout();
        Label spacer = new Label("");
        spacer.setWidth("2em");
        bar.addComponent(spacer);
        for (Component c : getExtraComponents()) {
            bar.addComponent(c);
        }
        addComponent(bar, 3, 0);

        m_logoutButton = new Button("Logout");
        m_logoutButton.addListener(new LogoutButtonListener());
        if (m_showLogoutButton) {
            addComponent(m_logoutButton, 4, 0);
        }

        // Ensure the spacer gets all the excessive room, causing the logout
        // button to appear at the right side of the screen....
        setColumnExpandRatio(3, 5);
    }
    
    protected List<Component> getExtraComponents() {
        List<Component> result = new ArrayList<Component>();
        for (UIExtensionFactory f : m_extensions.values()) {
            result.add(f.create(Collections.EMPTY_MAP));
        }
        return result;
    }
}
