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
package org.apache.ace.client.repository.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.repository.ext.impl.FilebasedBackupRepository;
import org.testng.annotations.Test;

public class FilebasedBackupRepositoryTest {

    /**
     * A basic scenario: we write, backup, write again, and revert.
     */
    @Test()
    public void testFilebasedBackupRepository() throws IOException {
        File current = File.createTempFile("testFilebasedBackupRepository", null);
        File backup = File.createTempFile("testFilebasedBackupRepository", null);
        current.deleteOnExit();
        backup.deleteOnExit();

        FilebasedBackupRepository rep = new FilebasedBackupRepository(current, backup);

        byte[] testContent = new byte[] { 'i', 'n', 'i', 't', 'i', 'a', 'l' };

        // write initial content
        rep.write(new ByteArrayInputStream(testContent));

        // read initial content
        InputStream input = rep.read();
        byte[] inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from read: " + new String(inputBytes);

        // backup what's in the repository
        rep.backup();

        // write new content
        byte[] newTestContent = new byte[] { 'n', 'e', 'w' };
        rep.write(new ByteArrayInputStream(newTestContent));

        // read current content
        input = rep.read();
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, newTestContent) : "We got something different than 'new' from read: " + new String(inputBytes);

        // revert to previous (initial) content
        rep.restore();

        // read current content
        input = rep.read();
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from read: " + new String(inputBytes);
    }

}
