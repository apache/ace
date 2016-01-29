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
import java.util.HashMap;
import java.util.Map;

/**
 * Mini REST client.
 */
public class Client {
    public static final String PROPERTY_FOLLOW_REDIRECTS = "followRedirects";
    public static final String PROPERTY_DEFAULT_CHARSET = "defaultCharset";

    public static Client create() {
        return new Client();
    }

    private final Map<String, Object> m_props;

    private Client() {
        m_props = new HashMap<>();
        m_props.put(PROPERTY_DEFAULT_CHARSET, System.getProperty("file.encoding", "UTF-8"));
        m_props.put(PROPERTY_FOLLOW_REDIRECTS, Boolean.TRUE);
    }

    public String getDefaultCharset() {
        return (String) m_props.get(PROPERTY_DEFAULT_CHARSET);
    }

    public Map<String, Object> getProperties() {
        return m_props;
    }

    public boolean isFollowRedirects() {
        return Boolean.TRUE.equals(m_props.get(PROPERTY_FOLLOW_REDIRECTS));
    }

    public WebResource resource(String path) {
        return resource(URI.create(path));
    }

    public WebResource resource(URI location) {
        return new WebResource(this, location);
    }
}
