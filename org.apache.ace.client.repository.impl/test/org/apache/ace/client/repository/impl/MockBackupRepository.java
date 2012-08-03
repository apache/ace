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
import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.repository.ext.BackupRepository;


public class MockBackupRepository implements BackupRepository {
    private byte[] m_current;
    private byte[] m_backup;

    public boolean backup() throws IOException {
        if (m_current == null) {
            return false;
        }
        m_backup = AdminTestUtil.copy(m_current);
        return true;
    }

    public InputStream read() throws IOException {
        return new ByteArrayInputStream(m_current);
    }

    public boolean restore() throws IOException {
        if (m_backup == null) {
            return false;
        }
        m_current = AdminTestUtil.copy(m_backup);
        return true;
    }

    public void write(InputStream data) throws IOException {
        m_current = AdminTestUtil.copy(data);
    }
    
    public void delete() throws IOException {
    	m_current = null;
    	m_backup = null;
    }
}
