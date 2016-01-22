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
package org.apache.ace.processlauncher.itest;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides some convenience methods commonly used in the unit tests of ace-launcher.
 */
public final class TestUtil {

    /**
     * Creates a new {@link TestUtil} instance, not used.
     */
    private TestUtil() {
        // No-op
    }

    /**
     * Returns the name of the running operating system.
     * 
     * @return the OS-name, in lower case.
     */
    public static String getOSName() {
        return System.getProperty("os.name", "").toLowerCase();
    }

    /**
     * Obtains the file denoted by the given path as resource, and treats it as properties file.
     * 
     * @param path the path to the resource to load, cannot be <code>null</code>.
     * @return a properties file, never <code>null</code>.
     * @throws IOException in case of I/O problems reading the properties file;
     * @throws RuntimeException in case the given path is not a valid resource file.
     */
    public static Properties getProperties(String path) throws IOException {
        InputStream is = TestUtil.class.getResourceAsStream(path);
        if (is == null) {
            throw new RuntimeException("File not found: " + path);
        }
        try {
            Properties props = new Properties();
            props.load(is);
            return props;
        }
        finally {
            is.close();
        }
    }

    /**
     * Sleeps for a given amount of milliseconds.
     * 
     * @param delayInMillis the delay to sleep, in milliseconds.
     */
    public static void sleep(int delayInMillis) {
        try {
            Thread.sleep(delayInMillis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reads an entire file denoted by the given argument and returns its content as string.
     * 
     * @param file the file to read, cannot be <code>null</code>.
     * @return the file contents, never <code>null</code>.
     * @throws IOException in case of I/O problems reading the file.
     */
    public static String slurpFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        FileReader fr = new FileReader(file);
        int ch;
        try {
            while ((ch = fr.read()) >= 0) {
                sb.append((char) ch);
            }
        }
        finally {
            fr.close();
        }
        return sb.toString();
    }
    
    /**
     * Creates a unique temporary directory.
     * 
     * @return the unique temporary directory
     */
    public static File createTempDir() throws IOException {

        File baseDir = new File(System.getProperty("java.io.tmpdir"));
        String baseName = System.currentTimeMillis() + "-";

        for (int counter = 0; counter < 1000; counter++) {
            File tempDir = new File(baseDir, baseName + counter);
            if (tempDir.mkdir()) {
                return tempDir;
            }
        }
        throw new IOException("Failed to create tmp directory");
    }
}
