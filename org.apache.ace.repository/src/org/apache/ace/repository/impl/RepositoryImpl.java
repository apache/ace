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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryConstants;
import org.apache.ace.repository.RepositoryReplication;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;

/**
 * Implementation of an object repository. The object repository holds (big) chunks of data identified by a version. To
 * interact with the repository two interfaces are implemented:
 * <ul>
 * <li><code>Repository</code> - a read-write interface to the repository, you can commit and checkout versions</li>
 * <li><code>RepositoryReplication</code> - interface used only for replication of the repository, you can get and put
 * versions</li>
 * </ul>
 * A repository can be either a master or a slave repository. Committing a new version is only possible on a master
 * repository.
 */
public class RepositoryImpl implements RepositoryReplication, Repository {

    private volatile LogService m_log; /* will be injected by dependency manager */
    private volatile boolean m_isMaster;
    private volatile long m_limit;

    private final File m_tempDir;
    private final File m_dir;
    private final String m_fileExtension;

    /**
     * Creates a new repository.
     * 
     * @param dir
     *            Directory to be used for storage of the repository data, will be created if needed.
     * @param temp
     *            Directory to be used as temp directory, will be created if needed.
     * @param isMaster
     *            True if this repository is a master repository, false otherwise.
     * @throws IllegalArgumentException
     *             If <code>dir</code> and/or <code>temp</code> could not be created or is not a directory.
     */
    public RepositoryImpl(File dir, File temp, boolean isMaster) {
        this(dir, temp, "", isMaster);
    }

    /**
     * Creates a new repository.
     * 
     * @param dir
     *            Directory to be used for storage of the repository data, will be created if needed.
     * @param temp
     *            Directory to be used as temp directory, will be created if needed.
     * @param isMaster
     *            True if this repository is a master repository, false otherwise.
     * @param limit
     *            The maximum number of versions to store in this repository.
     * @throws IllegalArgumentException
     *             If <code>dir</code> and/or <code>temp</code> could not be created or is not a directory.
     */
    public RepositoryImpl(File dir, File temp, boolean isMaster, long limit) {
        this(dir, temp, "", isMaster, limit);
    }

    /**
     * Creates a new repository.
     * 
     * @param dir
     *            Directory to be used for storage of the repository data, will be created if needed.
     * @param temp
     *            Directory to be used as temp directory, will be created if needed.
     * @param fileExtension
     *            Extension to be used for repository files.
     * @param isMaster
     *            True if this repository is a master repository, false otherwise.
     * @throws IllegalArgumentException
     *             If <code>dir</code> and/or <code>temp</code> could not be created or is not a directory.
     */
    public RepositoryImpl(File dir, File temp, String fileExtension, boolean isMaster) {
        this(dir, temp, fileExtension, isMaster, Long.MAX_VALUE);
    }

