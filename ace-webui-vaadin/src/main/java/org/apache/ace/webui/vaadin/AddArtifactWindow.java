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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

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
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.terminal.StreamVariable;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.DragAndDropWrapper.WrapperTransferable;
import com.vaadin.ui.Html5File;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.SucceededEvent;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

@SuppressWarnings("serial")
class AddArtifactWindow extends Window {
    private volatile LogService m_log;
    private volatile ArtifactRepository m_artifactRepository;
    private File m_file;
    private Window m_main;
    private List<File> m_uploadedArtifacts = new ArrayList<File>();
    private File m_sessionDir;
    private List<OBREntry> m_obrList;
    private URL m_obrUrl;

    public AddArtifactWindow(final Window main, LogService log,
        File sessionDir, List<OBREntry> obrList, URL obrUrl,
        ArtifactRepository artifactRepository) {
        super();
        m_main = main;
        m_log = log;
        m_sessionDir = sessionDir;
        m_obrList = obrList;
        m_obrUrl = obrUrl;
        m_artifactRepository = artifactRepository;

        setModal(true);
        setCaption("Add artifact");
        setWidth("50em");

        VerticalLayout layout = (VerticalLayout) getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        final TextField search = new TextField("search");
        final Table artifacts = new ArtifactTable(main);
        final Table uploadedArtifacts = new ArtifactTable(main);
        final Upload uploadArtifact = new Upload("Upload Artifact",
            new Upload.Receiver() {
                public OutputStream receiveUpload(String filename,
                    String MIMEType) {
                    FileOutputStream fos = null;
                    try {
                        m_file = new File(m_sessionDir, filename);
                        if (m_file.exists()) {
                            throw new IOException(
                                "Uploaded file already exists.");
                        }
                        fos = new FileOutputStream(m_file);
                    }
                    catch (final IOException e) {
                        m_main.showNotification(
                            "Upload artifact failed",
                            "File "
                                + m_file.getName()
                                + "<br />could not be accepted on the server.<br />"
                                + "Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                        m_log.log(LogService.LOG_ERROR, "Upload of "
                            + m_file.getAbsolutePath() + " failed.", e);
                        return null;
                    }
                    return fos;
                }
            });

        final DragAndDropWrapper finalUploadedArtifacts = new DragAndDropWrapper(
            uploadedArtifacts);

        final StreamVariable html5uploadStreamVariable = new StreamVariable() {
            FileOutputStream fos = null;

            public OutputStream getOutputStream() {
                return fos;
            }

            public boolean listenProgress() {
                return false;
            }

            public void streamingStarted(StreamingStartEvent event) {
                try {
                    m_file = new File(m_sessionDir, event.getFileName());
                    if (m_file.exists()) {
                        throw new IOException("Uploaded file already exists: "
                            + event.getFileName());
                    }
                    fos = new FileOutputStream(m_file);
                }
                catch (final IOException e) {
                    m_main.showNotification(
                        "Upload artifact failed",
                        "File "
                            + m_file.getName()
                            + "<br />could not be accepted on the server.<br />"
                            + "Reason: " + e.getMessage(),
                        Notification.TYPE_ERROR_MESSAGE);
                    m_log.log(LogService.LOG_ERROR,
                        "Upload of " + m_file.getAbsolutePath()
                            + " failed.", e);
                    fos = null;
                }
            }

            public void streamingFinished(StreamingEndEvent event) {
                try {
                    URL artifact = m_file.toURI().toURL();
                    Item item = uploadedArtifacts.addItem(artifact);
                    item.getItemProperty("symbolic name").setValue(
                        m_file.getName());
                    item.getItemProperty("version").setValue("");
                    m_uploadedArtifacts.add(m_file);
                    fos.close();
                }
                catch (IOException e) {
                    m_main.showNotification(
                        "Upload artifact processing failed",
                        "<br />Reason: " + e.getMessage(),
                        Notification.TYPE_ERROR_MESSAGE);
                    m_log.log(LogService.LOG_ERROR,
                        "Processing of " + m_file.getAbsolutePath()
                            + " failed.", e);
                }
                finally {
                    fos = null;
                }
            }

            public void streamingFailed(StreamingErrorEvent event) {
                m_main.showNotification("Upload artifact failed", "File "
                    + m_file.getName()
                    + "<br />could not be accepted on the server.<br />"
                    + "Reason: " + event.getException().getMessage(),
                    Notification.TYPE_ERROR_MESSAGE);
                m_log.log(LogService.LOG_ERROR,
                    "Upload of " + event.getFileName() + " failed.");
                m_file.delete();
                fos = null;
            }

            public boolean isInterrupted() {
                return fos == null;
            }

            public void onProgress(StreamingProgressEvent event) {
                // Do nothing, no progress indicator (yet ?)
            }

        };

        final DropHandler html5uploadDropHandler = new DropHandler() {

            public void drop(DragAndDropEvent dropEvent) {
                // expecting this to be an html5 drag
                WrapperTransferable tr = (WrapperTransferable) dropEvent
                    .getTransferable();
                Html5File[] files = tr.getFiles();
                if (files != null) {
                    for (final Html5File html5File : files) {
                        html5File.setStreamVariable(html5uploadStreamVariable);
                    }
                }
            }

            public AcceptCriterion getAcceptCriterion() {
                // TODO only accept .jar files ?
                return AcceptAll.get();
            }

        };

        finalUploadedArtifacts.setDropHandler(html5uploadDropHandler);

        this.addListener(new Window.CloseListener() {
            public void windowClose(CloseEvent e) {
                for (File artifact : m_uploadedArtifacts) {
                    artifact.delete();
                }
            }
        });

        artifacts.setCaption("Artifacts in repository");
        uploadedArtifacts.setCaption("Uploaded artifacts");
        uploadedArtifacts.setSelectable(false);

        search.setValue("");
        try {
            getBundles(artifacts);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        uploadArtifact.setImmediate(true);

        uploadArtifact.addListener(new Upload.SucceededListener() {

            public void uploadSucceeded(SucceededEvent event) {
                try {
                    URL artifact = m_file.toURI().toURL();
                    Item item = uploadedArtifacts.addItem(artifact);
                    item.getItemProperty("symbolic name").setValue(
                        m_file.getName());
                    item.getItemProperty("version").setValue("");
                    m_uploadedArtifacts.add(m_file);
                }
                catch (IOException e) {
                    m_main.showNotification(
                        "Upload artifact processing failed",
                        "<br />Reason: " + e.getMessage(),
                        Notification.TYPE_ERROR_MESSAGE);
                    m_log.log(LogService.LOG_ERROR,
                        "Processing of " + m_file.getAbsolutePath()
                            + " failed.", e);
                }
            }
        });
        uploadArtifact.addListener(new Upload.FailedListener() {
            public void uploadFailed(FailedEvent event) {
                m_main.showNotification("Upload artifact failed", "File "
                    + event.getFilename()
                    + "<br />could not be uploaded to the server.<br />"
                    + "Reason: " + event.getReason().getMessage(),
                    Notification.TYPE_ERROR_MESSAGE);
                m_log.log(
                    LogService.LOG_ERROR,
                    "Upload of " + event.getFilename() + " size "
                        + event.getLength() + " type "
                        + event.getMIMEType() + " failed.",
                    event.getReason());
            }
        });

        layout.addComponent(search);
        layout.addComponent(artifacts);
        layout.addComponent(uploadArtifact);
        layout.addComponent(finalUploadedArtifacts);

        Button close = new Button("Add", new Button.ClickListener() {
            // inline click-listener
            public void buttonClick(ClickEvent event) {
                List<ArtifactObject> added = new ArrayList<ArtifactObject>();
                // TODO add the selected artifacts
                for (Object id : artifacts.getItemIds()) {
                    if (artifacts.isSelected(id)) {
                        for (OBREntry e : m_obrList) {
                            if (e.getUri().equals(id)) {
                                try {
                                    ArtifactObject ao = importBundle(e);
                                    added.add(ao);
                                }
                                catch (Exception e1) {
                                    m_main.showNotification(
                                        "Import artifact failed",
                                        "Artifact "
                                            + e.getSymbolicName()
                                            + " "
                                            + e.getVersion()
                                            + "<br />could not be imported into the repository.<br />"
                                            + "Reason: "
                                            + e1.getMessage(),
                                        Notification.TYPE_ERROR_MESSAGE);
                                    m_log.log(LogService.LOG_ERROR,
                                        "Import of " + e.getSymbolicName()
                                            + " " + e.getVersion()
                                            + " failed.", e1);
                                }
                            }
                        }
                    }
                }
                for (File artifact : m_uploadedArtifacts) {
                    try {
                        ArtifactObject ao = importBundle(artifact.toURI()
                            .toURL());
                        added.add(ao);
                    }
                    catch (Exception e) {
                        m_main.showNotification(
                            "Import artifact failed",
                            "Artifact "
                                + artifact.getAbsolutePath()
                                + "<br />could not be imported into the repository.<br />"
                                + "Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                        m_log.log(LogService.LOG_ERROR,
                            "Import of " + artifact.getAbsolutePath()
                                + " failed.", e);
                    }
                    finally {
                        artifact.delete();
                    }
                }
                // close the window by removing it from the parent window
                (AddArtifactWindow.this.getParent())
                    .removeWindow(AddArtifactWindow.this);
                // TODO: make a decision here
                // so now we have enough information to show a list of imported
                // artifacts (added)
                // but do we want to show this list or do we just assume the
                // user will see the new
                // artifacts in the left most column? do we also report
                // failures? or only report
                // if there were failures?
            }
        });
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(close);
        layout.setComponentAlignment(close, Alignment.MIDDLE_RIGHT);
        search.focus();
    }

    public void getBundles(Table table) throws Exception {
        getBundles(table, m_obrUrl);
    }

    public void getBundles(Table table, URL obrBaseUrl) throws Exception {
        // retrieve the repository.xml as a stream
        URL url = null;
        try {
            url = new URL(obrBaseUrl, "repository.xml");
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR,
                "Error retrieving repository.xml from " + obrBaseUrl);
            throw e;
        }

        InputStream input = null;
        NodeList resources = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false); // We always want the newest
            // repository.xml file.
            input = connection.getInputStream();

            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                // this XPath expressing will find all 'resource' elements which
                // have an attribute 'uri'.
                resources = (NodeList) xpath.evaluate(
                    "/repository/resource[@uri]", new InputSource(input),
                    XPathConstants.NODESET);
            }
            catch (XPathExpressionException e) {
                m_log.log(LogService.LOG_ERROR,
                    "Error evaluating XPath expression.", e);
                throw e;
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR,
                "Error reading repository metadata.", e);
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

        m_obrList = new ArrayList<OBREntry>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();
            String uri = getNamedItemText(attr, "uri");
            String symbolicname = getNamedItemText(attr, "symbolicname");
            String version = getNamedItemText(attr, "version");
            m_obrList.add(new OBREntry(symbolicname, version, uri));
        }

        // Create a list of filenames from the ArtifactRepository
        List<OBREntry> fromRepository = new ArrayList<OBREntry>();
        List<ArtifactObject> artifactObjects = m_artifactRepository.get();
        artifactObjects.addAll(m_artifactRepository.getResourceProcessors());
        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if (artifactURL.startsWith(obrBaseUrl.toExternalForm())) {
                // we now know this artifact comes from the OBR we are querying,
                // so we are interested.
                fromRepository.add(new OBREntry(ao.getName(), ao
                    .getAttribute(BundleHelper.KEY_VERSION), new File(
                    artifactURL).getName()));
            }
        }

        // remove all urls we already know
        m_obrList.removeAll(fromRepository);
        if (m_obrList.isEmpty()) {
            m_log.log(LogService.LOG_INFO, "No new data in OBR.");
            return;
        }

        // Create a list of all bundle names
        for (OBREntry s : m_obrList) {
            String uri = s.getUri();
            String symbolicName = s.getSymbolicName();
            String version = s.getVersion();
            try {
                Item item = table.addItem(uri);
                if (symbolicName == null || symbolicName.length() == 0) {
                    item.getItemProperty("symbolic name").setValue(uri);
                }
                else {
                    item.getItemProperty("symbolic name")
                        .setValue(symbolicName);
                }
                item.getItemProperty("version").setValue(version);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static String getNamedItemText(NamedNodeMap attr, String name) {
        Node namedItem = attr.getNamedItem(name);
        if (namedItem == null) {
            return null;
        }
        else {
            return namedItem.getTextContent();
        }
    }

    public ArtifactObject importBundle(OBREntry bundle) throws IOException {
        return m_artifactRepository.importArtifact(
            new URL(m_obrUrl, bundle.getUri()), false);
    }

    public ArtifactObject importBundle(URL artifact) throws IOException {
        return m_artifactRepository.importArtifact(artifact, true);
    }
}