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
package org.apache.ace.ma.webstart;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;

import org.apache.ace.discovery.Discovery;

public class SystemPropertyDiscovery implements Discovery {
    private URL m_discovery;

    public SystemPropertyDiscovery() throws MalformedURLException {
        m_discovery = new URL(System.getProperty("gateway.discovery", "http://localhost:8080/"));

        // for debugging purposes, we have an override system
        try {
            m_discovery = new URL(Override.getProperty("gateway.discovery"));
        }
        catch (NoSuchElementException e) {
            // ignore the exception
        }
    }

    public URL discover() {
        return m_discovery;
    }
}
