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
package org.apache.ace.gogo.servlet;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

/**
 * Servlet that can execute a Gogo script provided by the caller. Note that this is a generic service that is not
 * actually specific to ACE, it will accept any Gogo script.
 * 
 * Requests to this servlet must include a "script" parameter that contains a valid Gogo script.
 * 
 * Note that commands used in the script must be deployed before the script is executed.
 * 
 * Motivation: provide the ability to script client calls to an ACE server for various automation purposes.
 */
public class ScriptServlet extends HttpServlet implements ManagedService {
    private static final long serialVersionUID = -7838800050936438994L;
    private static final String SCRIPT_KEY = "script";
    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    
    private volatile LogService m_logger;
    private volatile CommandProcessor m_processor;
    private volatile AuthenticationService m_authService;
    
    private boolean m_useAuth = false;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Dictionary<String, String> scriptDefinition = toDictionary(req.getParameterMap());
        respondToScriptRequest(resp, scriptDefinition);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String script = getAsString(req.getInputStream());
        req.getInputStream();
        Dictionary<String, String> scriptDefinition = new Hashtable<>();
        scriptDefinition.put(SCRIPT_KEY, script);
        respondToScriptRequest(resp, scriptDefinition);
    }
    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!authenticate(req)) {
            // Authentication failed; don't proceed with the original request...
            resp.sendError(SC_UNAUTHORIZED);
        } else {
            // Authentication successful, proceed with original request...
            super.service(req, resp);
        }
    }
    /**
     * Authenticates, if needed the user with the information from the given request.
     * 
     * @param request the request to obtain the credentials from, cannot be <code>null</code>.
     * @return <code>true</code> if the authentication was successful, <code>false</code> otherwise.
     */
    private boolean authenticate(HttpServletRequest request) {
        if (m_useAuth) {
            User user = m_authService.authenticate(request);
            if (user == null) {
                m_logger.log(LogService.LOG_INFO, "Authentication failure!");
            }
            return (user != null);
        }
        return true;
    }

    private void respondToScriptRequest(HttpServletResponse resp, Dictionary<String, String> scriptDefinition) throws IOException {
        try {
            executeScript(scriptDefinition);
        }
        catch (Exception e) {
            m_logger.log(LogService.LOG_ERROR, "Unable to execute Gogo script.", e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    };

    private void executeScript(Dictionary<String, String> scriptDefinition) throws Exception {
        String script = scriptDefinition.get(SCRIPT_KEY);
        if (script == null) {
            throw new IllegalArgumentException("Script definition *must* define at least a 'script' property!");
        }

        CommandSession session = m_processor.createSession(System.in, System.out, System.err);
        try {
            Object scriptResult = session.execute(script);
            m_logger.log(LogService.LOG_DEBUG, "Script output:\n" + scriptResult);
        }
        finally {
            session.close();
        }
    }

    private Dictionary<String, String> toDictionary(Map<String, ?> map) {
        Dictionary<String, String> result = new Hashtable<>();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            result.put(entry.getKey(), toString(entry.getValue()));
        }
        return result;
    }

    private String toString(Object value) {
        if (value != null) {
            if (!(value instanceof String)) {
                m_logger.log(LogService.LOG_DEBUG, "Using String value for non-String object: " + value);
            }
            return value.toString();
        }
        return null;
    }
    
    static String getAsString(InputStream is) throws IOException {
        // See <weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html>
        try (Scanner scanner = new Scanner(is, "UTF-8")) {
            scanner.useDelimiter("\\A");

            return scanner.hasNext() ? scanner.next() : null;
        }
    }
    
    @Override
    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String useAuthString = (String) settings.get(KEY_USE_AUTHENTICATION);
            if (useAuthString == null
                || !("true".equalsIgnoreCase(useAuthString) || "false".equalsIgnoreCase(useAuthString))) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            boolean useAuth = Boolean.parseBoolean(useAuthString);
            m_useAuth = useAuth;
        }
    }
}
