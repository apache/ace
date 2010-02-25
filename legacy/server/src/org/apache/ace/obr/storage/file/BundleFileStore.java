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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private volatile MetadataGenerator m_metadata; /* will be injected by dependencymanager */
    private volatile LogService m_log; /* will be injected by dependencymanager */

    private final Map<String, Long> m_foundFiles = new ConcurrentHashMap<String, Long>();
    private volatile File m_dir;

    @SuppressWarnings("unused")
    private void start() {
        try {
            generateMetadata();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Could not generate initial meta data for bundle repository");
        }
    }

    public InputStream get(String fileName) throws IOException {
        if (fileName.equals("repository.xml") && directoryChanged()) {
            generateMetadata(); // might be called too often
        }
        return new FileInputStream(new File(m_dir, fileName));
    }

    public synchronized boolean put(String fileName, InputStream data) throws IOException {
        File file = new File(m_dir, fileName);
        if (!file.exists()) {
            FileOutputStream output = null;
            File tempFile = null;
            boolean success = false;
            try {
                tempFile = File.createTempFile("obr", ".tmp");
                output = new FileOutputStream(tempFile);
                byte[] buffer = new byte[BUFFER_SIZE];
                for (int count = data.read(buffer); count != -1; count = data.read(buffer)) {
                    output.write(buffer, 0, count);
                }
                success = true;
            }
            finally {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            }
            if (success) {
                tempFile.renameTo(file);
            }
            return success;
        }
        return false;
    }

    public synchronized boolean remove(String fileName) throws IOException {
        File file = new File(m_dir, fileName);
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

    @SuppressWarnings("boxing")
    private boolean directoryChanged() {
        File[] files = m_dir.listFiles();

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

    @SuppressWarnings("boxing")
    public synchronized void generateMetadata() throws IOException {
        File[] files = m_dir.listFiles();
        m_metadata.generateMetadata(m_dir);
        for (File current : files) {
            m_foundFiles.put(current.getAbsolutePath(), current.lastModified() ^ current.length());
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized void updated(Dictionary dict) throws ConfigurationException {
        if (dict != null) {
            String path = (String) dict.get(OBRFileStoreConstants.FILE_LOCATION_KEY);
            if (path == null) {
                throw new ConfigurationException(OBRFileStoreConstants.FILE_LOCATION_KEY, "Missing property");
            }
            File dir = new File(path);
            if (!dir.equals(m_dir)) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                else if (!dir.isDirectory()) {
                    throw new ConfigurationException(OBRFileStoreConstants.FILE_LOCATION_KEY, "Is not a directory: " + dir);
                }
                m_dir = dir;
                m_foundFiles.clear();
            }
        }
        else {
            // clean up after getting a null as dictionary, as the service is going to be pulled afterwards
            m_foundFiles.clear();
        }
    }
}
