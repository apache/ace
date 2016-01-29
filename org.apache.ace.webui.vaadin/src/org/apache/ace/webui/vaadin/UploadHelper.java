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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;

import com.vaadin.event.Transferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.terminal.StreamVariable;
import com.vaadin.ui.DragAndDropWrapper.WrapperTransferable;
import com.vaadin.ui.Html5File;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.FinishedEvent;
import com.vaadin.ui.Upload.SucceededEvent;

/**
 * Provides convenience and utility methods for handling uploads of artifacts.
 */
public class UploadHelper {
    public static interface UploadHandle {
        void cleanup();

        Exception getFailureReason();

        File getFile();

        String getFilename();

        boolean isSuccessful();
    }

    /**
     * Provides a {@link DropHandler} implementation for handling dropped artifacts.
     */
    static final class ArtifactDropHandler implements DropHandler {
        private final GenericUploadHandler m_uploadHandler;

        ArtifactDropHandler(GenericUploadHandler uploadHandler) {
            m_uploadHandler = uploadHandler;
        }

        @Override
        public void drop(DragAndDropEvent dropEvent) {
            Transferable transferable = dropEvent.getTransferable();
            if (!(transferable instanceof WrapperTransferable)) {
                return;
            }

            // expecting this to be an html5 drag
            WrapperTransferable tr = (WrapperTransferable) transferable;
            Html5File[] files = tr.getFiles();
            if (files != null) {
                for (Html5File html5File : files) {
                    StreamVariable streamVar = m_uploadHandler.prepareUpload(html5File);

                    html5File.setStreamVariable(streamVar);
                }
            }
        }

        @Override
        public AcceptCriterion getAcceptCriterion() {
            return AcceptAll.get();
        }
    }

    /**
     * Provides a upload handler capable of handling "old school" uploads, and new HTML5-style uploads.
     */
    static abstract class GenericUploadHandler implements Upload.SucceededListener,
        Upload.FailedListener, Upload.Receiver, Upload.FinishedListener, Upload.ProgressListener {
        private final Map<String, UploadHandleImpl> m_uploads = new ConcurrentHashMap<>();
        private final List<UploadHandle> m_completed = new CopyOnWriteArrayList<>();
        private final File m_sessionDir;

        /**
         * @param sessionDir
         *            the session directory to temporarily store uploaded artifacts in, cannot be <code>null</code>.
         */
        GenericUploadHandler(File sessionDir) {
            m_sessionDir = sessionDir;
        }

        /**
         * Install this upload handler for the given {@link Upload} component.
         * 
         * @param upload
         *            the upload component to install this handler on, cannot be <code>null</code>.
         */
        public void install(Upload upload) {
            upload.setReceiver(this);
            upload.addListener((Upload.FailedListener) this);
            upload.addListener((Upload.FinishedListener) this);
            upload.addListener((Upload.ProgressListener) this);
            upload.addListener((Upload.SucceededListener) this);
        }

        @Override
        public final OutputStream receiveUpload(String filename, String MIMEType) {
            UploadHandleImpl handle = new UploadHandleImpl(this, new File(m_sessionDir, filename));
            m_uploads.put(filename, handle);
            handle.startUpload();
            uploadStarted(handle);
            return handle.m_fos;
        }

        @Override
        public final void uploadFailed(FailedEvent event) {
            handleUploadFailure(event.getFilename(), event.getReason());
        }

        @Override
        public final void uploadFinished(FinishedEvent event) {
            UploadHandleImpl handle = m_uploads.get(event.getFilename());
            if (handle != null) {
                silentlyClose(handle);
            }
        }

        @Override
        public final void uploadSucceeded(SucceededEvent event) {
            handleUploadSuccess(event.getFilename());
        }

        final void handleUploadFailure(String filename, Exception reason) {
            UploadHandleImpl handle = m_uploads.remove(filename);
            if (handle != null) {
                handle.uploadFailed(reason);

                silentlyClose(handle);

                m_completed.add(handle);
            }

            if (m_uploads.isEmpty()) {
                // All uploads are finished...
                artifactsUploaded(m_completed);
                m_completed.clear();
            }
        }

        final void handleUploadSuccess(String filename) {
            UploadHandleImpl handle = m_uploads.remove(filename);
            if (handle != null) {
                silentlyClose(handle);

                m_completed.add(handle);
            }

            if (m_uploads.isEmpty()) {
                // All uploads are finished...
                artifactsUploaded(m_completed);
                m_completed.clear();
            }
        }

        final UploadHandleImpl prepareUpload(Html5File file) {
            String fileName = file.getFileName();
            UploadHandleImpl uploadHandle = new UploadHandleImpl(this, new File(m_sessionDir, fileName));
            m_uploads.put(fileName, uploadHandle);
            return uploadHandle;
        }

        /**
         * Called when the upload is finished. The exact status (success or failure) can be determined by the given
         * {@link UploadHandle}.
         * 
         * @param handles
         *            the uploaded artifacts to process, never <code>null</code>.
         */
        protected abstract void artifactsUploaded(List<UploadHandle> handles);

        /**
         * Called when the upload is started for an artifact.
         * 
         * @param handle
         *            the handle to the upload that is started, never <code>null</code>.
         */
        protected void uploadStarted(UploadHandle handle) {
            // Nop
        }
    }

