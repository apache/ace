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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.Dictionary;
import java.util.Stack;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.apache.ace.obr.storage.BundleStore;
import org.apache.ace.obr.storage.file.constants.OBRFileStoreConstants;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * This BundleStore retrieves the files from the file system. Via the Configurator the relative path is set, and all bundles and
 * the repository.xml should be retrievable from that path (which will internally be converted to an absolute path).
 */
public class BundleFileStore implements BundleStore, ManagedService {

    // matches a valid OSGi version
    private final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(\\.(\\d+)(\\.(\\d+)([\\.-]([\\w-]+))?)?)?");
    
    private static int BUFFER_SIZE = 8 * 1024;
    private static final String REPOSITORY_XML = "repository.xml";

    // injected by dependencymanager
    private volatile MetadataGenerator m_metadata;
    private volatile LogService m_log;

    private volatile long m_dirLastModified;
    private volatile File m_dir;

    /**
     * Checks if the the directory was modified since we last checked. If so, the meta-data generator is called.
     * 
     * @throws IOException If there is a problem synchronizing the meta-data.
     */
    public void synchronizeMetadata() throws IOException {
        File dir = m_dir;
        synchronized (REPOSITORY_XML) {
            long dirLastmodified = getDirLastModified(dir);
            if (dirLastmodified > m_dirLastModified) {
                m_metadata.generateMetadata(dir);
                m_dirLastModified = getDirLastModified(dir);
            }
        }
    }

    public InputStream get(String fileName) throws IOException {
        if (REPOSITORY_XML.equals(fileName)) {
            synchronizeMetadata();
        }
        FileInputStream result = null;
        try {
			result = new FileInputStream(createFile(fileName));
		} catch (FileNotFoundException e) {
			// Resource does not exist; notify caller by returning null...
		}
		return result;
    }

