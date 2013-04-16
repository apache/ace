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
package org.apache.ace.obr.servlet;

import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.obr.storage.BundleStore;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

/**
 * Provides access to the OBR through a REST-ish API.
 */
public class BundleServlet extends HttpServlet implements ManagedService {
    private static final long serialVersionUID = 1L;

    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";

    private static final int COPY_BUFFER_SIZE = 4096;

    public static final String TEXT_MIMETYPE = "text/plain";

    private volatile DependencyManager m_dm; // injected by Dependency Manager
    private volatile LogService m_log; /* will be injected by dependencymanager */
    private volatile BundleStore m_store; /* will be injected by dependencymanager */
    private volatile AuthenticationService m_authService;

    private volatile String m_servletEndpoint = "/";
    private volatile boolean m_useAuth = false;

    @Override
    public String getServletInfo() {
        return "Apache ACE OBR Servlet";
    }

    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            String useAuthString = (String) settings.get(KEY_USE_AUTHENTICATION);
            if (useAuthString == null
                || !("true".equalsIgnoreCase(useAuthString) || "false".equalsIgnoreCase(useAuthString))) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            boolean useAuth = Boolean.parseBoolean(useAuthString);
            m_useAuth = useAuth;
            
            m_servletEndpoint = (String) settings.get("org.apache.ace.server.servlet.endpoint");
            if(m_servletEndpoint == null){
                m_servletEndpoint = "/";
            }
            if(!m_servletEndpoint.startsWith("/")){
                m_servletEndpoint = "/" + m_servletEndpoint;
            }
            if(!m_servletEndpoint.endsWith("/")){
                m_servletEndpoint = m_servletEndpoint + "/";
            }
        }
        else {
            m_useAuth = false;
        }
    }

    /**
     * Called by Dependency Manager upon initialization of this component.
     * 
     * @param comp the component to initialize, cannot be <code>null</code>.
     */
    protected void init(Component comp) {
        comp.add(m_dm.createServiceDependency()
            .setService(AuthenticationService.class)
            .setRequired(m_useAuth)
            .setInstanceBound(true)
            );
    }

    /**
     * Responds to POST requests sent to http://host:port/obr by writing the received data to the bundle store and
     * returning the persistent location. Will send out a response that contains one of the following status codes:
     * <ul>
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - if no resource was specified</li>
     * <li><code>HttpServletResponse.SC_CONFLICT</code> - if the resource already exists</li>
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - if there was a problem storing the resource</li>
     * <li><code>HttpServletResponse.SC_CREATED</code> - if all went fine</li>
     * </ul>
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {

        String fileName = request.getParameter("filename");
        try {
            String storePath = m_store.put(request.getInputStream(), fileName);
            if (storePath != null) {
                sendCreated(request, response, storePath);
            }
            else {
                sendResponse(response, SC_CONFLICT);
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Exception handling request: " + request.getRequestURL(), e);
            sendResponse(response, SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Responds to DELETE requests sent to http://host:port/obr/resource by deleting the file specified.
     * Will send out a response that contains one of the following status codes:
     * <br>
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - if no resource was specified
     * <li><code>HttpServletResponse.SC_NOT_FOUND</code> - if the specified resource did not exist
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - if there was a problem deleting the resource
     * <li><code>HttpServletResponse.SC_OK</code> - if all went fine
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        if ((path == null) || (path.length() <= 1)) {
            sendResponse(response, SC_BAD_REQUEST);
        }
        else {
            // Remove leading slash...
            String id = path.substring(1);
            try {
                if (m_store.remove(id)) {
                    sendResponse(response, SC_OK);
                }
                else {
                    sendResponse(response, SC_NOT_FOUND);
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_WARNING, "Exception handling request: " + request.getRequestURL(), e);
                sendResponse(response, SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Responds to GET requests sent to http://host:port/obr/resource with a stream to the specified filename.
     * Will send out a response that contains one of the following status codes:
     * <br>
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - if no resource is specified
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - if there was a problem storing the resource
     * <li><code>HttpServletResponse.SC_OK</code> - if all went fine
     * <br>
     * The response will only contain the data of the requested resource if the status code of the response is
     * <code>HttpServletResponse.SC_OK</code>.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        if ((path == null) || (path.length() <= 1)) {
            sendResponse(response, SC_BAD_REQUEST);
        }
        else {
            // Remove leading slash...
            String id = path.substring(1);

            ServletOutputStream output = null;
            InputStream fileStream = null;
            try {
                fileStream = m_store.get(id);
                if (fileStream == null) {
                    sendResponse(response, HttpServletResponse.SC_NOT_FOUND);
                }
                else {
                    // send the bundle as stream to the caller
                    response.setContentType(TEXT_MIMETYPE);

                    output = response.getOutputStream();
                    byte[] buffer = new byte[COPY_BUFFER_SIZE];
                    for (int bytes = fileStream.read(buffer); bytes != -1; bytes = fileStream.read(buffer)) {
                        output.write(buffer, 0, bytes);
                    }
                }
            }
            catch (EOFException ex) {
                // ACE-260: lower log-level of this exception; as it is probably because the remote hung up early...
                m_log.log(LogService.LOG_DEBUG, "EOF Exception in request: " + request.getRequestURL()
                    + "; probably the remote hung up early.");
            }
            catch (IOException ex) {
                // ACE-260: all other exception are logged, as we might have a possible resource leak...
                m_log.log(LogService.LOG_WARNING, "Exception in request: " + request.getRequestURL(), ex);
                sendResponse(response, SC_INTERNAL_SERVER_ERROR);
            }
            finally {
                closeSafely(fileStream, request);
                closeSafely(output, request);
            }
        }
    }
    
    /**
     * Responds to HEAD requests sent to http://host:port/obr/resource with a stream to the specified filename.
     * Will send out a response that contains one of the following status codes:
     * <br>
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - if no resource is specified
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - if there was a problem storing the resource
     * <li><code>HttpServletResponse.SC_NOT_FOUND</code> - if the requested resource does not exist
     * <li><code>HttpServletResponse.SC_OK</code> - if all went fine
     * <br>
     * The response is empty in all situations.
     */
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        if ((path == null) || (path.length() <= 1)) {
            sendResponse(response, SC_BAD_REQUEST);
        }
        else {
            // Remove leasing slash...
            String id = path.substring(1);

            ServletOutputStream output = null;
            InputStream fileStream = null;
            try {
            	// TODO extend the store with an exists method???
                fileStream = m_store.get(id);
                if (fileStream == null) {
                    sendResponse(response, HttpServletResponse.SC_NOT_FOUND);
                }
                else {
                    sendResponse(response, HttpServletResponse.SC_OK);
                }
            }
            catch (IOException ex) {
                // ACE-260: all other exception are logged, as we might have a possible resource leak...
                m_log.log(LogService.LOG_WARNING, "Exception in request: " + request.getRequestURL(), ex);
                sendResponse(response, SC_INTERNAL_SERVER_ERROR);
            }
            finally {
                closeSafely(fileStream, request);
                closeSafely(output, request);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
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
                m_log.log(LogService.LOG_INFO, "Authentication failure!");
            }
            return (user != null);
        }
        return true;
    }

    private void closeSafely(Closeable resource, HttpServletRequest request) {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (EOFException ex) {
                // ACE-260: lower log-level of this exception; as it is probably because the remote hung up early...
                m_log.log(LogService.LOG_DEBUG, "EOF Exception trying to close stream: " + request.getRequestURL()
                    + "; probably the remote hung up early.");
            }
            catch (Exception ex) {
                // ACE-260: all other exception are logged, as we might have a possible resource leak...
                m_log.log(LogService.LOG_WARNING, "Exception trying to close stream: " + request.getRequestURL(), ex);
            }
        }
    }

    // send a created response with location header
    private void sendCreated(HttpServletRequest request, HttpServletResponse response, String relativePath) {
        StringBuilder locationBuiler = new StringBuilder(request.getScheme()).append("://").append(request.getServerName());
        boolean ignorePort = (request.getScheme().equals("http") && request.getServerPort() == 80) | (request.getScheme().equals("https") && request.getServerPort() == 443);
        if(!ignorePort){
            locationBuiler.append(":" + request.getServerPort());
        }
        locationBuiler.append(m_servletEndpoint).append(relativePath);
        response.setHeader("Location", locationBuiler.toString());
        response.setStatus(SC_CREATED);
    }

    // send a response with the specified status code
    private void sendResponse(HttpServletResponse response, int statusCode) {
        sendResponse(response, statusCode, "");
    }

    // send a response with the specified status code and description
    private void sendResponse(HttpServletResponse response, int statusCode, String description) {
        try {
            response.sendError(statusCode, description);
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_WARNING, "Unable to send response with status code '" + statusCode + "'", e);
        }
    }
}