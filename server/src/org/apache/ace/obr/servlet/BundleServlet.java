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

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.obr.storage.BundleStore;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

public class BundleServlet extends HttpServlet implements ManagedService {
    public static final String TEXT_MIMETYPE = "text/plain";

    private static final long serialVersionUID = 1L;
    private static final int COPY_BUFFER_SIZE = 1024;

    private volatile LogService m_log; /* will be injected by dependencymanager */
    private volatile BundleStore m_store; /* will be injected by dependencymanager */

    /**
     * Responds to POST requests sent to http://host:port/obr/resource by writing the received data to the bundle store.
     * Will send out a response that contains one of the following status codes:
     * <ul>
     * <li><code>HttpServletResponse.SC_BAD_REQUEST</code> - if no resource was specified</li>
     * <li><code>HttpServletResponse.SC_CONFLICT</code> - if the resource already exists</li>
     * <li><code>HttpServletResponse.SC_INTERNAL_SERVER_ERROR</code> - if there was a problem storing the resource</li>
     * <li><code>HttpServletResponse.SC_OK</code> - if all went fine</li>
     * </ul>
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        String path = request.getPathInfo();
        if ((path == null) || (path.length() <= 1)) {
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST);
        }
        else {
            String id = path.substring(1);
            try {
                if (m_store.put(id, request.getInputStream())) {
                    sendResponse(response, HttpServletResponse.SC_OK);
                }
                else {
                    sendResponse(response, HttpServletResponse.SC_CONFLICT);
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_WARNING, "Exception handling request: " + request.getRequestURL(), e);
                sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
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
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST);
        }
        else {
            String id = path.substring(1);
            try {
                if (m_store.remove(id)) {
                    sendResponse(response, HttpServletResponse.SC_OK);
                }
                else {
                    sendResponse(response, SC_NOT_FOUND);
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_WARNING, "Exception handling request: " + request.getRequestURL(), e);
                sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
            sendResponse(response, HttpServletResponse.SC_BAD_REQUEST);
        }
        else {
            String id = path.substring(1);
            response.setContentType(TEXT_MIMETYPE);

            ServletOutputStream output = null;
            try {
                output = response.getOutputStream();
                InputStream fileStream = null;
                try {
                    fileStream = m_store.get(id);

                    if (fileStream != null) {
                        // send the bundle as stream to the caller
                        byte[] buffer = new byte[COPY_BUFFER_SIZE];
                        for (int bytes = fileStream.read(buffer); bytes != -1; bytes = fileStream.read(buffer)) {
                            output.write(buffer, 0, bytes);
                        }
                    }
                    else {
                        sendResponse(response, HttpServletResponse.SC_NOT_FOUND);
                    }
                }
                finally {
                    if (fileStream != null) {
                        try {
                            fileStream.close();
                        }
                        catch (IOException ioe) {
                            m_log.log(LogService.LOG_WARNING, "Exception closing the stream " + request.getRequestURL(), ioe);
                        }
                    }
                }
            }
            catch (IOException ex) {
                m_log.log(LogService.LOG_WARNING, "Exception in request: " + request.getRequestURL(), ex);
                // TODO sending an error response after we've already started writing data does not work,
                sendResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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

    @Override
    public String getServletInfo() {
        return "LiQ OBR Servlet";
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        // Nothing needs to be done - handled by DependencyManager
    }
}