    public String put(InputStream data, String fileName) throws IOException {

        File tempFile = downloadToTempFile(data);

        ResourceMetaData metaData = getBundleMetaData(tempFile);
        if (metaData == null) {
            metaData = getArtifactMetaData(fileName);
        }
        if (metaData == null) {
            throw new IOException("Not a valid bundle and no filename found");
        }

        File storeLocation = getResourceFile(metaData);
        if (storeLocation == null) {
            throw new IOException("Failed to store resource");
        }
        if (storeLocation.exists()) {
            return null;
        }

        moveFile(tempFile, storeLocation);
        String filePath = storeLocation.getAbsolutePath().substring(getWorkingDir().getAbsolutePath().length());
        if(filePath.startsWith(File.separator)){
            filePath = filePath.substring(1);
        }
        return filePath;
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

                m_dir = newDir;
                m_dirLastModified = 0l;
            }
        }
        else {
            // clean up after getting a null as dictionary, as the service is going to be pulled afterwards
            m_dirLastModified = 0l;
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
     * Returns the highest last-modified for the directory by recursively looking at all directories and files.
     * 
     * @param dir
     *            The directory
     * @return the Last-modified
     */
    private long getDirLastModified(File dir) {
        long highest = 0l;
        Stack<File> dirs = new Stack<File>();
        dirs.push(dir);
        while (!dirs.isEmpty()) {
            File pwd = dirs.pop();
            long modified = pwd.lastModified();
            if (modified > highest) {
                highest = modified;
            }
            for (File file : pwd.listFiles()) {
                if (file.isDirectory()) {
                    dirs.push(file);
                    continue;
                }
                modified = file.lastModified();
                if (modified > highest) {
                    highest = modified;
                }
            }
        }
        return highest;
    }

    /**
     * Downloads a given input stream to a temporary file.
     * 
     * @param source the input stream to download;
     * @throws IOException in case of I/O problems.
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
     * Tries extract file metadata from a file assuming it is a valid OSGi bundle.
     * 
     * @param file the file to analyze
     * @return the metadata, or <code>null</code> if the file is not a valid bundle.
     */
    private ResourceMetaData getBundleMetaData(File file) {
        JarInputStream jis = null;
        try {
            jis = new JarInputStream(new FileInputStream(file));
            Manifest manifest = jis.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                if (attributes != null) {
                    String bundleSymbolicName = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
                    String bundleVersion = attributes.getValue(Constants.BUNDLE_VERSION);
                    if (bundleSymbolicName != null) {
                        if (bundleVersion == null) {
                            bundleVersion = "0.0.0";
                        }
                        return new ResourceMetaData(bundleSymbolicName, bundleVersion, "jar");
                    }
                }
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
        finally {
            closeQuietly(jis);
        }
    }
        
    /**
     * Tries extract file metadata from a filename assuming a pattern. The version must be a valid OSGi version. If no
     * version is found the default "0.0.0" is returned.
     * <br/><br/>
     * Filename pattern: <code>&lt;filename&gt;[-&lt;version&gt;][.&lt;extension&gt;]<code>
     * 
     * @param file the fileName to analyze
     * @return the metadata, or <code>null</code> if the file is not a valid bundle.
     */
    ResourceMetaData getArtifactMetaData(String fileName) {
        
        if (fileName == null || fileName.equals("")) {
            return null;
        }

        String symbolicName = null;
        String version = null;
        String extension = null;

        // determine extension
        String[] fileNameParts = fileName.split("\\.");
        if (fileNameParts.length > 1) {
            extension = fileNameParts[fileNameParts.length - 1];
            symbolicName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        else {
            symbolicName = fileName;
        }

        // determine version
        int dashIndex = symbolicName.indexOf('-');
        while (dashIndex != -1 && version == null) {
            String versionCandidate = symbolicName.substring(dashIndex + 1);
            Matcher versionMatcher = VERSION_PATTERN.matcher(versionCandidate);
            if (versionMatcher.matches()) {
                symbolicName = symbolicName.substring(0, dashIndex);
                version = versionCandidate;
            }
            else {
                dashIndex = symbolicName.indexOf('-', dashIndex + 1);                
            }
        }
        
        if (version == null) {
            version = "0.0.0";
        }
        return new ResourceMetaData(symbolicName, version, extension);
    }
    
    /**
     * Encapsulated the store layout strategy by creating the resource file based on the provided meta-data.
     * 
     * @param metaData the meta-data for the resource
     * @return the resource file
     * @throws IOException in case of I/O problems.
     */
    private File getResourceFile(ResourceMetaData metaData) throws IOException {

        File resourceFile = null;
        String[] dirs = metaData.getSymbolicName().split("\\.");
        for (String subDir : dirs) {
            if (resourceFile == null) {
                resourceFile = new File(getWorkingDir(), subDir);
            }
            else {
                resourceFile = new File(resourceFile, subDir);
            }
        }
        if (!resourceFile.exists() && !resourceFile.mkdirs()) {
            throw new IOException("Failed to create store directory");
        }
        
        if (metaData.getExtension() != null && !metaData.getExtension().equals("")) {
            resourceFile = new File(resourceFile, metaData.getSymbolicName() + "-" + metaData.getVersion() + "." + metaData.getExtension());
        }
        else {
            resourceFile = new File(resourceFile, metaData.getSymbolicName() + "-" + metaData.getVersion());
        }
        return resourceFile;
    }

    /**
     * @return the working directory of this file store.
     */
    private File getWorkingDir() {
        return m_dir;
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
    
    /**
     * Wrapper that holds resource meta-data relevant to the store layout.
     *
     */
    static class ResourceMetaData {
        private final String m_bundleSymbolicName;
        private final String m_version;
        private final String m_extension;
        
        public ResourceMetaData(String bundleSymbolicName, String version, String extension) {
            m_bundleSymbolicName = bundleSymbolicName;
            m_version = version;
            m_extension = extension;
        }
        
        public String getSymbolicName() {
            return m_bundleSymbolicName;
        }
        
        public String getVersion() {
            return m_version;
        }
        
        public String getExtension() {
            return m_extension;
        }
    }
}