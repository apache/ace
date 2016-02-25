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
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
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
import com.vaadin.terminal.gwt.server.Constants;
import com.vaadin.terminal.gwt.server.WebApplicationContext;

public class VaadinServlet extends AbstractApplicationServlet implements ManagedService {
    public static final String DEFAULT_SERVLET_ENDPOINT = "/ace";

    private static final long serialVersionUID = 1L;

    /** A boolean denoting whether or not to use production mode in Vaadin. */
    private static final String KEY_USE_PRODUCTION_MODE = "vaadin.productionMode";
    /** An int denoting the timeout in seconds to cache resources. */
    private static final String KEY_RESOURCE_CACHE_TIME = "vaadin.cache.time";
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
    private static final String KEY_CACHE_RATE = "artifacts.cache.rate";
    private static final String KEY_PAGE_LENGTH = "artifacts.page.length";

    private static final boolean DEFAULT_USE_AUTHENTICATION = false;
    private static final int DEFAULT_RESOURCE_CACHE_TIME = 3600;
    private static final boolean DEFAULT_USE_PRODUCTION_MODE = true;
    private static final String DEFAULT_USER_NAME = "";
    private static final String DEFAULT_PASSWORD = "";
    private static final URL DEFAULT_ACE_HOST;
    private static final URL DEFAULT_OBR_URL;
    private static final String DEFAULT_OBR_XML = "index.xml";
    private static final int DEFAULT_SESSION_TIMEOUT = 300; // in seconds.
    private static final double DEFAULT_CACHE_RATE = 1;
    private static final int DEFAULT_PAGE_LENGTH = 100;

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
    private volatile boolean m_useProductionMode;
    private volatile int m_resourceCacheTime;
    private volatile boolean m_useAuth;
    private volatile String m_userName;
    private volatile String m_password;
    private volatile URL m_aceHost;
    private volatile URL m_obrUrl;
    private volatile String m_repositoryXML;
    private volatile int m_sessionTimeout;
    private volatile double m_cacheRate;
    private volatile int m_pageLength;

    /**
     * Creates a new {@link VaadinServlet} instance.
     */
    public VaadinServlet() {
        m_useProductionMode = DEFAULT_USE_PRODUCTION_MODE;
        m_resourceCacheTime = DEFAULT_RESOURCE_CACHE_TIME;
        m_useAuth = DEFAULT_USE_AUTHENTICATION;
        m_userName = DEFAULT_USER_NAME;
        m_password = DEFAULT_PASSWORD;
        m_aceHost = DEFAULT_ACE_HOST;
        m_obrUrl = DEFAULT_OBR_URL;
        m_repositoryXML = DEFAULT_OBR_XML;
        m_sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        m_cacheRate = DEFAULT_CACHE_RATE;
        m_pageLength = DEFAULT_PAGE_LENGTH;
    }

    @Override
    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        boolean useProductionMode = DEFAULT_USE_PRODUCTION_MODE;
        int resourceCacheTime = DEFAULT_RESOURCE_CACHE_TIME;
        boolean useAuth = DEFAULT_USE_AUTHENTICATION;
        String userName = DEFAULT_USER_NAME;
        String password = DEFAULT_PASSWORD;
        URL aceHost = DEFAULT_ACE_HOST;
        URL obrUrl = DEFAULT_OBR_URL;
        String repositoryXML = DEFAULT_OBR_XML;
        int sessionTimeout = DEFAULT_SESSION_TIMEOUT;
        double cacheRate = DEFAULT_CACHE_RATE;
        int pageLength = DEFAULT_PAGE_LENGTH;

        if (dictionary != null) {
            useProductionMode = getBoolean(dictionary, KEY_USE_PRODUCTION_MODE);
            resourceCacheTime = getInteger(dictionary, KEY_RESOURCE_CACHE_TIME);

            useAuth = getBoolean(dictionary, KEY_USE_AUTHENTICATION);
            userName = getOptionalString(dictionary, KEY_USER_NAME);
            password = getOptionalString(dictionary, KEY_USER_PASSWORD);
            aceHost = getURL(dictionary, KEY_ACE_HOST);
            obrUrl = getURL(dictionary, KEY_OBR_URL);
            repositoryXML = getOptionalString(dictionary, KEY_OBR_XML);
            sessionTimeout = getInteger(dictionary, KEY_SESSION_TIMEOUT);

            Double doubleValue = getOptionalDouble(dictionary, KEY_CACHE_RATE);
            if (doubleValue != null) {
                cacheRate = doubleValue.doubleValue();
            }

            Integer intValue = getOptionalInteger(dictionary, KEY_PAGE_LENGTH);
            if (intValue != null) {
                pageLength = intValue.intValue();
            }
        }

