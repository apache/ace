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

import java.io.File;
import java.io.IOException;

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
                Index.main(new String[] {"-q", "-a", "-r", tempIndex.getAbsolutePath(), directory.getAbsolutePath()});
                // TODO: try to move the index file to it's final location, this can fail if the target
                // file was not released by a third party before we were called (not all platforms support reading and writing
                // to a file at the same time), for now we will try 10 times and throw an IOException if the move has not
                // succeeded by then.
                boolean renameOK = false;
                int attempts = 0;
                while(!renameOK && (attempts < 10)) {
                    index.delete();
                    renameOK = tempIndex.renameTo(index);
                    if (!renameOK) {
                        attempts++;
                        Thread.sleep(1000);
                    }
                }
                if (!renameOK) {
                    m_log.log(LogService.LOG_ERROR, "Unable to move new repository index to it's final location.");
                    throw new IOException("Could not move temporary index file (" + tempIndex.getAbsolutePath() + ") to it's final location (" + index.getAbsolutePath() + ")");
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Unable to create temporary file for new repository index.", e);
                throw e;
            }
            catch (InterruptedException e) {
                m_log.log(LogService.LOG_ERROR, "Waiting for next attempt to move temporary repository index failed.", e);
            }
            catch (Exception e) {
                m_log.log(LogService.LOG_ERROR, "Failed to generate new repository index.", e);
                throw new IOException("Failed to generate new repository index. + (" + e.getMessage() + ")");
            }
        }
    }
}
