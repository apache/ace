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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.felix.dm.DependencyManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

import com.vaadin.Application;
import com.vaadin.Application.CustomizedSystemMessages;
import com.vaadin.Application.SystemMessages;
import com.vaadin.terminal.gwt.server.AbstractApplicationServlet;
import com.vaadin.terminal.gwt.server.WebApplicationContext;

public class VaadinServlet extends AbstractApplicationServlet implements ManagedService {
    private static final long serialVersionUID = 1L;

    /** denotes what endpoint we're serving this servlet. */
    private static final String KEY_SERVLET_ENDPOINT = "org.apache.ace.server.servlet.endpoint";
    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "ui.authentication.enabled";
    /** Name of the user to log in as. */
    private static final String KEY_USER_NAME = "ui.authentication.user.name";
    /** Password of the user to log in as. */
    private static final String KEY_USER_PASSWORD = "ui.authentication.user.password";
    /** A string denoting the host name of the management service. */
    private static final String KEY_ACE_HOST = "ace.host";
    /** A string denoting the URL to the management server's OBR. */
    private static final String KEY_OBR_URL = "obr.url";
    /** A string denoting the URL to the management server's OBR. */
    private static final String KEY_OBR_XML = "obr.xml";
    /** The timeout (in seconds) of a session. */
    private static final String KEY_SESSION_TIMEOUT = "session.timeout";

    private static final boolean DEFAULT_USE_AUTHENTICATION = false;
    private static final String DEFAULT_USER_NAME = "";
    private static final String DEFAULT_PASSWORD = "";
    private static final URL DEFAULT_ACE_HOST;
    private static final URL DEFAULT_OBR_URL;
    private static final String DEFAULT_OBR_XML = "repository.xml";
    private static final String DEFAULT_SERVLET_ENDPOINT = "/ace";
    private static final int DEFAULT_SESSION_TIMEOUT = 300; // in seconds.

    static {
        try {
            DEFAULT_ACE_HOST = new URL("http://localhost:8080/");
            DEFAULT_OBR_URL = new URL("http://localhost:8080/obr/");
        }
        catch (MalformedURLException exception) {
            throw new RuntimeException("Should never happen!");
        }
    }

    private volatile DependencyManager m_manager;
    private volatile boolean m_useAuth;
    private volatile String m_userName;
    private volatile String m_password;
    private volatile URL m_aceHost;
    private volatile URL m_obrUrl;
    private volatile String m_repositoryXML;
    private volatile String m_servletEndpoint;
    private volatile int m_sessionTimeout;

    /**
     * Creates a new {@link VaadinServlet} instance.
     */
    public VaadinServlet() {
        m_useAuth = DEFAULT_USE_AUTHENTICATION;
        m_userName = DEFAULT_USER_NAME;
        m_password = DEFAULT_PASSWORD;
        m_aceHost = DEFAULT_ACE_HOST;
        m_obrUrl = DEFAULT_OBR_URL;
        m_repositoryXML = DEFAULT_OBR_XML;
        m_servletEndpoint = DEFAULT_SERVLET_ENDPOINT;
        m_sessionTimeout = DEFAULT_SESSION_TIMEOUT;
    }

    @Override
    public void updated(Dictionary dictionary) throws ConfigurationException {
        boolean useAuth = DEFAULT_USE_AUTHENTICATION;
        String userName = DEFAULT_USER_NAME;
        String password = DEFAULT_PASSWORD;
        URL aceHost = DEFAULT_ACE_HOST;
        URL obrUrl = DEFAULT_OBR_URL;
        String repositoryXML = DEFAULT_OBR_XML;
        String servletEndpoint = DEFAULT_SERVLET_ENDPOINT;
        int sessionTimeout = DEFAULT_SESSION_TIMEOUT;

        if (dictionary != null) {
            useAuth = getBoolean(dictionary, KEY_USE_AUTHENTICATION);
            userName = getOptionalString(dictionary, KEY_USER_NAME);
            password = getOptionalString(dictionary, KEY_USER_PASSWORD);
            aceHost = getURL(dictionary, KEY_ACE_HOST);
            obrUrl = getURL(dictionary, KEY_OBR_URL);
            repositoryXML = getOptionalString(dictionary, KEY_OBR_XML);
            servletEndpoint = getOptionalString(dictionary, KEY_SERVLET_ENDPOINT);
            sessionTimeout = getInteger(dictionary, KEY_SESSION_TIMEOUT);
        }

        if ("".equals(repositoryXML)) {
            repositoryXML = DEFAULT_OBR_XML;
        }

        if ("".equals(userName) && !useAuth) {
            throw new ConfigurationException(KEY_USER_NAME, "Missing value; authentication is disabled!");
        }

        m_useAuth = useAuth;
        m_userName = userName;
        m_password = password;
        m_aceHost = aceHost;
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;
        m_servletEndpoint = servletEndpoint;
        m_sessionTimeout = sessionTimeout;
    }

    @Override
    protected Class<? extends Application> getApplicationClass() {
        return VaadinClient.class;
    }

    @Override
    protected WebApplicationContext getApplicationContext(HttpSession session) {
        session.setMaxInactiveInterval(m_sessionTimeout);

        return super.getApplicationContext(session);
    }

    @Override
    protected Application getNewApplication(HttpServletRequest request) throws ServletException {
        return new VaadinClient(m_manager, m_aceHost, m_obrUrl, m_repositoryXML, m_useAuth, m_userName, m_password);
    }

    @Override
    protected SystemMessages getSystemMessages() {
        CustomizedSystemMessages msgs = new CustomizedSystemMessages();
        msgs.setSessionExpiredNotificationEnabled(false);
        msgs.setSessionExpiredURL(m_servletEndpoint.concat("/?sessionTimedOut"));
        return msgs;
    }

    private boolean getBoolean(Dictionary dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value == null || !(value instanceof String)) {
            throw new ConfigurationException(key, "Missing property");
        }
        String valueStr = ((String) value).trim();
        if (!("true".equalsIgnoreCase(valueStr) || "false".equalsIgnoreCase(valueStr))) {
            throw new ConfigurationException(key, "Invalid value!");
        }
        return Boolean.parseBoolean(valueStr);
    }

    private int getInteger(Dictionary dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value == null || !(value instanceof String)) {
            throw new ConfigurationException(key, "Missing property");
        }
        try {
            String valueStr = ((String) value).trim();
            return Integer.parseInt(valueStr);
        }
        catch (NumberFormatException exception) {
            throw new ConfigurationException(key, "Invalid value!");
        }
    }

    private String getOptionalString(Dictionary dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value != null && !(value instanceof String)) {
            throw new ConfigurationException(key, "Missing property");
        }
        return (value == null) ? "" : ((String) value).trim();
    }

    private URL getURL(Dictionary dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value == null || !(value instanceof String)) {
            throw new ConfigurationException(key, "Missing property");
        }
        try {
            return new URL((String) value);
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(key, "Is not a valid URL", e);
        }
    }
}
