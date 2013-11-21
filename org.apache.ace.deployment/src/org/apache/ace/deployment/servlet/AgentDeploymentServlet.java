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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.framework.Version;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class AgentDeploymentServlet extends HttpServlet implements ManagedService {
    private static final int BUFFER_SIZE = 1024 * 32;
    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    /** URL to the OBR that is used for finding versions of the agent. */
    private static final String KEY_OBR_URL = "obr.url";

    private static final String XPATH_QUERY = "/repository/resource[@uri]";

    public static final String VERSIONS = "versions";
    public static final String BUNDLE_MIMETYPE = "application/octet-stream";
    public static final String TEXT_MIMETYPE = "text/plain";

    // injected by Dependency Manager
    private volatile LogService m_log;
    private volatile AuthenticationService m_authService;
    private volatile ConnectionFactory m_connectionFactory;
    // See updated()
    private boolean m_useAuth = false;
    private URL m_obrURL;

    private final String m_repositoryXML = "repository.xml";

    /**
     * Gets the actual text from a named item contained in the given node map.
     * 
     * @param map
     *            the node map to get the named item from;
     * @param name
     *            the name of the item to get.
     * @return the text of the named item, can be <code>null</code> in case the named item does not exist, or has no
     *         text.
     */
    private static String getNamedItemText(NamedNodeMap map, String name) {
        Node namedItem = map.getNamedItem(name);
        if (namedItem == null) {
            return null;
        }
        else {
            return namedItem.getTextContent();
        }
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

            String obrURL = (String) settings.get(KEY_OBR_URL);
            try {
                URL url = new URL(obrURL);
                m_obrURL = url;
            }
            catch (MalformedURLException e) {
                throw new ConfigurationException(KEY_OBR_URL, "Invalid value, not a URL.", e);
            }
            if (obrURL == null) {
                throw new ConfigurationException(KEY_OBR_URL, "Missing " +
                    "value!");
            }
        }
        else {
            m_useAuth = false;
            m_obrURL = null;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String[] pathElements = verifyAndGetPathElements(request.getPathInfo());
            // String targetID = pathElements[1]; // in the future we might use this for per target approval
            String agentID = pathElements[2];
            int numberOfElements = pathElements.length;
            if (numberOfElements == 4) {
                handleVersionsRequest(getVersions(agentID), response);
            }
            else {
                handlePackageDelivery(agentID, new Version(pathElements[4]), request, response);
            }
        }
        catch (AceRestException e) {
            m_log.log(LogService.LOG_WARNING, e.getMessage(), e);
            e.handleAsHttpError(response);
        }
    }

    protected URLConnection openConnection(URL url) throws IOException {
        return m_connectionFactory.createConnection(url);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!authenticate(req)) {
            // Authentication failed; don't proceed with the original request...
            resp.sendError(SC_UNAUTHORIZED);
        }
        else {
            // Authentication successful, proceed with original request...
            super.service(req, resp);
        }
    }

    /**
     * Authenticates, if needed the user with the information from the given request.
     * 
     * @param request
     *            the request to obtain the credentials from, cannot be <code>null</code>.
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

    private void closeSilently(Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Exception trying to close stream after request. ", e);
            throw new RuntimeException(e);
        }
    }

    private URL createOBRURL() throws MalformedURLException {
        try {
            return new URL(m_obrURL, m_repositoryXML);
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Error retrieving repository.xml from " + m_obrURL);
            throw e;
        }
    }

    private InputStream getAgentFromOBR(URL obrBaseUrl, String agentID, Version version) throws XPathExpressionException, IOException {
        InputStream input = null;
        NodeList resources = getOBRNodeList(input);
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();

            String uri = getNamedItemText(attr, "uri");
            if (uri == null || uri.equals("")) {
                m_log.log(LogService.LOG_ERROR, "Skipping resource without uri from repository " + obrBaseUrl);
                continue;
            }

            String symbolicname = getNamedItemText(attr, "symbolicname");
            Version bundleVersion = new Version(getNamedItemText(attr, "version"));
            if (agentID.equals(symbolicname) && version.equals(bundleVersion)) {
                URL url = new URL(obrBaseUrl, getNamedItemText(attr, "uri"));
                URLConnection connection = openConnection(url);
                return connection.getInputStream();
            }
        }
        return null;
    }

    private NodeList getOBRNodeList(InputStream input) throws XPathExpressionException, IOException {
        NodeList resources;
        try {
            URLConnection connection = openConnection(createOBRURL());
            // We always want the newest repository.xml file.
            connection.setUseCaches(false);

            input = connection.getInputStream();

            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                // this XPath expressing will find all 'resource' elements which
                // have an attribute 'uri'.
                resources = (NodeList) xpath.evaluate(XPATH_QUERY, new InputSource(input), XPathConstants.NODESET);
            }
            catch (XPathExpressionException e) {
                m_log.log(LogService.LOG_ERROR, "Error evaluating XPath expression.", e);
                throw e;
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Error reading repository metadata.", e);
            throw e;
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException e) {
                    // too bad, no worries.
                }
            }
        }
        return resources;
    }

    private List<Version> getVersions(String agentID) throws AceRestException {
        try {
            return getVersionsFromOBR(m_obrURL, agentID);
        }
        catch (XPathExpressionException e) {
            throw new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Unknown agent (" + agentID + ")");
        }
        catch (IOException ioe) {
            m_log.log(LogService.LOG_WARNING, "Error getting available versions.", ioe);
            throw new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error getting available versions.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Version> getVersionsFromOBR(URL obrBaseUrl, String agentID) throws XPathExpressionException, IOException {
        InputStream input = null;
        NodeList resources = getOBRNodeList(input);
        List<Version> obrList = new ArrayList<Version>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();

            String uri = getNamedItemText(attr, "uri");
            if (uri == null || uri.equals("")) {
                m_log.log(LogService.LOG_ERROR, "Skipping resource without uri from repository " + obrBaseUrl);
                continue;
            }

            String symbolicname = getNamedItemText(attr, "symbolicname");
            if (agentID.equals(symbolicname)) {
                Version version = new Version(getNamedItemText(attr, "version"));
                obrList.add(version);
            }
        }
        Collections.sort(obrList);
        return obrList;
    }

    private void handlePackageDelivery(String agentID, Version version, HttpServletRequest request, HttpServletResponse response) throws AceRestException {
        InputStream is = null;
        OutputStream os = null;

        try {
            // Wrap response to add support for range requests
            response = new ContentRangeResponseWrapper(request, response);

            try {
                is = getAgentFromOBR(m_obrURL, agentID, version);
                if (is == null) {
                    throw (AceRestException) new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Agent not found in OBR.");
                }
            }
            catch (XPathExpressionException e) {
                throw (AceRestException) new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Agent not found: error parsing OBR").initCause(e);
            }

            response.setContentType(BUNDLE_MIMETYPE);

            os = response.getOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytes;
            while ((bytes = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytes);
            }
            os.flush();
        }
        catch (IllegalArgumentException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid").initCause(e);
        }
        catch (IOException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not deliver package").initCause(e);
        }
        finally {
            closeSilently(is);
            closeSilently(os);
        }
    }

    private void handleVersionsRequest(List<Version> versions, HttpServletResponse response) throws AceRestException {
        ServletOutputStream output = null;

        response.setContentType(TEXT_MIMETYPE);
        try {
            output = response.getOutputStream();
            for (Version version : versions) {
                output.print(version.toString());
                output.print("\n");
            }
        }
        catch (IOException e) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
        }
        finally {
            closeSilently(output);
        }
    }

    private String[] verifyAndGetPathElements(String path) throws AceRestException {
        if (path == null) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid, no path specified.");
        }
        String[] elements = path.split("/");
        int numberOfElements = elements.length;
        if ((numberOfElements < 4) || (numberOfElements > 5) || !VERSIONS.equals(elements[3])) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI elements are invalid: " + path);
        }
        return elements;
    }
}
