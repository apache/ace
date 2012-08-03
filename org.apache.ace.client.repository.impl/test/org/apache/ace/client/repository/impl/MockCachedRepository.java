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

import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.ext.CachedRepository;


public class MockCachedRepository implements CachedRepository {

    public InputStream checkout(boolean fail) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean commit() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public InputStream getLocal(boolean fail) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean revert() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public void writeLocal(InputStream data) throws IOException {
        // TODO Auto-generated method stub

    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isCurrent() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public long getHighestRemoteVersion() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long getMostRecentVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void deleteLocal() throws IOException {
    	// TODO Auto-generated method stub
    }
}
