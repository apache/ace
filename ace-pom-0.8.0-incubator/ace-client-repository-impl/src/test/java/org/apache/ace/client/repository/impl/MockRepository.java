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

import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.Repository;


public class MockRepository implements Repository {
    private byte[] m_repo;
    private int currentVersion = 0;

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version == currentVersion) {
            return new ByteArrayInputStream(m_repo);
        }
        return null;
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        if (fromVersion == currentVersion) {
            currentVersion++;
            m_repo = AdminTestUtil.copy(data);
            return true;
        }
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(new long[] {currentVersion});
    }
}
