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

import java.io.Closeable;
import java.io.EOFException;
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
    private static final int COPY_BUFFER_SIZE = 4096;

    private volatile LogService m_log; /* will be injected by dependencymanager */
    private volatile BundleStore m_store; /* will be injected by dependencymanager */

    @Override
    public String getServletInfo() {
        return "Apache ACE OBR Servlet";
    }

    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        // nothing needs to be done, settings are propagated by the dependency manager
    }

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
            // Remove leading slash...
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
            // Remove leasing slash...
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
                m_log.log(LogService.LOG_DEBUG, "EOF Exception in request: " + request.getRequestURL() + "; probably the remote hung up early.");
            }
            catch (IOException ex) {
                // ACE-260: all other exception are logged, as we might have a possible resource leak...
                m_log.log(LogService.LOG_WARNING, "Exception in request: " + request.getRequestURL(), ex);
            }
            finally {
                closeSafely(fileStream, request);
                closeSafely(output, request);
            }
        }
    }
    
    private void closeSafely(Closeable resource, HttpServletRequest request) {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (EOFException ex) {
                // ACE-260: lower log-level of this exception; as it is probably because the remote hung up early...
                m_log.log(LogService.LOG_DEBUG, "EOF Exception trying to close stream: " + request.getRequestURL() + "; probably the remote hung up early.");
            }
            catch (Exception ex) {
                // ACE-260: all other exception are logged, as we might have a possible resource leak...
                m_log.log(LogService.LOG_WARNING, "Exception trying to close stream: " + request.getRequestURL(), ex);
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
}