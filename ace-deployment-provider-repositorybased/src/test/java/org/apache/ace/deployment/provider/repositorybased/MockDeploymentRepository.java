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
package org.apache.ace.deployment.provider.repositorybased;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.SortedRangeSet;

public class MockDeploymentRepository implements Repository {

    private String m_range;
    private String m_xmlRepository;

    public MockDeploymentRepository(String range, String xmlRepository) {
        m_range = range;
        m_xmlRepository = xmlRepository;
    }

    /* (non-Javadoc)
     * Magic number version 1, generates an IOException, else return
     * @see org.apache.ace.repository.Repository#checkout(long)
     */
    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version == 1) {
            //throw an IOException!
            throw new IOException("Checkout exception.");
        }
        else {
            return new ByteArrayInputStream(m_xmlRepository.getBytes());
        }
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        // Not used in test
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(m_range);
    }
}