    /**
     * Creates a new repository.
     * 
     * @param dir
     *            Directory to be used for storage of the repository data, will be created if needed.
     * @param temp
     *            Directory to be used as temp directory, will be created if needed.
     * @param fileExtension
     *            Extension to be used for repository files.
     * @param isMaster
     *            True if this repository is a master repository, false otherwise.
     * @param limit
     *            The maximum number of versions to store in this repository.
     * @throws IllegalArgumentException
     *             If <code>dir</code> and/or <code>temp</code> could not be created or is not a directory.
     */
    public RepositoryImpl(File dir, File temp, String fileExtension, boolean isMaster, long limit) {
        m_isMaster = isMaster;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IllegalArgumentException("Repository location is not a valid directory (" + dir.getAbsolutePath() + ")");
        }
        if (!temp.isDirectory() && !temp.mkdirs()) {
            throw new IllegalArgumentException("Temp location is not a valid directory (" + temp.getAbsolutePath() + ")");
        }
        if (fileExtension == null) {
            throw new IllegalArgumentException("File extension must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be at least 1, was " + limit);
        }
        m_tempDir = temp;
        m_dir = dir;
        m_fileExtension = fileExtension;
        m_limit = limit;
    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }

        InputStream result = null;
        File file = getFilename(version);
        if (file.isFile()) {
            result = new FileInputStream(file);
        }
        return result;
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
            if (fromVersion != 0) {
                throw new IOException("Repository already changed, cannot commit initial version!");
            }
            return put(data, 1);
        }

        long lastVersion = versions[versions.length - 1];
        if (lastVersion != fromVersion) {
            throw new IOException("Repository already changed, cannot commit version " + fromVersion + "!");
        }

        boolean result = put(data, fromVersion + 1);
        // Make sure we do not exceed our max limit...
        purgeOldFiles(getVersions(), m_limit);

        return result;
    }

    public InputStream get(long version) throws IOException, IllegalArgumentException {
        return checkout(version);
    }

    /**
     * @return the directory in which this repository stores its files.
     */
    public File getDir() {
        return m_dir;
    }

    /**
     * @return the file extension used for this repository.
     */
    public String getFileExtension() {
        return m_fileExtension;
    }

    @Override
    public long getLimit() {
        return m_limit;
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(getVersions());
    }

    public boolean put(InputStream data, long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }
        File file = getFilename(version);
        if (file.exists()) {
            return false;
        }

        // store stream in temp file
        File tempFile = File.createTempFile("repository", null, m_tempDir);
        OutputStream fileStream = null;

        try {
            fileStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int bytes;

            while ((bytes = data.read(buffer)) >= 0) {
                fileStream.write(buffer, 0, bytes);
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
            closeQuietly(fileStream);
        }

        // ACE-421: check whether there's a change in data...
        if (version > 1) {
            File current = getFilename(version - 1);
            if (current.exists() && contentsEqual(current, tempFile)) {
                return false;
            }
        }

        // move temp file to final location
        renameFile(tempFile, file);

        return true;
    }

    /**
     * Updates the repository configuration.
     * 
     * @param isMaster
     *            True if the repository is a master repository, false otherwise.
     * @throws ConfigurationException
     *             If it was impossible to use the new configuration.
     */
    public void updated(boolean isMaster, long limit) throws ConfigurationException {
        if (limit < 1) {
            throw new ConfigurationException(RepositoryConstants.REPOSITORY_LIMIT, "Limit must be at least 1, was " + limit);
        }
        m_isMaster = isMaster;
        if (limit < m_limit) {
            // limit was decreased, we might need to delete some old versions
            try {
                purgeOldFiles(getVersions(), limit);
            }
            catch (IOException e) {
                throw new ConfigurationException(RepositoryConstants.REPOSITORY_LIMIT, "Could not set new limit to " + limit, e);
            }
        }
        m_limit = limit;
    }

    /**
     * Safely closes a given resource, ignoring any I/O exceptions that might occur by this.
     * 
     * @param resource
     *            the resource to close, can be <code>null</code>.
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
     * Tests whether two given files are identical with respect to their length and content.
     * 
     * @param file1
     *            the file to check against;
     * @param file2
     *            the file to check.
     * @return <code>true</code> if both files are equal in length and content, <code>false</code> otherwise.
     */
    private boolean contentsEqual(File file1, File file2) {
        // if lengths are not equal, we're certain that the contents won't be equal...
        if (file1.length() != file2.length()) {
            return false;
        }
        if (file1.getAbsolutePath().equals(file2.getAbsolutePath())) {
            // they are one and the same file...
            return true;
        }

        InputStream is1 = null;
        InputStream is2 = null;

        boolean result = true;

        try {
            is1 = new BufferedInputStream(new FileInputStream(file1));
            is2 = new BufferedInputStream(new FileInputStream(file2));

            int read;
            while ((read = is1.read()) >= 0) {
                int readData = is2.read();
                if (readData != read) {
                    result = false;
                    break;
                }
            }
        }
        catch (IOException e) {
            // Log and ignore...
            m_log.log(LogService.LOG_DEBUG, "Failed to diff files?!", e);
        }
        finally {
            closeQuietly(is1);
            closeQuietly(is2);
        }

        return result;
    }

    private boolean delete(long version) throws IOException, IllegalArgumentException {
        if (version <= 0) {
            throw new IllegalArgumentException("Version must be greater than 0.");
        }
        File file = getFilename(version);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    private File getFilename(long version) {
        return new File(m_dir, String.format("%d%s", version, m_fileExtension));
    }

    /* returns list of all versions present in ascending order */
    private long[] getVersions() throws IOException {
        File[] versions = m_dir.listFiles();
        if (versions == null) {
            throw new IOException("Unable to list version in the store (failed to get the filelist for directory '" + m_dir.getAbsolutePath() + "'");
        }
        long[] results = new long[versions.length];
        for (int i = 0; i < versions.length; i++) {
            String name = versions[i].getName();
            name = name.substring(0, name.length() - m_fileExtension.length());
            try {
                results[i] = Long.parseLong(name);
            }
            catch (NumberFormatException nfe) {
                m_log.log(LogService.LOG_WARNING, "Unable to determine version number for '" + name + "', skipping it.");
            }
        }
        Arrays.sort(results);
        return results;
    }

    /**
     * Moves a given source file to a destination location, effectively resulting in a rename.
     * 
     * @param source
     *            the source file to move;
     * @param dest
     *            the destination file to move the file to.
     * @return <code>true</code> if the move succeeded.
     * @throws IOException
     *             in case of I/O problems.
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

    private void purgeOldFiles(long[] versions, long limit) throws IOException {
        int length = versions.length;
        int index = 0;
        while (length > limit) {
            delete(versions[index]);
            length--;
            index++;
        }
    }

    /**
     * Renames a given source file to a new destination file.
     * <p>
     * This avoids the problem mentioned in ACE-155.<br/>
     * The moveFile method from Commons-IO is not used, as it would mean that we need to include this JAR in several
     * placed for only a few lines of code.
     * </p>
     * 
     * @param source
     *            the file to rename;
     * @param dest
     *            the file to rename to.
     */
    private void renameFile(File source, File dest) throws IOException {
        boolean renameOK = false;
        int attempts = 0;
        while (!renameOK && (attempts++ < 10)) {
            try {
                renameOK = source.renameTo(dest);
                if (!renameOK) {
                    renameOK = moveFile(source, dest);
                }
            }
            catch (IOException e) {
                // In all other cases, we assume the source file is still locked and cannot be removed;
            }
        }

        if (!renameOK) {
            if (m_log != null) {
                m_log.log(LogService.LOG_ERROR, "Unable to move new repository file to it's final location.");
            }
            throw new IOException("Could not move temporary file (" + source.getAbsolutePath() + ") to it's final location (" + dest.getAbsolutePath() + ")");
        }
    }
}
