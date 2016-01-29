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

package org.apache.ace.client.rest.util;

import java.net.URI;

/**
 * 
 */
public class WebResponse {
    private static URI createURI(String newLocation) {
        if (!newLocation.endsWith("/")) {
            newLocation = newLocation.concat("/");
        }
        return URI.create(newLocation);
    }
    private final int m_status;
    private final URI m_location;

    private final String m_content;

    public WebResponse(String newLocation, int rc, String content) {
        this(createURI(newLocation), rc, content);
    }

    public WebResponse(URI location, int status, String content) {
        m_location = location;
        m_status = status;
        m_content = content;
    }

    public String getContent() {
        return m_content;
    }

    public URI getLocation() {
        return m_location;
    }

    public int getStatus() {
        return m_status;
    }

    @Override
    public String toString() {
        return String.format("%s: %d [%s]", getLocation(), getStatus(), getContent());
    }
}
