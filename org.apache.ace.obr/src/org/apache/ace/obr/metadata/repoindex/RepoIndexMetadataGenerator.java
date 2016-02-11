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
package org.apache.ace.obr.metadata.repoindex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ace.obr.metadata.MetadataGenerator;
import org.osgi.service.indexer.impl.RepoIndex;
import org.osgi.service.log.LogService;

public class RepoIndexMetadataGenerator implements MetadataGenerator {

    private static final String INDEX_FILENAME = "index";
    private static final String INDEX_EXTENSION = ".xml";

    private volatile LogService m_log; /* will be injected by dependencymanager */

    public void generateMetadata(File directory) throws IOException {
        if (directory.isDirectory()) {
            final File index = new File(directory, INDEX_FILENAME + INDEX_EXTENSION);
            final File tempIndex = File.createTempFile("repo", INDEX_EXTENSION, directory);

            try {
                RepoIndex repoIndex = new RepoIndex(m_log);
                try (FileOutputStream out = new FileOutputStream(tempIndex)) {
                    final Set<File> files = new HashSet<>();
                    Map<String, String> config = new HashMap<>();
                    Files.walkFileTree(directory.toPath(), new SimpleFileVisitor<Path>(){
                        
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                            File file = path.toFile();
                            if (!file.equals(index) && !file.equals(tempIndex)) {
                                files.add(file);
                            }
                            return super.visitFile(path, attrs);
                        }
                        
                    });
                    
                    config.put(RepoIndex.ROOT_URL, directory.getAbsolutePath());
                    config.put(RepoIndex.PRETTY, "true");
                    repoIndex.index(files, out, config);
                }
                
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

        try (FileInputStream fis = new FileInputStream(source);
                        FileOutputStream fos = new FileOutputStream(dest);
                        FileChannel input = fis.getChannel();
                        FileChannel output = fos.getChannel()) {

            long size = input.size();
            long pos = 0;
            while (pos < size) {
                pos += output.transferFrom(input, pos, Math.min(size - pos, bufferSize));
            }
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
}