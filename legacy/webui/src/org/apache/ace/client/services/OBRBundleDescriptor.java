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
package org.apache.ace.client.services;

import java.io.Serializable;

/**
 * Describes a bundle that currently resides in an OBR
 */
public class OBRBundleDescriptor implements Serializable {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 4498575423321448776L;
    
    private String m_name;
    private String m_url; // We have to use this as a URL, unfortunately. We could try translating it to Google's form
    
    public OBRBundleDescriptor() {}

    public OBRBundleDescriptor(String name, String url) {
        m_name = name;
        m_url = url;
    }

    public String getName() {
        return m_name;
    }
    
    public String getUrl() {
        return m_url;
    }
    
    @Override
    public String toString() {
        return getName();
    }
}
