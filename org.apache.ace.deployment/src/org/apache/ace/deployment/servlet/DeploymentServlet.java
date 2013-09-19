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
package org.apache.ace.deployment.servlet;

import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.deployment.processor.DeploymentProcessor;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

/**
 * The DeploymentServlet class provides in a list of versions available for a target and a stream
 * of data containing the DeploymentPackage (or fix package) for a specific target and version.
 */
public class DeploymentServlet extends HttpServlet implements ManagedService {
    private static final long serialVersionUID = 1L;

    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";

    public static final String CURRENT = "current";
    public static final String PROCESSOR = "processor";
    public static final String VERSIONS = "versions";
    public static final String DP_MIMETYPE = "application/vnd.osgi.dp";
    public static final String TEXT_MIMETYPE = "text/plain";
    
    private final ConcurrentMap<String, DeploymentProcessor> m_processors = new ConcurrentHashMap<String, DeploymentProcessor>();
    
    // injected by Dependency Manager
    private volatile DependencyManager m_dm; 
    private volatile LogService m_log;
    private volatile StreamGenerator m_streamGenerator;
    private volatile DeploymentProvider m_provider;
    private volatile AuthenticationService m_authService;

    private volatile boolean m_useAuth = false;

    /**
     * Responds to GET requests sent to this endpoint, the response depends on the requested path:
     * <li>http://host/endpoint/targetid/versions/ returns a list of versions available for the specified target
     * <li>http://host/endpoint/targetid/versions/x.y.z returns a deployment package stream for the specified target and version
     *
     * The status code of the response can be one of the following:
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - If no target is specified or the request is malformed in a different way.
     * <li><code>HttpServletResponse.SC_NOT_FOUND</code> - If the specified target or version does not exist.
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - If there was a problem processing the request.
     * <li><code>HttpServletResponse.SC_OK</code> - If all went fine
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String[] pathElements = verifyAndGetPathElements(request.getPathInfo());
            String targetID = pathElements[1];
            List<String> versions = getVersions(targetID);
            int numberOfElements = pathElements.length;

            if (numberOfElements == 3) {
                handleVersionsRequest(versions, response);
            }
            else {
                String version = pathElements[3];
                handlePackageDelivery(targetID, version, versions, request, response);
            }
        }
        catch (AceRestException e) {
            m_log.log(LogService.LOG_WARNING, e.getMessage(), e);
            e.handleAsHttpError(response);
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

    /**
     * Serve the case where requested path is like:
     * http://host/endpoint/targetid/versions/ returns a list of versions available for the specified target
     *
     * @param versions versions to be put into response
     * @param response response object.
     */
    private void handleVersionsRequest(List<String> versions, HttpServletResponse response) throws AceRestException {
        ServletOutputStream output = null;

        response.setContentType(TEXT_MIMETYPE);
        try {
            output = response.getOutputStream();
            for (String version : versions) {
                output.print(version);
                output.print("\n");
            }
        }
        catch (IOException e) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
        }
        finally {
            tryClose(output);
        }
    }

    private void handlePackageDelivery(String targetID, String version, List<String> versions, HttpServletRequest request, HttpServletResponse response) throws AceRestException {

        ServletOutputStream output = null;

        try {
            // Wrap response to add support for range requests
            response = new ContentRangeResponseWrapper(request, response);

            if (!versions.contains(version)) {
                throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown version (" + version + ")");
            }
            String current = request.getParameter(CURRENT);
            String processor = request.getParameter(PROCESSOR);

            InputStream inputStream;
            if (current != null) {
                inputStream = m_streamGenerator.getDeploymentPackage(targetID, current, version);
            }
            else {
                inputStream = m_streamGenerator.getDeploymentPackage(targetID, version);
            }

            if (processor != null) {
                DeploymentProcessor deploymentProcessor = m_processors.get(processor);
                if (deploymentProcessor != null) {
                    deploymentProcessor.process(inputStream, request, response);
                    return;
                }
                else {
                    throw new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not find a deployment processor called: " + processor);
                }
            }
            response.setContentType(DP_MIMETYPE);
            output = response.getOutputStream();
            byte[] buffer = new byte[1024 * 32];
            for (int bytesRead = inputStream.read(buffer); bytesRead != -1; bytesRead = inputStream.read(buffer)) {
                output.write(buffer, 0, bytesRead);
            }
        }
        catch (IllegalArgumentException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid").initCause(e);
        }
        catch (IOException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not deliver package").initCause(e);
        }
        finally {
            tryClose(output);
        }
    }

    private List<String> getVersions(String targetID) throws AceRestException {
        try {
            return m_provider.getVersions(targetID);
        }
        catch (IllegalArgumentException iae) {
            throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown target (" + targetID + ")");
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_WARNING, "Error getting available versions.", ioe);
            throw new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error getting available versions.");
        }
    }

    private void tryClose(OutputStream output) {
        try {
            if (output != null) {
                output.close();
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request. ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Make sure the path is valid.
     * Also returns the splited version of #path.
     *
     * @param path http request path
     *
     * @return splitted version of #path. Split delim is "/"
     *
     * @throws org.apache.ace.deployment.servlet.AceRestException if path is not valid or cannot be processed.
     */
    private String[] verifyAndGetPathElements(String path) throws AceRestException {
        if (path == null) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
        }
        String[] elements = path.split("/");
        int numberOfElements = elements.length;

        if ((numberOfElements < 3) || (numberOfElements > 4) || !VERSIONS.equals(elements[2])) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
        }
        return elements;
    }

    @Override
    public String getServletInfo() {
        return "Ace Deployment Servlet Endpoint";
    }

    @Override
    public void updated(Dictionary settings) throws ConfigurationException {
        if (settings != null) {
            String useAuthString = (String) settings.get(KEY_USE_AUTHENTICATION);
            if (useAuthString == null
                || !("true".equalsIgnoreCase(useAuthString) || "false".equalsIgnoreCase(useAuthString))) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            boolean useAuth = Boolean.parseBoolean(useAuthString);

            m_useAuth = useAuth;
        }
        else {
            m_useAuth = false;
        }
    }

    public void addProcessor(ServiceReference ref, DeploymentProcessor processor) {
        String key = (String) ref.getProperty(PROCESSOR);
        if (key == null) {
            m_log.log(LogService.LOG_WARNING, "Deployment processor ignored, required service property '" + PROCESSOR + "' is missing.");
            return;
        }
        m_processors.putIfAbsent(key, processor);
    }

    public void removeProcessor(ServiceReference ref, DeploymentProcessor processor) {
        String key = (String) ref.getProperty(PROCESSOR);
        if (key == null) {
            // we do not log this here again, we already did so in 'addProcessor'
            return;
        }
        m_processors.remove(key);
    }
}