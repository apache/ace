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

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.osgi.service.log.LogService;

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
public class ScriptServlet extends HttpServlet {
    private static final long serialVersionUID = -7838800050936438994L;
    private static final String SCRIPT_KEY = "script";
    
    private volatile LogService m_logger;
    private volatile CommandProcessor m_processor;

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
    
}
