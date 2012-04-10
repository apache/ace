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
package org.apache.ace.obr.storage.file;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.storage.BundleStore;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * This BundleStore retrieves the files from the file system. Via the Configurator the relative path is set, and all bundles and
 * the repository.xml should be retrievable from that path (which will internally be converted to an absolute path).
 */
public class BundleFileStore implements BundleStore, ManagedService {

    private static int BUFFER_SIZE = 8 * 1024;
    private static final String REPOSITORY_XML = "repository.xml";

    private final Map<String, Long> m_foundFiles = new ConcurrentHashMap<String, Long>();
    private final Object m_dirLock = new Object();

    private volatile MetadataGenerator m_metadata; /* will be injected by dependencymanager */
    private volatile LogService m_log; /* will be injected by dependencymanager */

    /** protected by m_dirLock. */
    private File m_dir;

    public void generateMetadata() throws IOException {
        File dir = getWorkingDir();
        File[] files = dir.listFiles();

        m_metadata.generateMetadata(dir);

        for (File current : files) {
            m_foundFiles.put(current.getAbsolutePath(), current.lastModified() ^ current.length());
        }
    }

    public InputStream get(String fileName) throws IOException {
        if (REPOSITORY_XML.equals(fileName) && directoryChanged(getWorkingDir())) {
            generateMetadata(); // might be called too often
        }
        return new FileInputStream(createFile(fileName));
    }

    public boolean put(String fileName, InputStream data) throws IOException {
        final File file = createFile(fileName);

        boolean success = false;
        if (!file.exists()) {
            try {
                // the reason for first writing to a temporary file is that we want to minimize
                // the window where someone could be looking at a "partial" file that is still being
                // uploaded
                downloadToFile(data, file);
                success = true;
            }
            catch (IOException e) {
                // if anything goes wrong while reading from the input stream or
                // moving the file, delete the temporary file
            }
        }
        return success;
    }

    public boolean remove(String fileName) throws IOException {
        File file = createFile(fileName);

        if (file.exists()) {
            if (file.delete()) {
                return true;
            }
            else {
                throw new IOException("Unable to delete file (" + file.getAbsolutePath() + ")");
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary dict) throws ConfigurationException {
        if (dict != null) {
            String path = (String) dict.get(OBRFileStoreConstants.FILE_LOCATION_KEY);
            if (path == null) {
                throw new ConfigurationException(OBRFileStoreConstants.FILE_LOCATION_KEY, "Missing property");
            }

            File newDir = new File(path);
            File curDir = getWorkingDir();

            if (!newDir.equals(curDir)) {
                if (!newDir.exists()) {
                    newDir.mkdirs();
                }
                else if (!newDir.isDirectory()) {
                    throw new ConfigurationException(OBRFileStoreConstants.FILE_LOCATION_KEY, "Is not a directory: " + newDir);
                }

                synchronized (m_dirLock) {
                    m_dir = newDir;
                }

                m_foundFiles.clear();
            }
        }
        else {
            // clean up after getting a null as dictionary, as the service is going to be pulled afterwards
            m_foundFiles.clear();
        }
    }

    /**
     * Called by dependencymanager upon start of this component.
     */
    protected void start() {
        try {
            generateMetadata();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Could not generate initial meta data for bundle repository");
        }
    }

    @SuppressWarnings("boxing")
    private boolean directoryChanged(File dir) {
        File[] files = dir.listFiles();

        // if number of files changed, create new metadata
        if (files.length != m_foundFiles.size()) {
            return true;
        }

        // iterate over the current files
        for (File current : files) {
            Long modifiedDateAndLengthXOR = m_foundFiles.get(current.getAbsolutePath());
            // if one of the current files is not in the old set of files, create new metadata
            if (modifiedDateAndLengthXOR == null) {
                return true;
            }
            // else if of one of the files the size or the date has been changed, create new metadata
            if ((current.lastModified() ^ current.length()) != modifiedDateAndLengthXOR) {
                return true;
            }
        }

        return false;
    }

    /**
     * Downloads a given input stream to a temporary file and if done, moves it to its final location.
     * 
     * @param source the input stream to download;
     * @param dest the destination to write the downloaded file to.
     * @throws IOException in case of I/O problems.
     */
    private void downloadToFile(InputStream source, File dest) throws IOException {
        File tempFile = File.createTempFile("obr", ".tmp");

        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(tempFile);

            int read;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((read = source.read(buffer)) >= 0) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            fos.close();

            if (!tempFile.renameTo(dest)) {
                if (!moveFile(tempFile, dest)) {
                    throw new IOException("Failed to move file store to its destination!");
                }
            }
        }
        finally {
            closeQuietly(fos);

            tempFile.delete();
        }
    }

    /**
     * @return the working directory of this file store.
     */
    private File getWorkingDir() {
        final File dir;
        synchronized (m_dirLock) {
            dir = m_dir;
        }
        return dir;
    }

    /**
     * Moves a given source file to a destination location, effectively resulting in a rename.
     * 
     * @param source the source file to move;
     * @param dest the destination file to move the file to.
     * @return <code>true</code> if the move succeeded.
     * @throws IOException in case of I/O problems.
     */
    private boolean moveFile(File source, File dest) throws IOException {
        final int bufferSize = 1024 * 1024; // 1MB

        FileInputStream fis = null;
        FileOutputStream fos = null;
        FileChannel input = null;
        FileChannel output = null;

        try {
            fis = new FileInputStream(source);
            input = fis.getChannel();

            fos = new FileOutputStream(dest);
            output = fos.getChannel();

            long size = input.size();
            long pos = 0;
            while (pos < size) {
                pos += output.transferFrom(input, pos, Math.min(size - pos, bufferSize));
            }
        }
        finally {
            closeQuietly(fos);
            closeQuietly(fis);
            closeQuietly(output);
            closeQuietly(input);
        }

        if (source.length() != dest.length()) {
            throw new IOException("Failed to move file! Not all contents from '" + source + "' copied to '" + dest + "'!");
        }

        dest.setLastModified(source.lastModified());

        if (!source.delete()) {
            dest.delete();
            throw new IOException("Failed to move file! Source file (" + source + ") locked?");
        }

        return true;
    }

    /**
     * Safely closes a given resource, ignoring any I/O exceptions that might occur by this.
     * 
     * @param resource the resource to close, can be <code>null</code>.
     */
    private void closeQuietly(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IOException e) {
            // Ignored...
        }
    }

    /**
     * Creates a {@link File} object with the given file name in the current working directory.
     * 
     * @param fileName the name of the file.
     * @return a {@link File} object, never <code>null</code>.
     * @see #getWorkingDir()
     */
    private File createFile(String fileName) {
        return new File(getWorkingDir(), fileName);
    }
}