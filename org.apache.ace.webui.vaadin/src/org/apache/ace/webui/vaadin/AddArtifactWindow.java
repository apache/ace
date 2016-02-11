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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.webui.domain.OBREntry;
import org.apache.ace.webui.vaadin.UploadHelper.ArtifactDropHandler;
import org.apache.ace.webui.vaadin.UploadHelper.GenericUploadHandler;
import org.apache.ace.webui.vaadin.UploadHelper.UploadHandle;
import org.osgi.service.log.LogService;

import com.vaadin.data.Item;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.themes.Reindeer;

/**
 * Provides a dialog for uploading new artifacts to ACE, or selecting existing artifacts from the repository.
 */
abstract class AddArtifactWindow extends Window {
    private static final String PROPERTY_SYMBOLIC_NAME = "symbolic name";
    private static final String PROPERTY_VERSION = "version";
    private static final String PROPERTY_PURGE = "purge";

    private final String m_repositoryXML;
    private final File m_sessionDir;
    private final URL m_obrUrl;

    private final List<File> m_uploadedArtifacts = new ArrayList<>();
    private final Button m_searchButton;
    private final Button m_addButton;
    private final Table m_artifactsTable;

    /**
     * Creates a new {@link AddArtifactWindow} instance.
     * 
     * @param sessionDir
     *            the session directory to temporary place artifacts in;
     * @param obrUrl
     *            the URL of the OBR to use.
     */
    public AddArtifactWindow(File sessionDir, URL obrUrl, String repositoryXML) {
        super("Add artifact");

        m_sessionDir = sessionDir;
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;

        setModal(true);
        setWidth("50em");
        setCloseShortcut(KeyCode.ESCAPE);

        m_artifactsTable = new Table("Artifacts in repository");
        m_artifactsTable.addContainerProperty(PROPERTY_SYMBOLIC_NAME, String.class, null);
        m_artifactsTable.addContainerProperty(PROPERTY_VERSION, String.class, null);
        m_artifactsTable.addContainerProperty(PROPERTY_PURGE, Button.class, null);
        m_artifactsTable.setSizeFull();
        m_artifactsTable.setSelectable(true);
        m_artifactsTable.setMultiSelect(true);
        m_artifactsTable.setImmediate(true);
        m_artifactsTable.setHeight("15em");

        final Table uploadedArtifacts = new Table("Uploaded artifacts");
        uploadedArtifacts.addContainerProperty(PROPERTY_SYMBOLIC_NAME, String.class, null);
        uploadedArtifacts.addContainerProperty(PROPERTY_VERSION, String.class, null);
        uploadedArtifacts.setSizeFull();
        uploadedArtifacts.setSelectable(false);
        uploadedArtifacts.setMultiSelect(false);
        uploadedArtifacts.setImmediate(true);
        uploadedArtifacts.setHeight("15em");

        final GenericUploadHandler uploadHandler = new GenericUploadHandler(m_sessionDir) {
            @Override
            public void updateProgress(long readBytes, long contentLength) {
                // TODO Auto-generated method stub
            }

            @Override
            protected void artifactsUploaded(List<UploadHandle> uploads) {
                for (UploadHandle handle : uploads) {
                    try {
                        URL artifact = handle.getFile().toURI().toURL();

                        Item item = uploadedArtifacts.addItem(artifact);
                        item.getItemProperty(PROPERTY_SYMBOLIC_NAME).setValue(handle.getFilename());
                        item.getItemProperty(PROPERTY_VERSION).setValue("");

                        m_uploadedArtifacts.add(handle.getFile());
                    }
                    catch (MalformedURLException e) {
                        showErrorNotification("Upload artifact processing failed", "<br />Reason: " + e.getMessage());
                        logError("Processing of " + handle.getFilename() + " failed.", e);
                    }
                }
            }
        };

        final Upload uploadArtifact = new Upload();
        uploadArtifact.setCaption("Upload Artifact");
        uploadHandler.install(uploadArtifact);

        final DragAndDropWrapper finalUploadedArtifacts = new DragAndDropWrapper(uploadedArtifacts);
        finalUploadedArtifacts.setDropHandler(new ArtifactDropHandler(uploadHandler));

        addListener(new Window.CloseListener() {
            public void windowClose(CloseEvent e) {
                for (File artifact : m_uploadedArtifacts) {
                    artifact.delete();
                }
            }
        });

        HorizontalLayout searchBar = new HorizontalLayout();
        searchBar.setMargin(false);
        searchBar.setSpacing(true);

        final TextField searchField = new TextField();
        searchField.setImmediate(true);
        searchField.setValue("");

        final IndexedContainer dataSource = (IndexedContainer) m_artifactsTable.getContainerDataSource();

        m_searchButton = new Button("Search", new ClickListener() {
            public void buttonClick(ClickEvent event) {
                String searchValue = (String) searchField.getValue();

                dataSource.removeAllContainerFilters();

                if (searchValue != null && searchValue.trim().length() > 0) {
                    dataSource.addContainerFilter(PROPERTY_SYMBOLIC_NAME, searchValue,
                        true /* ignoreCase */, false /* onlyMatchPrefix */);
                }
            }
        });
        m_searchButton.setImmediate(true);

        searchBar.addComponent(searchField);
        searchBar.addComponent(m_searchButton);

        m_addButton = new Button("Add", new ClickListener() {
            public void buttonClick(ClickEvent event) {
                // Import all "local" (existing) bundles...
                importLocalBundles(m_artifactsTable);
                // Import all "remote" (non existing) bundles...
                importRemoteBundles(m_uploadedArtifacts);

                close();
            }
        });
        m_addButton.setImmediate(true);
        m_addButton.setStyleName(Reindeer.BUTTON_DEFAULT);
        m_addButton.setClickShortcut(KeyCode.ENTER);

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        layout.addComponent(searchBar);
        layout.addComponent(m_artifactsTable);
        layout.addComponent(uploadArtifact);
        layout.addComponent(finalUploadedArtifacts);
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(m_addButton);
        layout.setComponentAlignment(m_addButton, Alignment.MIDDLE_RIGHT);

        searchField.focus();
    }

