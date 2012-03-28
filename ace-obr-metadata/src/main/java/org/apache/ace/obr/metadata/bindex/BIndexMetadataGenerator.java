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
package org.apache.ace.obr.metadata.bindex;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.osgi.impl.bundle.bindex.Index;
import org.osgi.service.log.LogService;

public class BIndexMetadataGenerator implements MetadataGenerator {

    private static final String INDEX_FILENAME = "repository";
    private static final String INDEX_EXTENSION = ".xml";

    private volatile LogService m_log; /* will be injected by dependencymanager */

    public void generateMetadata(File directory) throws IOException {
        if (directory.isDirectory()) {
            File tempIndex;
            File index = new File(directory, INDEX_FILENAME + INDEX_EXTENSION);
            try {
                tempIndex = File.createTempFile("repo", INDEX_EXTENSION, directory);
                Index.main(new String[] { "-q", "-a", "-r", tempIndex.getAbsolutePath(), directory.getAbsolutePath() });
                renameFile(tempIndex, index);
            }
            catch (IOException e) {
                if (m_log != null) {
                    m_log.log(LogService.LOG_ERROR, "Unable to create temporary file for new repository index.", e);
                }
                throw e;
            }
            catch (InterruptedException e) {
                if (m_log != null) {
                    m_log.log(LogService.LOG_ERROR, "Waiting for next attempt to move temporary repository index failed.", e);
                }
                // Make sure the thread's administration remains correct...
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                if (m_log != null) {
                    m_log.log(LogService.LOG_ERROR, "Failed to generate new repository index.", e);
                }
                throw new IOException("Failed to generate new repository index. + (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * Renames a given source file to a new destination file, using Commons-IO.
     * <p>This avoids the problem mentioned in ACE-155.</p>
     * 
     * @param source the file to rename;
     * @param dest the file to rename to.
     */
    private void renameFile(File source, File dest) throws IOException, InterruptedException {
        boolean renameOK = false;
        int attempts = 0;
        while (!renameOK && (attempts++ < 10)) {
            try {
                renameOK = moveFile(source, dest);
            }
            catch (IOException e) {
                // In all other cases, we assume the source file is still locked and cannot be removed;
                Thread.sleep(1000);
            }
        }

        if (!renameOK) {
            if (m_log != null) {
                m_log.log(LogService.LOG_ERROR, "Unable to move new repository index to it's final location.");
            }
            throw new IOException("Could not move temporary index file (" + source.getAbsolutePath() + ") to it's final location (" + dest.getAbsolutePath() + ")");
        }
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
}