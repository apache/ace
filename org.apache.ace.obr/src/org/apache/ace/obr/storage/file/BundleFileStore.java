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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Stack;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.metadata.util.ResourceMetaData;
import org.apache.ace.obr.storage.BundleStore;
import org.apache.ace.obr.storage.OBRFileStoreConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * This BundleStore retrieves the files from the file system. Via the Configurator the relative path is set, and all
 * bundles and the index.xml should be retrievable from that path (which will internally be converted to an absolute
 * path).
 */
public class BundleFileStore implements BundleStore, ManagedService {
    private static final String REPOSITORY_XML = "index.xml";
    private static int BUFFER_SIZE = 8 * 1024;

    private final Object m_lock = new Object();
    // injected by dependencymanager
    private volatile MetadataGenerator m_metadata;
    private volatile LogService m_log;

    private volatile String m_dirChecksum;
    private volatile File m_dir;

    /**
     * Checks if the the directory was modified since we last checked. If so, the meta-data generator is called.
     *
     * @throws IOException
     *             If there is a problem synchronizing the meta-data.
     */
    public void synchronizeMetadata() throws IOException {
        synchronized (m_lock) {
            File dir = m_dir;

            if (m_dirChecksum == null || !m_dirChecksum.equals(getDirChecksum(dir))) {
                m_metadata.generateMetadata(dir);
                m_dirChecksum = getDirChecksum(dir);
            }
        }
    }

    @Override
    public boolean exists(String fileName) throws IOException {
        File f = createFile(fileName);
        return f.exists();
    }

    @Override
    public InputStream get(String fileName) throws IOException {
        if (REPOSITORY_XML.equals(fileName)) {
            synchronizeMetadata();
        }

        FileInputStream result = null;
        try {
            result = new FileInputStream(createFile(fileName));
        }
        catch (FileNotFoundException e) {
            // Resource does not exist; notify caller by returning null...
        }

        return result;
    }

    @Override
    public String put(InputStream data, String fileName, boolean replace) throws IOException {
        if (fileName == null) {
            fileName = "";
        }
        File tempFile = downloadToTempFile(data);

        ResourceMetaData metaData = ResourceMetaData.getBundleMetaData(tempFile);
        if (metaData == null) {
            metaData = ResourceMetaData.getArtifactMetaData(fileName);
        }
        if (metaData == null) {
            tempFile.delete();
            throw new IOException("Not a valid bundle and no filename found (filename = " + fileName + ")");
        }

        File storeLocation = getResourceFile(metaData);
        if (storeLocation == null) {
            tempFile.delete();
            throw new IOException("Failed to store resource (filename = " + fileName + ")");
        }

        if (storeLocation.exists()) {
            if (replace || compare(storeLocation, tempFile)) {
                m_log.log(LogService.LOG_DEBUG, "Exact same resource already existed in OBR (filename = " + fileName + ")");
            }
            else {
                m_log.log(LogService.LOG_ERROR, "Different resource with same name already existed in OBR (filename = " + fileName + ")");
                return null;
            }
        }

        moveFile(tempFile, storeLocation);

        String filePath = storeLocation.toURI().toString().substring(getWorkingDir().toURI().toString().length());
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        return filePath;
    }