    private static class UploadHandleImpl implements StreamVariable, Closeable, UploadHandle {
        private final GenericUploadHandler m_handler;
        private final String m_filename;
        private final File m_file;

        private FileOutputStream m_fos = null;
        private Exception m_failureReason = null;

        /**
         * Creates a new {@link UploadHandleImpl} instance.
         */
        public UploadHandleImpl(GenericUploadHandler handler, File file) {
            m_handler = handler;
            m_filename = file.getName();
            m_file = file;
            m_fos = null;
        }

        @Override
        public void cleanup() {
            m_file.delete();
            silentlyClose(m_fos);
            m_fos = null;
        }

        @Override
        public void close() throws IOException {
            if (m_fos != null) {
                m_fos.close();
            }
        }

        @Override
        public Exception getFailureReason() {
            return m_failureReason;
        }

        @Override
        public File getFile() {
            return m_file;
        }

        @Override
        public String getFilename() {
            return m_filename;
        }

        @Override
        public OutputStream getOutputStream() {
            return m_fos;
        }

        @Override
        public boolean isInterrupted() {
            return (m_fos == null);
        }

        @Override
        public boolean isSuccessful() {
            return (m_failureReason == null);
        }

        @Override
        public boolean listenProgress() {
            return true;
        }

        @Override
        public void onProgress(StreamingProgressEvent event) {
            m_handler.updateProgress(event.getBytesReceived(), event.getContentLength());
        }

        @Override
        public void streamingFailed(StreamingErrorEvent event) {
            uploadFailed(event.getException());
            m_handler.handleUploadFailure(m_filename, event.getException());
        }

        @Override
        public void streamingFinished(StreamingEndEvent event) {
            m_handler.handleUploadSuccess(m_filename);
        }

        @Override
        public void streamingStarted(StreamingStartEvent event) {
            startUpload();

            if (m_fos != null) {
                m_handler.uploadStarted(this);
            }
        }

        /**
         * Starts the upload by creating an output stream to the temporary file.
         */
        void startUpload() {
            try {
                m_fos = new FileOutputStream(m_file);
            }
            catch (FileNotFoundException exception) {
                // This can only happen in theory...
                m_failureReason = exception;
                m_fos = null;
            }
        }

        void uploadFailed(Exception reason) {
            m_failureReason = reason;
        }
    }

    /**
     * Imports a local bundle (already contained in the OBR) bundle.
     * 
     * @param artifactURL
     *            the URL of the artifact to import, cannot be <code>null</code>.
     * @return the imported artifact object, never <code>null</code>.
     * @throws IOException
     *             in case an I/O exception has occurred.
     */
    public static ArtifactObject importLocalBundle(ArtifactRepository artifactRepository, File artifact) throws IOException {
        URL url = artifact.toURI().toURL();
        if (!artifactRepository.recognizeArtifact(url)) {
            throw new IOException("Artifact " + artifact.getName() + " not recognized!");
        }
        return importLocalBundle(artifactRepository, url);
    }

    /**
     * Imports a local bundle (already contained in the OBR) bundle.
     * 
     * @param artifactURL
     *            the URL of the artifact to import, cannot be <code>null</code>.
     * @return the imported artifact object, never <code>null</code>.
     * @throws IOException
     *             in case an I/O exception has occurred.
     */
    public static ArtifactObject importLocalBundle(ArtifactRepository artifactRepository, URL url) throws IOException {
        return artifactRepository.importArtifact(url, false /* upload */);
    }

    /**
     * Imports a remote bundle by uploading it to the OBR.
     * 
     * @param artifactURL
     *            the URL of the artifact to import, cannot be <code>null</code>.
     * @return the imported artifact object, never <code>null</code>.
     * @throws IOException
     *             in case an I/O exception has occurred.
     */
    public static ArtifactObject importRemoteBundle(ArtifactRepository artifactRepository, File artifact) throws IOException {
        URL url = artifact.toURI().toURL();
        if (!artifactRepository.recognizeArtifact(url)) {
            throw new IOException("Artifact " + artifact.getName() + " not recognized!");
        }
        return artifactRepository.importArtifact(url, true /* upload */);
    }

    /**
     * Silently closes the given {@link Closeable} implementation, ignoring any errors that come out of the
     * {@link Closeable#close()} method.
     * 
     * @param closable
     *            the closeable to close, can be <code>null</code>.
     */
    private static void silentlyClose(Closeable closable) {
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
