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
import com.vaadin.terminal.gwt.server.AbstractApplicationServlet;
import com.vaadin.terminal.gwt.server.WebApplicationContext;

public class VaadinServlet extends AbstractApplicationServlet implements ManagedService {

    private static final int SESSION_TIMEOUT = 30; // in seconds (so 120 = 2 minutes)
    private static final long serialVersionUID = 1L;
    public static final String PID = "org.apache.ace.webui.vaadin";

    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "ui.authentication.enabled";

    /** Name of the user to log in as. */
    private static final String KEY_USER_NAME = "ui.authentication.user.name";

    /** A string denoting the host name of the management service. */
    private static final String KEY_ACE_HOST = "ace.host";

    /** A string denoting the URL to the management server's OBR. */
    private static final String KEY_OBR_URL = "obr.url";

    /** A string denoting the URL to the management server's OBR. */
    private static final String KEY_OBR_XML = "obr.xml";
    private volatile DependencyManager m_manager;
    private volatile boolean m_useAuth;
    private volatile String m_userName;
    private volatile URL m_aceHost;
    private volatile URL m_obrUrl;
    private volatile String m_repositoryXML;

    @Override
    protected Class<? extends Application> getApplicationClass() {
        return VaadinClient.class;
    }

    @Override
    protected Application getNewApplication(HttpServletRequest request) throws ServletException {
        return new VaadinClient(m_manager, m_aceHost, m_obrUrl, m_repositoryXML, m_useAuth,
            m_userName);
    }

    @Override
    protected WebApplicationContext getApplicationContext(HttpSession session) {
        if (session.getMaxInactiveInterval() != SESSION_TIMEOUT) {
            session.setMaxInactiveInterval(SESSION_TIMEOUT);
        }

        return super.getApplicationContext(session);
    }

    public void updated(Dictionary dictionary) throws ConfigurationException {
        if (dictionary == null) {
            return;
        }
        URL aceHost;
        try {
            String aceHostString = (String) dictionary.get(KEY_ACE_HOST);
            if (aceHostString == null) {
                throw new ConfigurationException(KEY_ACE_HOST, "Missing property");
            }
            aceHost = new URL(aceHostString);
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(KEY_ACE_HOST, "Is not a valid URL", e);
        }

        URL obrUrl;
        try {
            String obrUrlString = (String) dictionary.get(KEY_OBR_URL);
            if (obrUrlString == null) {
                throw new ConfigurationException(KEY_OBR_URL, "Missing property");
            }
            obrUrl = new URL(obrUrlString);
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(KEY_OBR_URL, "Is not a valid URL", e);
        }

        String repositoryXML = (String) dictionary.get(KEY_OBR_XML);
        if ((repositoryXML == null) ||
            (repositoryXML.trim().length() == 0)) {
            repositoryXML = "repository.xml";
        }

        String useAuthString = (String) dictionary.get(KEY_USE_AUTHENTICATION);
        if ((useAuthString == null) ||
            !("true".equalsIgnoreCase(useAuthString) ||
            "false".equalsIgnoreCase(useAuthString))) {
            throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
        }

        boolean useAuth = Boolean.parseBoolean(useAuthString);

        String userNameString = (String) dictionary.get(KEY_USER_NAME);
        if ((userNameString == null) && !useAuth) {
            throw new ConfigurationException(KEY_USER_NAME, "Missing value; authentication is disabled!");
        }

        m_useAuth = useAuth;
        m_userName = userNameString;
        m_aceHost = aceHost;
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;
    }
}
