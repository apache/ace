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

import static org.osgi.service.log.LogService.LOG_ERROR;
import static org.osgi.service.log.LogService.LOG_WARNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.ShortcutHelper;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.event.EventHandler;

import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.terminal.gwt.server.WebApplicationContext;
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

        public void buttonClick(ClickEvent event) {
            // Avoid double-clicks...
            event.getButton().setEnabled(false);

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
                showError("Changes not stored", "Failed to store the changes to the server.", e);
            }
        }

        public void onDialogResult(String buttonName) {
            if (ConfirmationDialog.YES.equals(buttonName)) {
                try {
                    logout();
                }
                catch (IOException e) {
                    showError("Warning", "There were errors during the logout procedure.", e);
                }
            }
        }

        /**
         * Does the actual logout of the user.
         *
         * @throws IOException
         *             in case of I/O problems during the logout.
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

        public void buttonClick(ClickEvent event) {
            // Avoid double-clicks...
            event.getButton().setEnabled(false);

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

        private void handleIOException(IOException e) {
            showError("Retrieve failed", "Failed to retrieve the data from the server.", e);
        }

        /**
         * Does the actual retrieval of the latest version.
         *
         * @throws IOException
         *             in case of I/O problems during the retrieve.
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

        public void buttonClick(ClickEvent event) {
            // Avoid double-clicks...
            event.getButton().setEnabled(false);

            try {
                if (getRepositoryAdmin().isModified()) {
                    // Revert all changes...
                    getWindow().addWindow(
                        new ConfirmationDialog("Revert changes?",
                            "Are you sure you want to overwrite all local changes?", this));
                }
                else {
                    // Nothing to revert...
                    showWarning("Nothing to revert", "There are no local changes that need to be reverted.");
                }
            }
            catch (IOException e) {
                handleIOException(e);
            }
        }

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

        private void handleIOException(IOException e) {
            showError("Revert failed", "Failed to revert your changes.", e);
        }

        /**
         * Does the actual revert of changes.
         *
         * @throws IOException
         *             in case of problems during I/O exception.
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

        public void buttonClick(ClickEvent event) {
            // Avoid double-clicks...
            event.getButton().setEnabled(false);

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
                    showWarning("Nothing to store", "There are no changes that can be stored to the repository.");
                }
            }
            catch (IOException e) {
                showError("Changes not stored", "Failed to store the changes to the server.", e);
            }
        }

        /**
         * Does the actual commit of changes.
         *
         * @throws IOException
         *             in case of I/O problems during the commit.
         */
        private void commitChanges() throws IOException {
            getRepositoryAdmin().commit();
            doAfterCommit();
        }
    }

    private final ConcurrentMap<ServiceReference<UIExtensionFactory>, UIExtensionFactory> m_extensions;
    private final boolean m_showLogoutButton;

    private Button m_retrieveButton;
    private Button m_storeButton;
    private Button m_revertButton;
    private Button m_logoutButton;

    private HorizontalLayout m_extraComponentBar;

    /**
     * Creates a new {@link MainActionToolbar} instance.
     *
     * @param user
     * @param manager
     *
     * @param showLogoutButton
     *            <code>true</code> if a logout button should be shown, <code>false</code> if it should not.
     */
    public MainActionToolbar(boolean showLogoutButton) {
        super(6, 1);

        m_extensions = new ConcurrentHashMap<>();
        m_showLogoutButton = showLogoutButton;

        setWidth("100%");
        setSpacing(true);

        initComponent();
    }

    @Override
    public void attach() {
        try {
            addCrossPlatformShortcut(m_retrieveButton, KeyCode.G, "Retrieves the latest changes from the server");
            addCrossPlatformShortcut(m_storeButton, KeyCode.S, "Stores all local changes");
            addCrossPlatformShortcut(m_revertButton, KeyCode.U, "Reverts all local changes");

            if (m_showLogoutButton) {
                addCrossPlatformShortcut(m_logoutButton, KeyCode.L, "Log out");
            }
        }
        finally {
            super.attach();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void handleEvent(org.osgi.service.event.Event event) {
        boolean modified = false;
        try {
            modified = getRepositoryAdmin().isModified();
        }
        catch (IOException e) {
            showError("Communication failed!", "Failed to communicate with the server.", e);
        }

        // always enabled...
        m_retrieveButton.setEnabled(true);
        // only enabled when an actual change has been made...
        m_storeButton.setEnabled(modified);
        m_revertButton.setEnabled(modified);
    }

    protected final void add(ServiceReference<UIExtensionFactory> ref, UIExtensionFactory factory) {
        m_extensions.put(ref, factory);
        setExtraComponents();
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

    protected final List<Component> getExtraComponents() {
        // create a shapshot of the current extensions...
        Map<ServiceReference<UIExtensionFactory>, UIExtensionFactory> extensions = new HashMap<>(m_extensions);

        // Make sure we've got a predictable order of the components...
        List<ServiceReference<UIExtensionFactory>> refs = new ArrayList<>(extensions.keySet());
        Collections.sort(refs);

        Map<String, Object> context = new HashMap<>();

        List<Component> result = new ArrayList<>();
        for (ServiceReference<UIExtensionFactory> ref : refs) {
            UIExtensionFactory factory = extensions.get(ref);

            result.add(factory.create(context));
        }
        return result;
    }

    /**
     * @return a repository admin instance, never <code>null</code>.
     */
    protected abstract RepositoryAdmin getRepositoryAdmin();

    /**
     * Called by Felix DM when initializing this component.
     */
    protected void init(org.apache.felix.dm.Component component) {
        DependencyManager dm = component.getDependencyManager();
        component.add(dm.createServiceDependency()
            .setService(UIExtensionFactory.class, "(" + UIExtensionFactory.EXTENSION_POINT_KEY + "=" + UIExtensionFactory.EXTENSION_POINT_VALUE_MENU + ")")
            .setCallbacks("add", "remove")
            .setRequired(false));
    }

    protected final void remove(ServiceReference<UIExtensionFactory> ref, UIExtensionFactory factory) {
        m_extensions.remove(ref);
        setExtraComponents();
    }

    protected void showError(String title, String message, Exception e) {
        StringBuilder sb = new StringBuilder("<br/>");
        sb.append(message);
        if (e.getMessage() != null) {
            sb.append("<br/>").append(e.getMessage());
        }
        else {
            sb.append("<br/>unknown error!");
        }
        log(LOG_ERROR, message, e);
        getWindow().showNotification(title, sb.toString(), Notification.TYPE_ERROR_MESSAGE);
    }

    protected void showWarning(String title, String message) {
        log(LOG_WARNING, message);
        getWindow().showNotification(title, String.format("<br/>%s", message), Notification.TYPE_WARNING_MESSAGE);
    }

    protected final void log(int level, String msg, Object... args) {
        log(level, msg, null, args);
    }

    protected abstract void log(int level, String msg, Exception e, Object... args);

    private void addCrossPlatformShortcut(Button button, int key, String description) {
        // ACE-427 - NPE when using getMainWindow() if no authentication is used...
        WebApplicationContext context = (WebApplicationContext) getApplication().getContext();
        ShortcutHelper.addCrossPlatformShortcut(context.getBrowser(), button, description, key);
    }

    /**
     * Initializes this component.
     */
    private void initComponent() {
        m_retrieveButton = new Button("Retrieve");
        m_retrieveButton.setEnabled(false);
        m_retrieveButton.addListener(new RetrieveButtonListener());
        addComponent(m_retrieveButton, 0, 0);

        m_storeButton = new Button("Store");
        m_storeButton.setEnabled(false);
        m_storeButton.addListener(new StoreButtonListener());
        addComponent(m_storeButton, 1, 0);

        m_revertButton = new Button("Revert");
        m_revertButton.setEnabled(false);
        m_revertButton.addListener(new RevertButtonListener());
        addComponent(m_revertButton, 2, 0);

        Label spacer = new Label("");
        spacer.setWidth("2em");
        addComponent(spacer, 3, 0);

        m_extraComponentBar = new HorizontalLayout();
        m_extraComponentBar.setSpacing(true);

        addComponent(m_extraComponentBar, 4, 0);

        m_logoutButton = new Button("Logout");
        m_logoutButton.addListener(new LogoutButtonListener());
        m_logoutButton.setVisible(m_showLogoutButton);

        addComponent(m_logoutButton, 5, 0);

        // Ensure the spacer gets all the excessive room, causing the logout
        // button to appear at the right side of the screen....
        setColumnExpandRatio(3, 5);
    }

    private void setExtraComponents() {
        m_extraComponentBar.removeAllComponents();
        for (Component c : getExtraComponents()) {
            m_extraComponentBar.addComponent(c);
        }
    }
}
