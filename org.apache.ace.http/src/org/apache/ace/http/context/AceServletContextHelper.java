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
package org.apache.ace.http.context;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;

import java.io.IOException;
import java.util.Dictionary;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.useradmin.User;

public class AceServletContextHelper extends ServletContextHelper implements ManagedService {

    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    private static final String KEY_CONTEXT_PATH = "context.path";

    private static final String DEFAULT_CONTEXT_PATH = "/";

    private volatile DependencyManager m_dependencyManager;
    private volatile Component m_component;
    private volatile AuthenticationService m_authenticationService;

    private volatile boolean m_useAuth;

    public AceServletContextHelper() {
        super();
    }

    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return authenticate(request);
    }

    /**
     * Called by Dependency Manager upon initialization of this component.
     * 
     * @param comp
     *            the component to initialize, cannot be <code>null</code>.
     */
    protected void init() {
        m_component.add(m_dependencyManager.createServiceDependency()
            .setService(AuthenticationService.class)
            .setRequired(m_useAuth));
    }

    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        boolean useAuth = false;
        String contextPath = DEFAULT_CONTEXT_PATH;

        if (settings != null) {
            Object value = settings.get(KEY_USE_AUTHENTICATION);
            if (value == null || "".equals(value)) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            useAuth = Boolean.parseBoolean(value.toString());

            value = settings.get(KEY_CONTEXT_PATH);
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

        m_useAuth = useAuth;
        updateContextPath(contextPath);
    }

    private void updateContextPath(String pattern) {
        Dictionary<Object, Object> serviceProperties = m_component.getServiceProperties();
        if (!pattern.equals(serviceProperties.get(HTTP_WHITEBOARD_CONTEXT_PATH))) {
            serviceProperties.put(HTTP_WHITEBOARD_CONTEXT_PATH, pattern);
            m_component.setServiceProperties(serviceProperties);
        }
    }

    /**
     * Authenticates, if needed the user with the information from the given request.
     * 
     * @param request
     *            The request to obtain the credentials from, cannot be <code>null</code>.
     * @return <code>true</code> if the authentication was successful, <code>false</code> otherwise.
     */
    private boolean authenticate(HttpServletRequest request) {
        if (m_useAuth) {
            User user = m_authenticationService.authenticate(request);

            request.setAttribute("org.apache.ace.authentication.user", user);

            return user != null;
        }
        return true;
    }

}
