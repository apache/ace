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

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.webui.domain.OBREntry;
import org.osgi.service.log.LogService;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.vaadin.data.Item;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.terminal.StreamVariable;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.DragAndDropWrapper.WrapperTransferable;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Html5File;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.FailedListener;
import com.vaadin.ui.Upload.SucceededEvent;
import com.vaadin.ui.Upload.SucceededListener;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * Provides a dialog for uploading new artifacts to ACE, or selecting existing artifacts from the repository.
 */
abstract class AddArtifactWindow extends Window {

    /**
     * Provides a {@link DropHandler} implementation for handling dropped artifacts.
     */
    private static final class ArtifactDropHandler implements DropHandler {
        private final StreamVariable m_html5uploadStreamVariable;

        private ArtifactDropHandler(StreamVariable html5uploadStreamVariable) {
            m_html5uploadStreamVariable = html5uploadStreamVariable;
        }

        /*
         * @see com.vaadin.event.dd.DropHandler#drop(com.vaadin.event.dd.DragAndDropEvent)
         */
        public void drop(DragAndDropEvent dropEvent) {
            // expecting this to be an html5 drag
            WrapperTransferable tr = (WrapperTransferable) dropEvent.getTransferable();
            Html5File[] files = tr.getFiles();
            if (files != null) {
                for (final Html5File html5File : files) {
                    html5File.setStreamVariable(m_html5uploadStreamVariable);
                }
            }
        }

        /*
         * @see com.vaadin.event.dd.DropHandler#getAcceptCriterion()
         */
        public AcceptCriterion getAcceptCriterion() {
            // TODO only accept .jar files ?
            return AcceptAll.get();
        }
    }

