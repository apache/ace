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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.ace.repository.ext.BackupRepository;

/**
 * A file-based implementation of the Backup Repository, using two files to store the current
 * and backup version.
 */
public class FilebasedBackupRepository implements BackupRepository {
    private static final int COPY_BUFFER_SIZE = 4096;

    private final File m_current;
    private final File m_backup;

    /**
     * Creates a FilebasedBackupRepository. The file objects should point to a correct file,
     * but the files will be created when necessary.
     * @param current A file to store the current revision in.
     * @param backup A file to store a backup version in.
     */
    public FilebasedBackupRepository(File current, File backup) {
        m_current = current;
        m_backup = backup;
    }

    public InputStream read() throws IOException {
        if (!m_current.exists()) {
            return new ByteArrayInputStream(new byte[0]);
        }

        try {
            return new FileInputStream(m_current);
        }
        catch (FileNotFoundException e) {
            throw new IOException("Unable to open file:" + e.getMessage());
        }
    }

    public void write(InputStream data) throws IOException {
        try {
            if (!m_current.exists()) {
                m_current.createNewFile();
            }
        }
        catch (IOException e) {
            throw new IOException("Unable to create file:" + e.getMessage());
        }

        try {
            FileOutputStream out = new FileOutputStream(m_current);
            copy(data, out);
            out.close();
        }
        catch (FileNotFoundException e) {
            throw new IOException("Unable to open file:" + e.getMessage());
        }
    }

    public boolean backup() throws IOException {
        if (!m_current.exists()) {
            return false;
        }
        copy(m_current, m_backup);
        return true;
    }

    public boolean restore() throws IOException {
        if (!m_backup.exists()) {
            return false;
        }
        copy(m_backup, m_current);
        return true;
    }

    /**
     * Helper function that writes the contents of one file to another.
     * @param source The source file.
     * @param destination The destination file.
     * @throws IOException Thrown when file IO goes wrong.
     */
    private static void copy(File source, File destination) throws IOException {
        if (destination.exists()) {
            destination.delete();
        }
        destination.createNewFile();

        FileOutputStream out = new FileOutputStream(destination);
        FileInputStream in = new FileInputStream(source);

        copy(in, out);
        in.close();
        out.close();
    }

    /**
     * Copies the contents of an input stream to an output stream.
     * @param in The input stream.
     * @param out The output stream.
     * @throws IOException Thrown when the output stream is closed unexpectedly.
     */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            out.flush();
            bytes = in.read(buffer);
        }
    }

    @Override
    public String toString() {
        return "FilebasedBackupRepository[" + m_current + "," + m_backup + "]";
    }
}
