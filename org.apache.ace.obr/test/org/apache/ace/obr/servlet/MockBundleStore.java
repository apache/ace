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
import java.util.Dictionary;

import org.apache.ace.obr.storage.BundleStore;
import org.osgi.service.cm.ConfigurationException;

public class MockBundleStore implements BundleStore {

    private InputStream m_outFile;

    public MockBundleStore(InputStream outFile) {
        m_outFile = outFile;
    }

    @Override
    public boolean exists(String filePath) throws IOException {
        if (filePath.equals("UnknownFile")) {
            return false;
        }
        return true;
    }

    public InputStream get(String fileName) throws IOException {
        if (fileName.equals("UnknownFile")) {
            return null;
        }
        return m_outFile;
    }

    public String put(InputStream data, String fileName, boolean replace) throws IOException {
        if (fileName.equals("NewFile")) {
            return "NewFile";
        }
        if (fileName.equals("path/to/file")) {
            return "path/to/file";
        }
        return null;
    }

    public boolean remove(String fileName) throws IOException {
        if (fileName.equals("RemoveMe")) {
            return true;
        }
        if (fileName.equals("path/to/file")) {
            return true;
        }
        return false;
    }

    public void updated(Dictionary<String, ?> dict) throws ConfigurationException {
        // Nop
    }
}