    /** Compares the contents of two files, returns <code>true</code> if they're exactly the same. */
    private boolean compare(File first, File second) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(first));
        BufferedInputStream bis2 = new BufferedInputStream(new FileInputStream(second));
        int b1, b2;
        try {
            do {
                b1 = bis.read();
                b2 = bis2.read();
                if (b1 != b2) {
                    return false;
                }
            }
            while (b1 != -1 && b2 != -1);
            return (b1 == b2);
        }
        finally {
            if (bis != null) {
                try {
                    bis.close();
                }
                catch (IOException e) {
                }
            }
            if (bis2 != null) {
                try {
                    bis2.close();
                }
                catch (IOException e) {
                }
            }
        }
    }

    @Override
    public boolean remove(String fileName) throws IOException {
        File dir;
        synchronized (m_lock) {
            dir = m_dir;
        }

        File file = createFile(fileName);
        if (file.exists()) {
            if (file.delete()) {
                // deleting empty parent dirs
                while ((file = file.getParentFile()) != null && !file.equals(dir) && file.list().length == 0) {
                    file.delete();
                }
                return true;
            }
            else {
                throw new IOException("Unable to delete file (" + file.getAbsolutePath() + ")");
            }
        }
        return false;
    }

    @Override
    public void updated(Dictionary<String, ?> dict) throws ConfigurationException {
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

                synchronized (m_lock) {
                    m_dir = newDir;
                    m_dirChecksum = "";
                }
            }
        }
    }

    /**
     * Called by dependencymanager upon start of this component.
     */
    protected void start() {
        try {
            synchronizeMetadata();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Could not generate initial meta data for bundle repository");
        }
    }

    /**
     * Computes a magic checksum used to determine whether there where changes in the directory without actually looking
     * into the files or using observation.
     *
     * @param dir
     *            The directory
     * @return The checksum
     */
    private String getDirChecksum(File dir) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            // really should not happen
            m_log.log(LogService.LOG_WARNING, "Unable to get an MD5 digest. Metadata will refresh every ten minutes.", e);
            return "" + (System.currentTimeMillis() / 600000);
        }

        Stack<File> dirs = new Stack<>();
        dirs.push(dir);
        while (!dirs.isEmpty()) {
            File pwd = dirs.pop();
            for (File file : pwd.listFiles()) {
                if (file.isDirectory()) {
                    dirs.push(file);
                    continue;
                }
                // basically we hash the filenames, but...
                // include last-modified to detect touched files
                // include length to work around last-modified rounding issues
                String magic = file.getName() + file.length() + file.lastModified();
                digest.update(magic.getBytes());
            }
        }
        String checksum = new BigInteger(digest.digest()).toString();
        return checksum;
    }

    /**
     * Downloads a given input stream to a temporary file.
     *
     * @param source
     *            the input stream to download;
     * @throws IOException
     *             in case of I/O problems.
     */
    private File downloadToTempFile(InputStream source) throws IOException {
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
            return tempFile;
        }
        finally {
            closeQuietly(fos);
        }
    }

    /**
     * Encapsulated the store layout strategy by creating the resource file based on the provided meta-data.
     *
     * @param metaData
     *            the meta-data for the resource
     * @return the resource file
     * @throws IOException
     *             in case of I/O problems.
     */
    private File getResourceFile(ResourceMetaData metaData) throws IOException {
        File resourceDirectory = getWorkingDir();
        String[] dirs = split(metaData.getSymbolicName());
        for (int i = 0; i < (dirs.length - 1); i++) {
            String subDir = dirs[i];
            resourceDirectory = new File(resourceDirectory, subDir);
        }
        if (!resourceDirectory.exists() && !resourceDirectory.mkdirs()) {
            throw new IOException("Failed to create store directory");
        }

        String name = metaData.getSymbolicName();
        String version = metaData.getVersion();
        if (version != null && !version.equals("") && !version.equals("0.0.0")) {
            name += "-" + version;
        }
        String extension = metaData.getExtension();
        if (extension != null && !extension.equals("")) {
            name += "." + extension;
        }
        return new File(resourceDirectory, name);
    }

    /**
     * Splits a name into parts, breaking at all dots as long as what's behind the dot resembles a Java package name
     * (ie. it starts with a lowercase character).
     *
     * @param name
     *            the name to split
     * @return an array of parts
     */
    public static String[] split(String name) {
        List<String> result = new ArrayList<>();
        int startPos = 0;
        for (int i = 0; i < (name.length() - 1); i++) {
            if (name.charAt(i) == '.') {
                if (Character.isLowerCase(name.charAt(i + 1))) {
                    result.add(name.substring(startPos, i));
                    i++;
                    startPos = i;
                }
                else {
                    break;
                }
            }
        }
        result.add(name.substring(startPos));
        return result.toArray(new String[result.size()]);
    }

    /**
     * @return the working directory of this file store.
     */
    private File getWorkingDir() {
        synchronized (m_lock) {
            return m_dir;
        }
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
     * Creates a {@link File} object with the given file name in the current working directory.
     *
     * @param fileName
     *            the name of the file.
     * @return a {@link File} object, never <code>null</code>.
     * @see #getWorkingDir()
     */
    private File createFile(String fileName) {
        return new File(getWorkingDir(), fileName);
    }
}
