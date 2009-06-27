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

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * The DeploymentServlet class provides in a list of versions available for a gateway and a stream
 * of data containing the DeploymentPackage (or fix package) for a specific gateway and version.
 */
public class DeploymentServlet extends HttpServlet implements ManagedService {

    private static final long serialVersionUID = 1L;

    public static final String CURRENT = "current";
    public static final String VERSIONS = "versions";
    public static final String DP_MIMETYPE = "application/vnd.osgi.dp";
    public static final String TEXT_MIMETYPE = "text/plain";

    private volatile LogService m_log;                  /* injected by dependency manager */
    private volatile StreamGenerator m_streamGenerator; /* injected by dependency manager */
    private volatile DeploymentProvider m_provider;     /* injected by dependency manager */

    /**
     * Responds to GET requests sent to this endpoint, the response depends on the requested path:
     * <li>http://host/endpoint/gatewayid/versions/ returns a list of versions available for the specified gateway
     * <li>http://host/endpoint/gatewayid/versions/x.y.z returns a deployment package stream for the specified gateway and version
     *
     * The status code of the response can be one of the following:
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - If no gateway is specified or the request is malformed in a different way.
     * <li><code>HttpServletResponse.SC_NOT_FOUND</code> - If the specified gateway or version does not exist.
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - If there was a problem processing the request.
     * <li><code>HttpServletResponse.SC_OK</code> - If all went fine
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        if (path == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
            return;
        }

        String[] pathElements = path.split("/");
        int numberOfElements = pathElements.length;

        if ((numberOfElements < 3) || (numberOfElements > 4) || !VERSIONS.equals(pathElements[2])) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
            return;
        }

        String gatewayID = pathElements[1];
        List<String> versions;
        try {
            versions = m_provider.getVersions(gatewayID);
        }
        catch (IllegalArgumentException iae) {
            String description = "Unknown gateway (" + gatewayID + ")";
            m_log.log(LogService.LOG_WARNING, description, iae);
            sendError(response, HttpServletResponse.SC_NOT_FOUND, description);
            return;
        }
        catch (IOException ioe) {
            String description = "Error getting available versions.";
            m_log.log(LogService.LOG_WARNING, description, ioe);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, description);
            return;
        }

        ServletOutputStream output = null;
        try {
            output = response.getOutputStream();
            if (numberOfElements == 3) {
                response.setContentType(TEXT_MIMETYPE);
                for (String version : versions) {
                    output.print(version);
                    output.print("\n");
                }
            }
            else {
                String version = pathElements[3];
                if (!versions.contains(version)) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Unknown version (" + version + ")");
                    return;
                }
                String current = request.getParameter(CURRENT);

                InputStream inputStream;
                if (current != null) {
                    inputStream = m_streamGenerator.getDeploymentPackage(gatewayID, current, version);
                }
                else {
                    inputStream = m_streamGenerator.getDeploymentPackage(gatewayID, version);
                }

                response.setContentType(DP_MIMETYPE);
                byte[] buffer = new byte[1024 * 32];
                for (int bytesRead = inputStream.read(buffer); bytesRead != -1; bytesRead = inputStream.read(buffer)) {
                    output.write(buffer, 0, bytesRead);
                }
            }
        }
        catch (IOException ex) {
            String description = "Problem reading request or response data";
            m_log.log(LogService.LOG_WARNING, description, ex);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, description);
        }
        finally {
            try {
                if (output != null) {
                    output.close();
                }
            }
            catch (Exception ex) {
                m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request: " + request.getRequestURL(), ex);
            }
        }
    }

    // send a response with the specified status code and description
    private void sendError(HttpServletResponse response, int statusCode, String description) {
        m_log.log(LogService.LOG_WARNING, "Deployment request failed: " + description);
        try {
            response.sendError(statusCode, description);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to send error response with status code '" + statusCode + "'", e);
        }
    }

    @Override
    public String getServletInfo() {
        return "LiQ Deployment Servlet Endpoint";
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        // Nothing needs to be done - handled by DependencyManager
    }
}
