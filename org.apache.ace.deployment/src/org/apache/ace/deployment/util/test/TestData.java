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
package org.apache.ace.deployment.util.test;

import java.net.URL;
import java.util.jar.Attributes;

import org.apache.ace.deployment.provider.ArtifactData;

import org.osgi.annotation.versioning.ConsumerType;

@ConsumerType
public class TestData implements ArtifactData {
    private final String m_fileName;
    private final String m_symbolicName;
    private final URL m_url;
    private final String m_version;
    private final boolean m_changed;

    public TestData(String fileName, String symbolicName, URL url, String version, boolean changed) {
        m_fileName = fileName;
        m_symbolicName = symbolicName;
        m_url = url;
        m_version = version;
        m_changed = changed;
    }

    @Override
    public boolean hasChanged() {
        return m_changed;
    }

    @Override
    public String getFilename() {
        return m_fileName;
    }

    @Override
    public long getSize() {
        return -1L;
    }
    
    @Override
    public String getSymbolicName() {
        return m_symbolicName;
    }

    @Override
    public URL getUrl() {
        return m_url;
    }

    @Override
    public String getVersion() {
        return m_version;
    }

    public String getDirective() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Attributes getManifestAttributes(boolean fixPackage) {
        Attributes a = new Attributes();
        a.putValue("Bundle-SymbolicName", getSymbolicName());
        a.putValue("Bundle-Version", getVersion());
        return a;
    }

    @Override
    public String getProcessorPid() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isBundle() {
        return true;
    }

    @Override
    public boolean isCustomizer() {
        return false;
    }
}
