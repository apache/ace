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
package org.apache.ace.obr.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;

import org.apache.ace.obr.storage.BundleStore;
import org.osgi.service.cm.ConfigurationException;

public class MockBundleStore implements BundleStore {

    private InputStream m_outFile;

    public MockBundleStore(InputStream outFile) {
        m_outFile = outFile;
    }

    public InputStream get(String fileName) throws IOException {
        if (fileName.equals("UnknownFile")) {
            return null;
        }
        return m_outFile;
    }

    public void put(String fileName, OutputStream data) throws IOException {
        // TODO does nothing yet
    }

    @Override
    public boolean put(String fileName, InputStream data) throws IOException {
        if (fileName.equals("NewFile")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(String fileName) throws IOException {
        if (fileName.equals("RemoveMe")) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updated(Dictionary arg0) throws ConfigurationException {
        // TODO does nothing yet
    }
}
