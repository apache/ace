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
package org.apache.ace.repository.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.range.SortedRangeSet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * Base class for the repository servlets. Both the repository and the repository replication servlets work in a similar
 * way, so the specifics were factored out of this base class and put in two subclasses.
 */
public abstract class RepositoryServletBase<REPO_TYPE> extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    
    private static final int COPY_BUFFER_SIZE = 1024;
    private static final String QUERY = "/query";
    protected static final String TEXT_MIMETYPE = "text/plain";
    protected static final String BINARY_MIMETYPE = "application/octet-stream";

    private final Class<REPO_TYPE> m_repoType;
    // injected by Dependency Manager
    protected volatile BundleContext m_context;
    protected volatile LogService m_log;

    public RepositoryServletBase(Class<REPO_TYPE> repoType) {
        m_repoType = repoType;
    }

    /**
     * Checkout or get data from the repository.
     * 
     * @param repo
     *            the repository service
     * @param version
     *            the version to check out.
     * @return the data
     * @throws IllegalArgumentException
     * @throws java.io.IOException
     */
    protected abstract InputStream doCheckout(REPO_TYPE repo, long version) throws IllegalArgumentException, IOException;

    /**
     * Commit or put the data into the repository.
     * 
     * @param repo
     *            the repository service
     * @param version
     *            The version to commit
     * @param data
     *            The data
     * @return <code>true</code> if successful
     * @throws IllegalArgumentException
     * @throws IOException
     */
    protected abstract boolean doCommit(REPO_TYPE repo, long version, InputStream data) throws IllegalArgumentException, IOException;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        String customer = request.getParameter("customer");
        String name = request.getParameter("name");
        String filter = request.getParameter("filter");
        String version = request.getParameter("version");

        if (QUERY.equals(path)) {
            // both repositories have a query method
            if (filter != null) {
                if ((name == null) && (customer == null)) {
                    handleQuery(filter, response);
                }
                else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Specify either a filter or customer and/or name, but not both.");
                }
            }
            else {
                if ((name != null) && (customer != null)) {
                    handleQuery(getRepositoryFilter(customer, name), response);
                }
                else if (name != null) {
                    handleQuery("(name=" + name + ")", response);
                }
                else if (customer != null) {
                    handleQuery("(customer=" + customer + ")", response);
                }
                else {
                    handleQuery(null, response);
                }
            }
        }
        else if (getCheckoutCommand().equals(path)) {
            // and both have a checkout, only it's named differently
            if ((name != null) && (customer != null) && (version != null)) {
                handleCheckout(customer, name, Long.parseLong(version), response);
            }
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getPathInfo();
        String customer = request.getParameter("customer");
        String name = request.getParameter("name");
        String version = request.getParameter("version");

        if (getCommitCommand().equals(path)) {
            // and finally, both have a commit, only it's named differently
            if ((name != null) && (customer != null) && (version != null)) {
                handleCommit(customer, name, Long.parseLong(version), request.getInputStream(), response);
            }
            else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Name, customer and version should all be specified.");
            }
        }
        else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Returns the name of the "checkout" command.
     */
    protected abstract String getCheckoutCommand();

    /**
     * Returns the name of the "commit" command.
     */
    protected abstract String getCommitCommand();

    /**
     * Implement this by asking the right repository for a range of available versions.
     * 
     * @param repo
     *            the repository service
     * @return a sorted range set
     * @throws IOException
     *             If the range cannot be obtained
     */
    protected abstract SortedRangeSet getRange(REPO_TYPE repo) throws IOException;

    /**
     * Copies data from an input stream to an output stream.
     * 
     * @param in
     *            The input
     * @param outThe
     *            output
     * @param version
     * @param name
     * @throws IOException
     *             If copying fails
     */
    private void copy(InputStream in, OutputStream out, String name, long version)
        throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int bytes = in.read(buffer);
        while (bytes != -1) {
            out.write(buffer, 0, bytes);
            bytes = in.read(buffer);
        }

    }

    /**
     * Returns a list of repositories that match the specified filter condition.
     * 
     * @param filter
     *            The filter condition
     * @return An array of service references
     * @throws InvalidSyntaxException
     *             If the filter condition is invalid
     */
    private List<ServiceReference<REPO_TYPE>> getRepositories(String filter) throws InvalidSyntaxException {
        List<ServiceReference<REPO_TYPE>> result = new ArrayList<>();
        result.addAll(m_context.getServiceReferences(m_repoType, filter));
        return result;
    }

    /**
     * Handles a checkout command and returns the response.
     */
    private void handleCheckout(String customer, String name, long version, HttpServletResponse response) throws IOException {
        List<ServiceReference<REPO_TYPE>> refs;
        try {
            refs = getRepositories(getRepositoryFilter(customer, name));
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
            return;
        }

        try {
            if (refs.size() != 1) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    (refs.isEmpty() ? "Could not find repository " : "Multiple repositories found ") + " for customer " + customer + ", name " + name);
                return;
            }

            ServiceReference<REPO_TYPE> ref = refs.get(0);
            if (ref == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find repository for customer " + customer + ", name " + name);
                return;
            }

            REPO_TYPE repo = m_context.getService(ref);

            try {
                response.setContentType(BINARY_MIMETYPE);

                InputStream data = doCheckout(repo, version);
                if (data == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Requested version does not exist: " + version);
                }
                else {
                    copy(data, response.getOutputStream(), name, version);
                }
            }
            finally {
                m_context.ungetService(ref);
            }
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "I/O exception: " + e.getMessage());
        }
    }

    /**
     * Handles a commit command and sends back the response.
     */
    private void handleCommit(String customer, String name, long version, InputStream data, HttpServletResponse response) throws IOException {
        List<ServiceReference<REPO_TYPE>> refs;
        try {
            refs = getRepositories(getRepositoryFilter(customer, name));
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
            return;
        }

        try {
            if (refs.size() != 1) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    (refs.isEmpty() ? "Could not find repository " : "Multiple repositories found ") + " for customer " + customer + ", name " + name);
                return;
            }

            ServiceReference<REPO_TYPE> ref = refs.get(0);
            if (ref == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find repository for customer " + customer + ", name " + name);
                return;
            }

            REPO_TYPE repo = m_context.getService(ref);

            try {
                if (!doCommit(repo, version, data)) {
                    response.sendError(HttpServletResponse.SC_NOT_MODIFIED, "Could not commit");
                }
                else {
                    response.setStatus(HttpServletResponse.SC_OK);
                }
            }
            catch (IllegalArgumentException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid version");
            }
            catch (IllegalStateException e) {
                response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Cannot commit, not the master repository");
            }
            finally {
                m_context.ungetService(ref);
            }
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "I/O exception: " + e.getMessage());
        }
    }

    private String getRepositoryFilter(String customer, String name) {
        return "(&(customer=" + customer + ")(name=" + name + ")(master=*))";
    }

    /**
     * Handles a query command and sends back the response.
     */
    private void handleQuery(String filter, HttpServletResponse response) throws IOException {
        List<ServiceReference<REPO_TYPE>> refs;
        try {
            refs = getRepositories(filter);
        }
        catch (InvalidSyntaxException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid filter syntax: " + e.getMessage());
            return;
        }

        try {
            StringBuffer result = new StringBuffer();

            for (ServiceReference<REPO_TYPE> ref : refs) {
                REPO_TYPE repo = m_context.getService(ref);
                try {
                    result.append((String) ref.getProperty("customer"));
                    result.append(',');
                    result.append((String) ref.getProperty("name"));
                    result.append(',');
                    result.append(getRange(repo).toRepresentation());
                    result.append('\n');
                }
                finally {
                    m_context.ungetService(ref);
                }
            }

            response.setContentType(TEXT_MIMETYPE);
            response.getWriter().print(result.toString());
        }
        catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Could not retrieve version range for repository: " + e.getMessage());
        }
    }
}
