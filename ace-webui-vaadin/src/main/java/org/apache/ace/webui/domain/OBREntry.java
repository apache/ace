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
package org.apache.ace.webui.domain;

public class OBREntry {
    private final String m_symbolicName;
    private final String m_version;
    private final String m_uri;

    public OBREntry(String symbolicName, String version, String uri) {
        m_symbolicName = symbolicName;
        m_version = version;
        m_uri = uri;
    }

    public String getVersion() {
        return m_version;
    }

    public String getSymbolicName() {
        return m_symbolicName;
    }

    public String getUri() {
        return m_uri;
    }

    @Override
    public int hashCode() {
        return m_uri.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return m_uri.equals(((OBREntry) obj).m_uri);
    }
}