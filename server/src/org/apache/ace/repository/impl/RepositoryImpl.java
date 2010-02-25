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
package org.apache.ace.repository.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryReplication;
import org.apache.ace.repository.SortedRangeSet;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

/**
 * Implementation of an object repository. The object repository holds (big) chunks of data identified by
 * a version. To interact with the repository two interfaces are implemented:
 * <ul>
 *   <li><code>Repository</code> - a read-write interface to the repository, you can commit and checkout versions</li>
 *   <li><code>RepositoryReplication</code> - interface used only for replication of the repository, you can get and put versions</li>
 * </ul>
 * A repository can be either a master or a slave repository. Committing a new version is only possible on a master repository.
 */
public class RepositoryImpl implements RepositoryReplication, Repository {

    private volatile LogService m_log; /* will be injected by dependency manager */

    private final File m_tempDir;
    private File m_dir;
    private boolean m_isMaster;

    /**
     * Creates a new repository.
     *
     * @param dir Directory to be used for storage of the repository data, will be created if needed.
     * @param temp Directory to be used as temp directory, will be created if needed.
     * @param isMaster True if this repository is a master repository, false otherwise.
     * @throws IllegalArgumentException If <code>dir</code> and/or <code>temp</code> could not be created or is not a directory.
     */
    public RepositoryImpl(File dir, File temp, boolean isMaster) {
        m_isMaster = isMaster;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalArgumentException("Repository location is not a valid directory (" + dir.getAbsolutePath() + ")");
        }
        if (!temp.isDirectory() && !temp.mkdirs()) {
            throw new IllegalArgumentException("Temp location is not a valid directory (" + temp.getAbsolutePath() + ")");
        }
        m_tempDir = temp;
        m_dir = dir;
    }

    public InputStream get(long version) throws IOException, IllegalArgumentException {
        return checkout(version);
    }

    public boolean put(InputStream data, long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }
        File file = new File(m_dir, Long.toString(version));
        if (file.exists()) {
            return false;
        }

        // store stream in temp file
        File tempFile = File.createTempFile("repository", null, m_tempDir);
        OutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024];
            int bytes = data.read(buffer);
            while (bytes != -1) {
                fileStream.write(buffer, 0, bytes);
                bytes = data.read(buffer);
            }
        }
        catch (IOException e) {
            String deleteMsg = "";
            if (!tempFile.delete()) {
                deleteMsg = " and was unable to remove temp file " + tempFile.getAbsolutePath();
            }
            m_log.log(LogService.LOG_WARNING, "Error occurred while storing new version in repository" + deleteMsg, e);
            throw e;
        }
        finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                }
                catch (IOException ioe) {
                    // Not much we can do
                }
            }
        }

        // move temp file to final location
        if (!tempFile.renameTo(file)) {
            String deleteMsg = "";
            if (!tempFile.delete()) {
                deleteMsg = " and was unable to remove temp file " + tempFile.getAbsolutePath();
            }
            throw new IOException("Unable to move temp file (" + tempFile.getAbsolutePath() + ") to final location (" + file.getAbsolutePath() + ")" + deleteMsg);
        }

        return true;
    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }
        File file = new File(m_dir, String.valueOf(version));
        return (file.isFile()) ? new FileInputStream(file) : null;
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        if (!m_isMaster) {
            throw new IllegalStateException("Commit is only permitted on master repositories");
        }
        if (fromVersion < 0) {
            throw new IllegalArgumentException("Version must be greater than or equal to 0.");
        }

        long[] versions = getVersions();

        if (versions.length == 0) {
            if (fromVersion == 0) {
                put(data, 1);
                return true;
            } else {
                return false;
            }
        }

        long lastVersion = versions[versions.length - 1];
        if (lastVersion == fromVersion) {
            put(data, fromVersion + 1);
            return true;
        }
        else {
            return false;
        }
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(getVersions());
    }

    /* returns list of all versions present in ascending order */
    private long[] getVersions() throws IOException {
        File[] versions = m_dir.listFiles();
        if (versions == null) {
            throw new IOException("Unable to list version in the store (failed to get the filelist for directory '" + m_dir.getAbsolutePath() + "'");
        }
        long[] results = new long[versions.length];
        for (int i = 0; i < versions.length; i++) {
            try {
                results[i] = Long.parseLong(versions[i].getName());
            }
            catch (NumberFormatException nfe) {
                m_log.log(LogService.LOG_WARNING, "Unable to determine version number for '" + results[i] + "', skipping it.");
            }
        }
        Arrays.sort(results);
        return results;
    }

    /**
     * Updates the repository configuration.
     *
     * @param isMaster True if the repository is a master repository, false otherwise.
     *
     * @throws ConfigurationException If it was impossible to use the new configuration.
     */
    public synchronized void updated(boolean isMaster) throws ConfigurationException {
        m_isMaster = isMaster;
    }
}