    /**
     * Shows this dialog on the parent window.
     * 
     * @param parent
     *            the parent for this window, cannot be <code>null</code>.
     */
    public final void showWindow(Window parent) {
        try {
            // Fill the artifacts table with the data from the OBR...
            populateArtifactTable(m_artifactsTable, m_obrUrl);

            parent.addWindow(this);
        }
        catch (Exception e) {
            // We've not yet added this window to the given parent, so we cannot use #showErrorNotification here...
            parent.showNotification("Failed to retrieve OBR repository!", "Reason: <br/>" + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
            logError("Failed to retrieve OBR repository!", e);
        }
    }

    /**
     * Imports all local, i.e., that are already in our local OBR, bundles.
     * 
     * @param artifacts
     *            the UI-table with artifacts to install, cannot be <code>null</code>.
     * @return the imported artifacts, never <code>null</code>.
     */
    final List<ArtifactObject> importLocalBundles(final Table artifacts) {
        final List<ArtifactObject> added = new ArrayList<>();

        Set<?> selectedItems = (Set<?>) artifacts.getValue();
        if (selectedItems != null && !selectedItems.isEmpty()) {
            for (Object itemID : selectedItems) {
                try {
                    added.add(UploadHelper.importLocalBundle(getArtifactRepository(), createOBRUrl(itemID)));
                }
                catch (Exception exception) {
                    Item item = artifacts.getItem(itemID);

                    Object symbolicName = item.getItemProperty(PROPERTY_SYMBOLIC_NAME).getValue();
                    Object version = item.getItemProperty(PROPERTY_VERSION).getValue();

                    showErrorNotification("Import artifact failed", "Artifact " + symbolicName + " " + version
                        + "<br />could not be imported into the repository."
                        + "<br />Reason: " + exception.getMessage());

                    logError("Import of " + symbolicName + " " + version + " failed.", exception);
                }
            }
        }
        return added;
    }

    /**
     * Import remote bundles.
     * 
     * @param uploadedArtifacts
     *            the list with uploaded artifacts, never <code>null</code>.
     * @return the list of imported bundles.
     */
    final List<ArtifactObject> importRemoteBundles(List<File> uploadedArtifacts) {
        List<ArtifactObject> added = new ArrayList<>();

        StringBuffer errors = new StringBuffer();
        int failedImports = 0;
        for (File artifact : uploadedArtifacts) {
            try {
                added.add(UploadHelper.importRemoteBundle(getArtifactRepository(), artifact));
            }
            catch (Exception exception) {
                failedImports++;
                errors.append("<br />" + exception.getMessage());
                logError("Import of " + artifact.getAbsolutePath() + " failed.", exception);
            }
            finally {
                artifact.delete();
            }
        }
        if (failedImports > 0) {
            if (failedImports == uploadedArtifacts.size()) {
                showErrorNotification("All " + failedImports + " artifacts failed", (failedImports > 30 ? "See the server log for a full list of failures." : errors.toString()));
            }
            else {
                showWarningNotification("" + failedImports + "/" + uploadedArtifacts.size() + " artifacts failed", (failedImports > 30 ? "See the server log for a full list of failures." : errors.toString()));
            }
        }
        return added;
    }

    /**
     * Logs a given message + exception at the error level.
     * <p>
     * If there's no log service present, this method will silently ignore the log statement.
     * </p>
     * 
     * @param aMessage
     *            the message to log;
     * @param aException
     *            the exception to log.
     */
    final void logError(String aMessage, Throwable aException) {
        LogService logger = getLogger();
        if (logger != null) {
            logger.log(LogService.LOG_ERROR, aMessage, aException);
        }
    }

    /**
     * Shows an error message on screen.
     * 
     * @param aTitle
     *            the title of the error message;
     * @param aMessage
     *            the error message itself.
     */
    final void showErrorNotification(final String aTitle, final String aMessage) {
        getParent().showNotification(aTitle, aMessage, Notification.TYPE_ERROR_MESSAGE);
    }

    /**
     * @return the artifact repository.
     */
    protected abstract ArtifactRepository getArtifactRepository();

    /**
     * @param url
     *            the URL to connect to, cannot be <code>null</code>.
     * @return a valid {@link URLConnection} instance, never <code>null</code>.
     */
    protected abstract ConnectionFactory getConnectionFactory();

    /**
     * @return the log service.
     */
    protected abstract LogService getLogger();

    /**
     * Create a new button that delete an OBREntry on-click.
     * 
     * @param entry
     *            The entry
     * @return The button
     */
    private Button createDeleteOBREntryButton(final OBREntry entry) {
        Button button = new Button("x");
        button.setStyleName(Reindeer.BUTTON_SMALL);
        button.setDescription("Delete " + entry.getName());

        button.addListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                event.getButton().setEnabled(false);

                try {
                    int code = OBRUtil.deleteOBREntry(getConnectionFactory(), entry, m_obrUrl);
                    if (code == HttpServletResponse.SC_OK) {
                        m_artifactsTable.removeItem(entry.getUri());
                    }
                    else {
                        showErrorNotification("Failed to delete resource", "The OBR returned an unexpected response code: " + code);
                    }
                }
                catch (IOException e) {
                    showErrorNotification("Failed to delete resource", "Reason: " + e.getMessage());
                }

            }
        });

        return button;
    }

    private URL createOBRUrl(Object itemID) throws MalformedURLException {
        return new URL(m_obrUrl, String.valueOf(itemID));
    }

    /**
     * Gets the bundles.
     * 
     * @param dataSource
     *            the datasource to fill;
     * @param obrBaseUrl
     *            the obr base url
     * @return the bundles
     * @throws Exception
     *             the exception
     */
    private void populateArtifactTable(Table dataSource, URL obrBaseUrl) throws Exception {
        List<OBREntry> obrList = OBRUtil.getAvailableOBREntries(getConnectionFactory(), getArtifactRepository(), obrBaseUrl, m_repositoryXML);
        if (obrList.isEmpty()) {
            logDebug("No new data in OBR.");
            return;
        }

        // Create a list of all bundle names
        for (OBREntry entry : obrList) {
            Item item = dataSource.addItem(entry.getUri());
            item.getItemProperty(PROPERTY_SYMBOLIC_NAME).setValue(entry.getName());
            item.getItemProperty(PROPERTY_VERSION).setValue(entry.getVersion());
            item.getItemProperty(PROPERTY_PURGE).setValue(createDeleteOBREntryButton(entry));
        }
    }

    /**
     * Logs a given message at the debug level.
     * <p>
     * If there's no log service present, this method will silently ignore the log statement.
     * </p>
     * 
     * @param aMessage
     *            the message to log.
     */
    private void logDebug(String aMessage) {
        LogService logger = getLogger();
        if (logger != null) {
            logger.log(LogService.LOG_DEBUG, aMessage);
        }
    }

    /** Shows a warning messsage on screen. */
    private void showWarningNotification(final String aTitle, final String aMessage) {
        getParent().showNotification(aTitle, aMessage, Notification.TYPE_WARNING_MESSAGE);
    }
}
