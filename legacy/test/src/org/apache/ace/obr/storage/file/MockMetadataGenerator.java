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
package org.apache.ace.obr.storage.file;

import java.io.File;

import org.apache.ace.obr.metadata.MetadataGenerator;

public class MockMetadataGenerator implements MetadataGenerator{

    private boolean m_generated = false;
    private int m_numberOfCalls = 0;

    public void generateMetadata(File metadataFilePath) {
        m_numberOfCalls++;
        m_generated = true;
    }

    public boolean generated() {
        return m_generated;
    }

    public int numberOfCalls() {
        return m_numberOfCalls;
    }
}
