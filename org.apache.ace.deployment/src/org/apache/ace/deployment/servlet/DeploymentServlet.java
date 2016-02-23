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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.deployment.processor.DeploymentProcessor;
import org.apache.ace.deployment.provider.ArtifactData;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.provider.OverloadedException;
import org.apache.ace.deployment.streamgenerator.StreamGenerator;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * The DeploymentServlet class provides in a list of versions available for a target and a stream of data containing the
 * DeploymentPackage (or fix package) for a specific target and version.
 */
public class DeploymentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /** HTTP header name used for Deployment Package size estimate, in bytes. */
    private static final String HEADER_DPSIZE = "X-ACE-DPSize";
    /** Multiplication factor for the DP size to account for slight changes in file change due to resource processors. */
    private static final double DPSIZE_FACTOR = 1.1;

    public static final String CURRENT = "current";
    public static final String PROCESSOR = "processor";
    public static final String VERSIONS = "versions";
    public static final String DP_MIMETYPE = "application/vnd.osgi.dp";
    public static final String TEXT_MIMETYPE = "text/plain";

    private final ConcurrentMap<String, DeploymentProcessor> m_processors = new ConcurrentHashMap<>();

    // injected by Dependency Manager
    private volatile LogService m_log;
    private volatile StreamGenerator m_streamGenerator;
    private volatile DeploymentProvider m_provider;

    public void addProcessor(ServiceReference<DeploymentProcessor> ref, DeploymentProcessor processor) {
        String key = (String) ref.getProperty(PROCESSOR);
        if (key != null) {
            m_processors.putIfAbsent(key, processor);
        }
        else {
            m_log.log(LogService.LOG_WARNING, "Deployment processor ignored, required service property '" + PROCESSOR + "' is missing.");
        }
    }

    @Override
    public String getServletInfo() {
        return "Ace Deployment Servlet Endpoint";
    }

    public void removeProcessor(ServiceReference<DeploymentProcessor> ref, DeploymentProcessor processor) {
        String key = (String) ref.getProperty(PROCESSOR);
        // we do not log this here again, we already did so in 'addProcessor'
        if (key != null) {
            m_processors.remove(key);
        }
    }

    /**
     * Responds to GET requests sent to this endpoint, the response depends on the requested path: <li>
     * http://host/endpoint/targetid/versions/ returns a list of versions available for the specified target <li>
     * http://host/endpoint/targetid/versions/x.y.z returns a deployment package stream for the specified target and
     * version
     * 
     * The status code of the response can be one of the following: <li><code>HttpServletResponse.SC_BAD_REQUEST</code>
     * - If no target is specified or the request is malformed in a different way. <li>
     * <code>HttpServletResponse.SC_NOT_FOUND</code> - If the specified target or version does not exist. <li>
     * <code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - If there was a problem processing the request. <li>
     * <code>HttpServletResponse.SC_OK</code> - If all went fine
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        try {
            String[] pathElements = verifyAndGetPathElements(request.getPathInfo());
            String targetID = pathElements[1];
            int numberOfElements = pathElements.length;

            if (numberOfElements == 3) {
                handleVersionsRequest(targetID, response);
            }
            else {
                String version = pathElements[3];
                handlePackageDelivery(targetID, version, request, response);
            }
        }
        catch (AceRestException e) {
            m_log.log(LogService.LOG_WARNING, e.getMessage(), e);
            if (!e.handleAsHttpError(response)) {
                m_log.log(LogService.LOG_ERROR, "Failed to properly notify client of exception!", e);
            }
        }
        catch (OverloadedException oe) {
            throw new ServletException(oe);
        }
    }

    /**
     * Responds to HEAD requests for particular deployment versions by sending back the estimated size of an update.
     */
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String[] pathElements = verifyAndGetPathElements(request.getPathInfo());
            int numberOfElements = pathElements.length;

            if (numberOfElements == 4) {
                String targetID = pathElements[1];
                String version = pathElements[3];

                response.setContentType(DP_MIMETYPE);

                long dpSize = estimateDeploymentPackageSize(request, targetID, version);
                if (dpSize > 0) {
                    response.addHeader(HEADER_DPSIZE, Long.toString(dpSize));
                }
            }
        }
        catch (AceRestException e) {
            m_log.log(LogService.LOG_WARNING, e.getMessage(), e);
            if (!e.handleAsHttpError(response)) {
                m_log.log(LogService.LOG_ERROR, "Failed to properly notify client of exception!", e);
            }
        }
        catch (OverloadedException oe) {
            throw new ServletException(oe);
        }
    }

    private long estimateDeploymentPackageSize(HttpServletRequest request, String targetID, String version) throws IOException, OverloadedException, AceRestException {
        List<String> versions = getVersions(targetID);
        String current = request.getParameter(CURRENT);

        List<ArtifactData> artifacts;
        if (current != null && versions.contains(current)) {
            artifacts = m_provider.getBundleData(targetID, current, version);
        }
        else {
            artifacts = m_provider.getBundleData(targetID, version);
        }

        long dpSize = 0L;
        for (ArtifactData artifactData : artifacts) {
            long size = artifactData.getSize();
            if (size > 0L) {
                dpSize += size;
            }
            else {
                // cannot determine the DP size...
                return -1L;
            }
        }
        return (long) (DPSIZE_FACTOR * dpSize);
    }

    private InputStream getDeploymentPackageStream(String targetID, String version, HttpServletRequest request, List<String> versions) throws IOException {
        String current = request.getParameter(CURRENT);

        // Determine whether we should return a fix-package, or a complete deployment package. Keep in consideration
        // that due to ACE-330, the given current-version can already be purged from the repository...
        if (current != null && versions.contains(current)) {
            m_log.log(LogService.LOG_DEBUG, "Generating deployment fix-package for " + current + " => " + version);

            return m_streamGenerator.getDeploymentPackage(targetID, current, version);
        }

        m_log.log(LogService.LOG_DEBUG, "Generating deployment package for " + version);

        return m_streamGenerator.getDeploymentPackage(targetID, version);
    }

    /**
     * @return the requested {@link DeploymentProcessor}, or <code>null</code> in case none is requested.
     * @throws AceRestException
     *             in case a non-existing deployment processor was requested.
     */
    private DeploymentProcessor getDeploymentProcessor(HttpServletRequest request) throws AceRestException {
        String processor = request.getParameter(PROCESSOR);
        if (processor != null) {
            DeploymentProcessor deploymentProcessor = m_processors.get(processor);
            if (deploymentProcessor == null) {
                throw new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not find a deployment processor called: " + processor);
            }

            m_log.log(LogService.LOG_DEBUG, "Using deployment processor " + processor);

            return deploymentProcessor;
        }

        m_log.log(LogService.LOG_DEBUG, "Using default deployment processor...");

        return new DefaultDeploymentProcessor();
    }

    private List<String> getVersions(String targetID) throws OverloadedException, AceRestException {
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

    private void handlePackageDelivery(String targetID, String version, HttpServletRequest request, HttpServletResponse response) throws OverloadedException, AceRestException {
        List<String> versions = getVersions(targetID);
        if (!versions.contains(version)) {
            throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown version (" + version + ")");
        }

        try {
            // Wrap response to add support for range requests
            response = new ContentRangeResponseWrapper(request, response);
            response.setContentType(DP_MIMETYPE);

            // determine the deployment processor early, as to avoid having to create a complete deployment package in
            // case of a missing/incorrect requested processor...
            DeploymentProcessor deploymentProcessor = getDeploymentProcessor(request);

            // get the input stream to the deployment package...
            InputStream inputStream = getDeploymentPackageStream(targetID, version, request, versions);

            // process and send back the results to the client...
            deploymentProcessor.process(inputStream, request, response);
        }
        catch (IllegalArgumentException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid").initCause(e);
        }
        catch (IOException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not deliver package").initCause(e);
        }
    }

    /**
     * Serve the case where requested path is like: http://host/endpoint/targetid/versions/ returns a list of versions
     * available for the specified target
     * 
     * @param targetID
     *            the target ID for which to return all available versions;
     * @param response
     *            response object.
     */
    private void handleVersionsRequest(String targetID, HttpServletResponse response) throws OverloadedException, AceRestException {
        ServletOutputStream output = null;

        List<String> versions = getVersions(targetID);

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

    private void tryClose(OutputStream output) {
        try {
            if (output != null) {
                output.close();
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request.", e);
        }
    }

    /**
     * Make sure the path is valid. Also returns the splited version of #path.
     * 
     * @param path
     *            http request path
     * 
     * @return splitted version of #path. Split delim is "/"
     * 
     * @throws org.apache.ace.deployment.servlet.AceRestException
     *             if path is not valid or cannot be processed.
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
}
