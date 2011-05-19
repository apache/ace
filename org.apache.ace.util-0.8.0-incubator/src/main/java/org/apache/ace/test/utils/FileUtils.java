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
package org.apache.ace.test.utils;

import java.io.File;
import java.io.IOException;

public class FileUtils {

    /**
     * Convenience method for creating temp files.
     * It creates a temp file, and then deletes it. This is done so the same (unique) filename can be used to create a directory.
     *
     * If you use null as the baseDirectoryName, a tempfile is created in the platform specific temp directory.
     * @throws IOException
     */
    public static File createTempFile(File baseDirectory) throws IOException {
        return createTempFile(baseDirectory, "");
    }

    public static File createTempFile(File baseDirectory, String extension) throws IOException {
        File tempFile = File.createTempFile("test", extension, baseDirectory);
        tempFile.delete();
        return tempFile;
    }

    /**
     * Remove the given directory and all it's files and subdirectories
     * @param directory the name of the directory to remove
     */
    public static void removeDirectoryWithContent(File directory) {
        if ((directory == null) || !directory.exists()) {
            return;
        }
        File[] filesAndSubDirs = directory.listFiles();
        for (int i=0; i < filesAndSubDirs.length; i++) {
            File file = filesAndSubDirs[i];
            if (file.isDirectory()) {
                removeDirectoryWithContent(file);
            }
            // else just remove the file
            file.delete();
        }
        directory.delete();
    }
}
