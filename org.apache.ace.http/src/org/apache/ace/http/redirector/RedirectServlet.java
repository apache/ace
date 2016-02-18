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
package org.apache.ace.http.redirector;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.io.IOException;
import java.util.Dictionary;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;

/**
 * Servlet that will redirect from a source URL to a target URL. If you append ?show to the source URL, it will instead
 * show you the redirect (and you can still click on the target URL).
 */
public class RedirectServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;

    public static final String REDIRECT_URL_KEY = "org.apache.ace.webui.vaadin.redirect";

    private final Object LOCK = new Object();

    private volatile ServiceRegistration<?> m_registration;

    private String m_redirectURL;
    private String m_sourceURL;

    public RedirectServlet(Dictionary<String, ?> properties) throws ConfigurationException {
        setup(properties);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String src;
        String url;
        synchronized (LOCK) {
            src = m_sourceURL;
            url = m_redirectURL;
        }
        if (req.getParameter("show") != null) {
            resp.setContentType("text/html");
            resp.getWriter().println("<h1>Http Redirector</h1><p>Redirect configured from " + src + " to <a href=\"" + url + "\">" + url + "</a></p>");
        }
        else {
            resp.sendRedirect(url);
        }
    }

    public void update(Dictionary<String, ?> properties) throws ConfigurationException {
        setup(properties);
        m_registration.setProperties(properties);
    }

    private void setup(Dictionary<String, ?> properties) throws ConfigurationException {
        synchronized (LOCK) {
            m_redirectURL = (String) properties.get(REDIRECT_URL_KEY);
            m_sourceURL = (String) properties.get(HTTP_WHITEBOARD_SERVLET_PATTERN);
            if (m_sourceURL == null) {
                throw new ConfigurationException(HTTP_WHITEBOARD_SERVLET_PATTERN, "needs to be specified");
            }
            if (m_redirectURL == null) {
                throw new ConfigurationException(REDIRECT_URL_KEY, "needs to be specified");
            }
        }
    }
}
