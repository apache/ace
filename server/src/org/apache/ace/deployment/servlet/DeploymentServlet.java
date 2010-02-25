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
import java.io.OutputStream;
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String[] pathElements = verifyAndGetPathElements(request.getPathInfo());
            String gatewayID = pathElements[1];
            List<String> versions = getVersions(gatewayID);
            int numberOfElements = pathElements.length;

            if (numberOfElements == 3) {
                handleVersionsRequest(versions, response);
            }
            else {
                String version = pathElements[3];
                handlePackageDelivery(gatewayID, version, versions, request, response);
            }

        }
        catch (AceRestException e) {
            m_log.log(LogService.LOG_WARNING, e.getMessage(), e);
            e.handleAsHttpError(response);
        }
    }

    /**
     * Serve the case where requested path is like:
     * http://host/endpoint/gatewayid/versions/ returns a list of versions available for the specified gateway
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

    private void handlePackageDelivery(final String gatewayID, final String version, final List<String> versions, final HttpServletRequest request, final HttpServletResponse response) throws AceRestException {
        ServletOutputStream output = null;

        response.setContentType(TEXT_MIMETYPE);
        try {
            output = response.getOutputStream();
            if (!versions.contains(version)) {
                throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown version (" + version + ")");
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
        catch (IOException e) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
        }
        finally {
            tryClose(output);
        }
    }

    private List<String> getVersions(String gatewayID) throws AceRestException {
        try {
            return m_provider.getVersions(gatewayID);
        }
        catch (IllegalArgumentException iae) {
            throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown gateway (" + gatewayID + ")");
        }
        catch (IOException ioe) {
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
     * @throws AceRestException if path is not valid or cannot be processed.
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

    @SuppressWarnings( "unchecked" )
    public void updated(Dictionary settings) throws ConfigurationException {
        // Nothing needs to be done - handled by DependencyManager
    }
}
