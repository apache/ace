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

import org.apache.ace.client.repository.RepositoryAdmin;
import org.osgi.service.event.EventHandler;

import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Window.Notification;

/**
 * Provides the main actions toolbar where one can commit, revert or retrieve changes.
 */
public abstract class MainActionToolbar extends GridLayout implements EventHandler {

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
                    getWindow().addWindow(new ConfirmationDialog("Revert changes?",
                        "Are you sure you want to overwrite all local changes?", this));
                }
                else {
                    // Nothing to revert...
                    getWindow().showNotification("Nothing to revert",
                        "There are no local changes that need to be reverted.",
                        Notification.TYPE_WARNING_MESSAGE);
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
         * Does the actual revert of changes.
         * 
         * @throws IOException in case of problems during I/O exception.
         */
        private void revertChanges() throws IOException {
            getRepositoryAdmin().revert();
            doAfterRevert();
        }

        /**
         * @param e the exception to handle.
         */
        private void handleIOException(IOException e) {
            getWindow().showNotification("Revert failed",
                "Failed to revert your changes.<br />" + "Reason: " + e.getMessage(),
                Notification.TYPE_ERROR_MESSAGE);
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
                    getWindow().showNotification("Nothing to store",
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
         * Does the actual retrieval of the latest version.
         * 
         * @throws IOException in case of I/O problems during the retrieve.
         */
        private void retrieveData() throws IOException {
            getRepositoryAdmin().checkout();
            doAfterRetrieve();
        }

        /**
         * @param e the exception to handle.
         */
        private void handleIOException(IOException e) {
            getWindow().showNotification("Retrieve failed", "Failed to retrieve the data from the server.<br />" +
                "Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
        }
    }

    private Button m_retrieveButton;
    private Button m_storeButton;
    private Button m_revertButton;

    /**
     * Creates a new {@link MainActionToolbar} instance.
     */
    public MainActionToolbar() {
        super(3, 1);

        setSpacing(true);

        initComponent();
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
                getWindow().showNotification("Communication failed!", "Failed to communicate with the server.<br />" +
                    "Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
            }

            m_storeButton.setEnabled(modified);
            m_revertButton.setEnabled(modified);
        }
    }

    /**
     * @return a repository admin instance, never <code>null</code>.
     */
    protected abstract RepositoryAdmin getRepositoryAdmin();

    /**
     * Called after a revert has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterRevert() throws IOException;

    /**
     * Called after a commit/store has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterCommit() throws IOException;

    /**
     * Called after a retrieve has taken place, allows additional UI-updates to be performed.
     * 
     * @throws IOException
     */
    protected abstract void doAfterRetrieve() throws IOException;

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
    }
}