        if ("".equals(repositoryXML)) {
            repositoryXML = DEFAULT_OBR_XML;
        }

        if ("".equals(userName) && !useAuth) {
            throw new ConfigurationException(KEY_USER_NAME, "Missing value; authentication is disabled!");
        }

        m_useAuth = useAuth;
        m_useProductionMode = useProductionMode;
        m_resourceCacheTime = resourceCacheTime;
        m_userName = userName;
        m_password = password;
        m_aceHost = aceHost;
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;
        m_sessionTimeout = sessionTimeout;
        m_cacheRate = cacheRate;
        m_pageLength = pageLength;
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        // ACE-472 - allow Vaadin to be put in production mode by means of configuration...
        super.init(new VaadinServletConfig(servletConfig));
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
        return new VaadinClient(m_manager, m_aceHost, m_obrUrl, m_repositoryXML, m_useAuth, m_userName, m_password, m_cacheRate, m_pageLength);
    }

    @Override
    protected SystemMessages getSystemMessages() {
        CustomizedSystemMessages msgs = new CustomizedSystemMessages();
        msgs.setAuthenticationErrorNotificationEnabled(false);
        msgs.setAuthenticationErrorURL(DEFAULT_SERVLET_ENDPOINT.concat("/?authenticationError"));
        msgs.setCommunicationErrorNotificationEnabled(false);
        msgs.setCommunicationErrorURL(DEFAULT_SERVLET_ENDPOINT.concat("/?communicationError"));
        msgs.setCookiesDisabledNotificationEnabled(false);
        msgs.setCookiesDisabledURL(DEFAULT_SERVLET_ENDPOINT.concat("/?cookiesDisabled"));
        msgs.setInternalErrorNotificationEnabled(false);
        msgs.setInternalErrorURL(DEFAULT_SERVLET_ENDPOINT.concat("/?internalError"));
        msgs.setOutOfSyncNotificationEnabled(false);
        msgs.setSessionExpiredNotificationEnabled(false);
        msgs.setSessionExpiredURL(DEFAULT_SERVLET_ENDPOINT.concat("/?sessionTimedOut"));
        return msgs;
    }

    private boolean getBoolean(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
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

    private int getInteger(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
        Integer value = getOptionalInteger(dictionary, key);
        if (value == null) {
            throw new ConfigurationException(key, "Missing property");
        }
        return value.intValue();
    }

    private Double getOptionalDouble(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Double) && !(value instanceof String)) {
            throw new ConfigurationException(key, "Invalid value!");
        }
        if (value instanceof Double) {
            return (Double) value;
        }

        try {
            String valueStr = ((String) value).trim();
            return Double.parseDouble(valueStr);
        }
        catch (NumberFormatException exception) {
            throw new ConfigurationException(key, "Invalid value!");
        }
    }

    private Integer getOptionalInteger(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Integer) && !(value instanceof String)) {
            throw new ConfigurationException(key, "Invalid value!");
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }

        try {
            String valueStr = ((String) value).trim();
            return Integer.parseInt(valueStr);
        }
        catch (NumberFormatException exception) {
            throw new ConfigurationException(key, "Invalid value!");
        }
    }

    private String getOptionalString(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
        Object value = dictionary.get(key);
        if (value != null && !(value instanceof String)) {
            throw new ConfigurationException(key, "Missing property");
        }
        return (value == null) ? "" : ((String) value).trim();
    }

    private URL getURL(Dictionary<String, ?> dictionary, String key) throws ConfigurationException {
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

    private class VaadinServletConfig implements ServletConfig {
        private final ServletConfig m_delegate;

        public VaadinServletConfig(ServletConfig delegate) {
            m_delegate = delegate;
        }

        @Override
        public String getInitParameter(String name) {
            if (Constants.SERVLET_PARAMETER_PRODUCTION_MODE.equals(name)) {
                return Boolean.toString(m_useProductionMode);
            }
            else if (Constants.SERVLET_PARAMETER_RESOURCE_CACHE_TIME.equals(name)) {
                return Integer.toString(m_resourceCacheTime);
            }
            return m_delegate.getInitParameter(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            Set<String> names = new HashSet<>(Collections.list(m_delegate.getInitParameterNames()));
            names.add(Constants.SERVLET_PARAMETER_PRODUCTION_MODE);
            names.add(Constants.SERVLET_PARAMETER_RESOURCE_CACHE_TIME);
            return Collections.enumeration(names);
        }

        @Override
        public ServletContext getServletContext() {
            return m_delegate.getServletContext();
        }

        @Override
        public String getServletName() {
            return "Apache Ace Vaadin Web UI";
        }
    }
}
