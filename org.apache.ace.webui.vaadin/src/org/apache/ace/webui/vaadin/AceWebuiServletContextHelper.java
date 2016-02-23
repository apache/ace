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
package org.apache.ace.webui.vaadin;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;

import java.net.URL;
import java.util.Dictionary;

import org.apache.felix.dm.Component;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.http.context.ServletContextHelper;

public class AceWebuiServletContextHelper extends ServletContextHelper implements ManagedService {
    private static final String RESOURCE_PATH = "/VAADIN";

    private static final String KEY_CONTEXT_PATH = "context.path";
    private static final String DEFAULT_CONTEXT_PATH = "/";

    // Managed by Felix DM...
    private volatile BundleContext m_context;
    private volatile Component m_component;

    public AceWebuiServletContextHelper() {
        super();
    }

    @Override
    public URL getResource(String name) {
        URL resource = null;
        // fix for ACE-156
        if (!name.startsWith("/")) {
            name = "/".concat(name);
        }

        String prefix = RESOURCE_PATH.concat("/");
        if (name.startsWith(prefix)) {
            String originalName = name.replace("/ace/", "/reindeer/");

            resource = m_context.getBundle().getEntry(originalName);
            if (resource == null) {
                // try to find the resource in the Vaadin bundle instead
                resource = com.vaadin.Application.class.getResource(originalName);
            }
        }
        return resource;
    }

    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        String contextPath = DEFAULT_CONTEXT_PATH;

        if (settings != null) {
            Object value = settings.get(KEY_CONTEXT_PATH);
            if (value != null) {
                if ("".equals(value)) {
                    throw new ConfigurationException(KEY_CONTEXT_PATH, "Invalid value!");
                }
                contextPath = value.toString();
            }

            if (!"/".equals(contextPath) && (!contextPath.startsWith("/") || contextPath.endsWith("/"))) {
                throw new ConfigurationException(KEY_CONTEXT_PATH, "Invalid value context path, context path should start with a '/' and NOT end with a '/'!");
            }
        }

        updateContextPath(contextPath);
    }

    private void updateContextPath(String pattern) {
        Dictionary<Object, Object> serviceProperties = m_component.getServiceProperties();
        String currentPath = (String) serviceProperties.get(HTTP_WHITEBOARD_CONTEXT_PATH);
        if (!pattern.equals(currentPath)) {
            serviceProperties.put(HTTP_WHITEBOARD_CONTEXT_PATH, pattern);
            m_component.setServiceProperties(serviceProperties);
        }
    }
}