    /**
     * Provides a upload handler capable of handling "old school" uploads, and new HTML5-style uploads.
     */
    private static abstract class GenericUploadHandler implements StreamVariable, Upload.SucceededListener,
        Upload.FailedListener, Upload.Receiver {
        private final File m_sessionDir;

        private FileOutputStream m_fos = null;
        private File m_file;

        /**
         * @param sessionDir the session directory to temporarily store uploaded artifacts in, cannot be <code>null</code>.
         */
        private GenericUploadHandler(File sessionDir) {
            m_sessionDir = sessionDir;
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#getOutputStream()
         */
        public final OutputStream getOutputStream() {
            return m_fos;
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#isInterrupted()
         */
        public final boolean isInterrupted() {
            return (m_fos == null);
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#listenProgress()
         */
        public final boolean listenProgress() {
            return false;
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#onProgress(com.vaadin.terminal.StreamVariable.StreamingProgressEvent)
         */
        public final void onProgress(StreamingProgressEvent event) {
            // Do nothing, no progress indicator (yet ?)
        }

        /*
         * @see com.vaadin.ui.Upload.Receiver#receiveUpload(java.lang.String, java.lang.String)
         */
        public final OutputStream receiveUpload(String filename, String MIMEType) {
            return prepareUpload(filename);
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#streamingFailed(com.vaadin.terminal.StreamVariable.StreamingErrorEvent)
         */
        public final void streamingFailed(StreamingErrorEvent event) {
            handleUploadFailure(event.getFileName(), event.getException());
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#streamingFinished(com.vaadin.terminal.StreamVariable.StreamingEndEvent)
         */
        public final void streamingFinished(StreamingEndEvent event) {
            finishUpload();
        }

        /*
         * @see com.vaadin.terminal.StreamVariable#streamingStarted(com.vaadin.terminal.StreamVariable.StreamingStartEvent)
         */
        public final void streamingStarted(StreamingStartEvent event) {
            prepareUpload(event.getFileName());
        }

        /*
         * @see com.vaadin.ui.Upload.FailedListener#uploadFailed(com.vaadin.ui.Upload.FailedEvent)
         */
        public final void uploadFailed(FailedEvent event) {
            handleUploadFailure(event.getFilename(), event.getReason());
        }

        /*
         * @see com.vaadin.ui.Upload.SucceededListener#uploadSucceeded(com.vaadin.ui.Upload.SucceededEvent)
         */
        public final void uploadSucceeded(SucceededEvent event) {
            finishUpload();
        }

        /**
         * Called when the upload was successful.
         * 
         * @param uploadedArtifact the uploaded file to process, never <code>null</code>.
         */
        protected abstract void artifactUploaded(File uploadedArtifact);

        /**
         * Called when the upload failed.
         * 
         * @param uploadedArtifact the name of the artifact whose upload failed;
         * @param throwable the (optional) exception that caused the upload to fail.
         */
        protected abstract void uploadFailed(String uploadedArtifact, Throwable throwable);

        /**
         * Called after successfully uploading the artifact. Calls the {@link #artifactUploaded(File)} method.
         */
        private void finishUpload() {
            // Make sure the output stream is properly closed...
            silentlyClose(m_fos);

            try {
                artifactUploaded(m_file);
            }
            finally {
                m_fos = null;
            }
        }

        /**
         * Handles any failures during the upload by closing all streams and cleaning up all resources. Calls the {@link #uploadFailed(String, Throwable)}
         * 
         * @param fileName the name of the uploaded artifact that failed;
         * @param throwable the (optional) exception, can be <code>null</code>.
         */
        private void handleUploadFailure(String fileName, Throwable throwable) {
            silentlyClose(m_fos);
            m_fos = null;
            m_file.delete();

            uploadFailed(fileName, throwable);
        }

        /**
         * Prepares the actual upload by creating a proper {@link FileOutputStream} to (temporarily) store the artifact to.
         * 
         * @param fileName the name of the uploaded artifact, cannot be <code>null</code>.
         */
        private OutputStream prepareUpload(String fileName) {
            try {
                m_file = new File(m_sessionDir, fileName);

                if (m_file.exists()) {
                    throw new IOException("Uploaded file already exists: " + fileName);
                }
                m_fos = new FileOutputStream(m_file);
            }
            catch (final IOException e) {
                uploadFailed(fileName, e);
                m_fos = null;
            }
            return m_fos;
        }

        /**
         * Silently closes the given {@link Closeable} implementation, ignoring any errors that come out of the {@link Closeable#close()} method.
         * 
         * @param closable the closeable to close, can be <code>null</code>.
         */
        private void silentlyClose(Closeable closable) {
            if (closable != null) {
                try {
                    closable.close();
                }
                catch (IOException e) {
                    // Best effort; nothing we can (or want) do about this...
                }
            }
        }
    }

    private static final String XPATH_QUERY = "/repository/resource[@uri]";

    private final File m_sessionDir;
    private final URL m_obrUrl;
    private final String m_repositoryXML;

    private final List<File> m_uploadedArtifacts = new ArrayList<File>();
    private final Button m_searchButton;
    private final Button m_closeButton;
    private final Table m_artifactsTable;

    /**
     * Creates a new {@link AddArtifactWindow} instance.
     * 
     * @param sessionDir the session directory to temporary place artifacts in;
     * @param obrUrl the URL of the OBR to use.
     */
    public AddArtifactWindow(File sessionDir, URL obrUrl, String repositoryXML) {
        super("Add artifact");

        m_sessionDir = sessionDir;
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;

        setModal(true);
        setWidth("50em");

        m_artifactsTable = new ResourceTable();
        m_artifactsTable.setCaption("Artifacts in repository");

        final IndexedContainer dataSource = (IndexedContainer) m_artifactsTable.getContainerDataSource();

        final Table uploadedArtifacts = new ArtifactTable();
        uploadedArtifacts.setCaption("Uploaded artifacts");
        uploadedArtifacts.setSelectable(false);

        final GenericUploadHandler uploadHandler = new GenericUploadHandler(m_sessionDir) {
            @Override
            protected void artifactUploaded(File uploadedArtifact) {
                try {
                    URL artifact = uploadedArtifact.toURI().toURL();

                    Item item = uploadedArtifacts.addItem(artifact);
                    item.getItemProperty(ArtifactTable.PROPERTY_SYMBOLIC_NAME).setValue(uploadedArtifact.getName());
                    item.getItemProperty(ArtifactTable.PROPERTY_VERSION).setValue("");

                    m_uploadedArtifacts.add(uploadedArtifact);
                }
                catch (MalformedURLException e) {
                    showErrorNotification("Upload artifact processing failed", "<br />Reason: " + e.getMessage());
                    logError("Processing of " + uploadedArtifact + " failed.", e);
                }
            }

            @Override
            protected void uploadFailed(String uploadedArtifact, Throwable throwable) {
                showErrorNotification("Upload artifact failed", "File "
                    + uploadedArtifact
                    + "<br />could not be accepted on the server.<br />"
                    + "Reason: " + throwable);

                logError("Upload of " + uploadedArtifact + " failed.");
            }
        };

        final Upload uploadArtifact = new Upload("Upload Artifact", uploadHandler);
        uploadArtifact.addListener((SucceededListener) uploadHandler);
        uploadArtifact.addListener((FailedListener) uploadHandler);
        uploadArtifact.setImmediate(true);

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

        m_searchButton = new Button("Search", new ClickListener() {
            public void buttonClick(ClickEvent event) {
                String searchValue = (String) searchField.getValue();

                dataSource.removeAllContainerFilters();

                if (searchValue != null && searchValue.trim().length() > 0) {
                    dataSource.addContainerFilter(ArtifactTable.PROPERTY_SYMBOLIC_NAME, searchValue,
                        true /* ignoreCase */, false /* onlyMatchPrefix */);
                }
            }
        });
        m_searchButton.setImmediate(true);

        searchBar.addComponent(searchField);
        searchBar.addComponent(m_searchButton);

        m_closeButton = new Button("Add", new ClickListener() {
            public void buttonClick(ClickEvent event) {
                // Import all "local" (existing) bundles...
                importLocalBundles(m_artifactsTable);
                // Import all "remote" (non existing) bundles...
                importRemoteBundles(m_uploadedArtifacts);

                closeWindow();

                // TODO: make a decision here so now we have enough information
                // to show a list of imported artifacts (added) but do we want
                // to show this list or do we just assume the user will see the
                // new artifacts in the left most column? do we also report
                // failures? or only report if there were failures?
            }
        });
        m_closeButton.setImmediate(true);

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        layout.addComponent(searchBar);
        layout.addComponent(m_artifactsTable);
        layout.addComponent(uploadArtifact);
        layout.addComponent(finalUploadedArtifacts);
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(m_closeButton);
        layout.setComponentAlignment(m_closeButton, Alignment.MIDDLE_RIGHT);

        searchField.focus();
    }

    /**
     * Gets the actual text from a named item contained in the given node map.
     * 
     * @param map the node map to get the named item from;
     * @param name the name of the item to get.
     * @return the text of the named item, can be <code>null</code> in case the named item does not exist, or has no text.
     */
    private static String getNamedItemText(NamedNodeMap map, String name) {
        Node namedItem = map.getNamedItem(name);
        if (namedItem == null) {
            return null;
        }
        else {
            return namedItem.getTextContent();
        }
    }
    
    /**
     * Shows this dialog on the parent window.
     * 
     * @param parent the parent for this window, cannot be <code>null</code>.
     */
    public final void showWindow(Window parent) {
        try {
            // Fill the artifacts table with the data from the OBR...
            getBundles(m_artifactsTable);
            
            parent.addWindow(this);
        }
        catch (Exception e) {
            // We've not yet added this window to the given parent, so we cannot use #showErrorNotification here...
            parent.showNotification("Failed to retrieve OBR repository!", "Reason: <br/>" + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
            logError("Failed to retrieve OBR repository!", e);
        }
    }

    /**
     * Closes this window.
     */
    final void closeWindow() {
        // close the window by removing it from the parent window
        getParent().removeWindow(this);
    }

    /**
     * Imports all local, i.e., that are already in our local OBR, bundles.
     * 
     * @param artifacts the UI-table with artifacts to install, cannot be <code>null</code>.
     * @return the imported artifacts, never <code>null</code>.
     */
    final List<ArtifactObject> importLocalBundles(final Table artifacts) {
        final List<ArtifactObject> added = new ArrayList<ArtifactObject>();

        Set<String> selectedItems = (Set<String>) artifacts.getValue();
        if (selectedItems != null && !selectedItems.isEmpty()) {
            for (String itemID : selectedItems) {
                try {
                    added.add(importLocalBundle(new URL(m_obrUrl, itemID)));
                }
                catch (Exception exception) {
                    Item item = artifacts.getItem(itemID);

                    Object symbolicName = item.getItemProperty(ArtifactTable.PROPERTY_SYMBOLIC_NAME).getValue();
                    Object version = item.getItemProperty(ArtifactTable.PROPERTY_VERSION).getValue();

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
     * @param uploadedArtifacts the list with uploaded artifacts, never <code>null</code>.
     * @return the list of imported bundles.
     */
    final List<ArtifactObject> importRemoteBundles(List<File> uploadedArtifacts) {
        List<ArtifactObject> added = new ArrayList<ArtifactObject>();

        for (File artifact : uploadedArtifacts) {
            try {
                added.add(importRemoteBundle(artifact.toURI().toURL()));
            }
            catch (Exception exception) {
                showErrorNotification("Import artifact failed", "<br/>Artifact '"
                    + artifact.getName()
                    + "' could not be imported into the repository.<br />"
                    + "Reason: " + exception.getMessage());

                logError("Import of " + artifact.getAbsolutePath() + " failed.", exception);
            }
            finally {
                artifact.delete();
            }
        }

        return added;
    }

    /**
     * Shows an error message on screen.
     * 
     * @param aTitle the title of the error message;
     * @param aMessage the error message itself.
     */
    final void showErrorNotification(final String aTitle, final String aMessage) {
        getParent().showNotification(aTitle, aMessage, Notification.TYPE_ERROR_MESSAGE);
    }

    /**
     * Logs a given message at the error level.
     * <p>If there's no log service present, this method will silently ignore the log statement.</p>
     * 
     * @param aMessage the message to log.
     */
    final void logError(String aMessage) {
        LogService logger = getLogger();
        if (logger != null) {
            logger.log(LogService.LOG_ERROR, aMessage);
        }
    }

    /**
     * Logs a given message + exception at the error level.
     * <p>If there's no log service present, this method will silently ignore the log statement.</p>
     * 
     * @param aMessage the message to log;
     * @param aException the exception to log.
     */
    final void logError(String aMessage, Throwable aException) {
        LogService logger = getLogger();
        if (logger != null) {
            logger.log(LogService.LOG_ERROR, aMessage, aException);
        }
    }

    /**
     * @return the artifact repository.
     */
    protected abstract ArtifactRepository getArtifactRepository();

    /**
     * @return the log service.
     */
    protected abstract LogService getLogger();
    
    /**
     * @param url the URL to connect to, cannot be <code>null</code>.
     * @return a valid {@link URLConnection} instance, never <code>null</code>.
     */
    protected abstract URLConnection openConnection(URL url) throws IOException;

    /**
     * Converts a given artifact object to an OBR entry.
     * 
     * @param artifactObject the artifact object to convert;
     * @param obrBase the obr base url.
     * @return an OBR entry instance, never <code>null</code>.
     */
    private OBREntry convertToOBREntry(ArtifactObject artifactObject, String obrBase) {
        String name = artifactObject.getName();
        String symbolicName = artifactObject.getAttribute(BundleHelper.KEY_SYMBOLICNAME);
        String version = artifactObject.getAttribute(BundleHelper.KEY_VERSION);
        String relativeURL = artifactObject.getURL().substring(obrBase.length());
        return new OBREntry(name, symbolicName, version, relativeURL);
    }

    /**
     * Gets the bundles.
     * 
     * @param table the table
     * @return the bundles
     * @throws Exception the exception
     */
    private void getBundles(Table table) throws Exception {
        getBundles(table, m_obrUrl);
    }

    /**
     * Gets the bundles.
     * 
     * @param dataSource the datasource to fill;
     * @param obrBaseUrl the obr base url
     * @return the bundles
     * @throws Exception the exception
     */
    private void getBundles(Table dataSource, URL obrBaseUrl) throws Exception {
        // retrieve the repository.xml as a stream
        List<OBREntry> obrList = parseOBRRepository(obrBaseUrl);

        // Create a list of filenames from the ArtifactRepository
        // remove those from the OBR entries we already know
        obrList.removeAll(getUsedOBRArtifacts(obrBaseUrl));

        if (obrList.isEmpty()) {
            logError("No new data in OBR.");
            return;
        }

        // Create a list of all bundle names
        for (final OBREntry entry : obrList) {
            
            String uri = entry.getUri();
            String name = entry.getName();
            String version = entry.getVersion();

            Item item = dataSource.addItem(uri);
            item.getItemProperty(ResourceTable.PROPERTY_SYMBOLIC_NAME).setValue(name);
            item.getItemProperty(ResourceTable.PROPERTY_VERSION).setValue(version);
            item.getItemProperty(ResourceTable.PROPERTY_PURGE).setValue(createDeleteOBREntryButton(entry));
        }
    }
    
    /**
     * Builds a list of all OBR artifacts currently in use.
     * 
     * @param obrBaseUrl the base URL of the OBR, cannot be <code>null</code>.
     * @return a list of used OBR entries, never <code>null</code>.
     * @throws IOException in case an artifact repository is not present.
     */
    private List<OBREntry> getUsedOBRArtifacts(URL obrBaseUrl) throws IOException {
        ArtifactRepository artifactRepository = getArtifactRepository();
        if (artifactRepository == null) {
            throw new IOException("No artifact repository present!");
        }

        final String baseURL = obrBaseUrl.toExternalForm();

        List<OBREntry> fromRepository = new ArrayList<OBREntry>();

        List<ArtifactObject> artifactObjects = artifactRepository.get();
        artifactObjects.addAll(artifactRepository.getResourceProcessors());

        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if (artifactURL != null && artifactURL.startsWith(baseURL)) {
                // we now know this artifact comes from the OBR we are querying,
                // so we are interested.
                fromRepository.add(convertToOBREntry(ao, baseURL));
            }
        }
        return fromRepository;
    }

    /**
     * Imports a local bundle (already contained in the OBR) bundle.
     * 
     * @param artifactURL the URL of the artifact to import, cannot be <code>null</code>.
     * @return the imported artifact object, never <code>null</code>.
     * @throws IOException in case an I/O exception has occurred.
     */
    private ArtifactObject importLocalBundle(URL artifactURL) throws IOException {
        ArtifactRepository artifactRepository = getArtifactRepository();
        if (artifactRepository == null) {
            throw new IOException("No artifact repository present!");
        }
        return artifactRepository.importArtifact(artifactURL, false /* upload */);
    }

    /**
     * Imports a remote bundle by uploading it to the OBR.
     * 
     * @param artifactURL the URL of the artifact to import, cannot be <code>null</code>.
     * @return the imported artifact object, never <code>null</code>.
     * @throws IOException in case an I/O exception has occurred.
     */
    private ArtifactObject importRemoteBundle(URL artifactURL) throws IOException {
        ArtifactRepository artifactRepository = getArtifactRepository();
        if (artifactRepository == null) {
            throw new IOException("No artifact repository present!");
        }
        return artifactRepository.importArtifact(artifactURL, true /* upload */);
    }

    /**
     * Parses the 'repository.xml' from OBR.
     * 
     * @param obrBaseUrl the base URL to access the OBR, cannot be <code>null</code>.
     * @return a list of parsed OBR entries, never <code>null</code>.
     * @throws XPathExpressionException in case OBR repository is invalid, or incorrect;
     * @throws IOException in case of problems accessing the 'repository.xml' file.
     */
    private List<OBREntry> parseOBRRepository(URL obrBaseUrl) throws XPathExpressionException, IOException {
        URL url = null;
        try {
            url = new URL(obrBaseUrl, m_repositoryXML);
        }
        catch (MalformedURLException e) {
            logError("Error retrieving repository.xml from " + obrBaseUrl);
            throw e;
        }

        InputStream input = null;
        NodeList resources = null;
        try {
            URLConnection connection = openConnection(url);
            // We always want the newest repository.xml file.
            connection.setUseCaches(false);

            input = connection.getInputStream();

            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                // this XPath expressing will find all 'resource' elements which
                // have an attribute 'uri'.
                resources = (NodeList) xpath.evaluate(XPATH_QUERY, new InputSource(input), XPathConstants.NODESET);
            }
            catch (XPathExpressionException e) {
                logError("Error evaluating XPath expression.", e);
                throw e;
            }
        }
        catch (IOException e) {
            logError("Error reading repository metadata.", e);
            throw e;
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException e) {
                    // too bad, no worries.
                }
            }
        }

        List<OBREntry> obrList = new ArrayList<OBREntry>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();

            String uri = getNamedItemText(attr, "uri");
            if (uri == null || uri.equals("")) {
                logError("Skipping resource without uri from repository " + obrBaseUrl);
                continue;
            }

            String name = getNamedItemText(attr, "presentationname");
            String symbolicname = getNamedItemText(attr, "symbolicname");
            String version = getNamedItemText(attr, "version");

            if (name == null || name.equals("")) {
                if (symbolicname != null && !symbolicname.equals("")) {
                    name = symbolicname;
                }
                else {
                    name = new File(uri).getName();
                }
            }

            obrList.add(new OBREntry(name, symbolicname, version, uri));
        }

        return obrList;
    }
    
    /**
     * Delete an entry from the OBR.
     * 
     * @param entry
     *            The OBREntry
     * @param obrBaseUrl
     *            The OBR base url
     * @return The HTTP response code
     * @throws IOException
     *             If the HTTP operation fails
     */
    private int deleteOBREntry(OBREntry entry, URL obrBaseUrl) throws IOException {

        HttpURLConnection connection = null;
        try {
            URL endpointUrl = new URL(obrBaseUrl, entry.getUri());
            connection = (HttpURLConnection) openConnection(endpointUrl);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("DELETE");
            connection.connect();
            return connection.getResponseCode();
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Create a new button that delete an OBREntry on-click.
     * 
     * @param entry
     *            The entry
     * @return The button
     */
    private Button createDeleteOBREntryButton(final OBREntry entry) {

        return new Button("X", new ClickListener() {

            @Override
            public void buttonClick(ClickEvent event) {

                try {
                    int code = deleteOBREntry(entry, m_obrUrl);
                    if (code == HttpServletResponse.SC_OK) {
                        m_artifactsTable.removeItem(entry.getUri());
                        m_artifactsTable.refreshRowCache();
                    }
                    else {
                        getWindow().showNotification("The OBR returned an unexpected response code: " + code,
                            Notification.TYPE_ERROR_MESSAGE);

                    }
                }
                catch (IOException e) {
                    getWindow().showNotification("Failed to delete resource!", "<br/>Reason: " + e.getMessage(),
                        Notification.TYPE_ERROR_MESSAGE);
                    e.printStackTrace();
                }

            }
        });
    }
}