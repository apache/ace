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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPathExpressionException;

import org.apache.ace.bnd.registry.RegistryImpl;
import org.apache.ace.bnd.repository.AceUrlConnector;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;
import org.osgi.service.repository.ContentNamespace;
import org.osgi.service.repository.Repository;

import aQute.bnd.deployer.repository.FixedIndexedRepo;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.service.Registry;

public class AgentDeploymentServlet extends HttpServlet implements ManagedService {
    private static final long serialVersionUID = 1L;

    private static final int BUFFER_SIZE = 1024 * 32;

    /** URL to the OBR that is used for finding versions of the agent. */
    private static final String KEY_OBR_URL = "obr.url";

    public static final String VERSIONS = "versions";
    public static final String BUNDLE_MIMETYPE = "application/octet-stream";
    public static final String TEXT_MIMETYPE = "text/plain";

    // injected by Dependency Manager
    private volatile LogService m_log;
    private volatile ConnectionFactory m_connectionFactory;
    // See updated()
    private URL m_obrURL;

    private final String m_repositoryXML = "index.xml";

    @Override
    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String obrURL = (String) settings.get(KEY_OBR_URL);
            if (obrURL == null) {
                throw new ConfigurationException(KEY_OBR_URL, "Missing value!");
            }
            try {
                URL url = new URL(obrURL);
                m_obrURL = url;
            }
            catch (MalformedURLException e) {
                throw new ConfigurationException(KEY_OBR_URL, "Invalid value, not a URL.", e);
            }
        }
        else {
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
            if (!e.handleAsHttpError(response)) {
                m_log.log(LogService.LOG_ERROR, "Failed to properly notify client of exception!", e);
            }
        }
    }

    protected URLConnection openConnection(URL url) throws IOException {
        return m_connectionFactory.createConnection(url);
    }    
    
    private InputStream getAgentFromOBR(URL obrBaseUrl, String agentID, Version version) throws IOException {
        Repository repository = createRepository();
        
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", String.format("(&(osgi.identity=%s)(version=%s)(type=*))", agentID, version))
            .buildSyntheticRequirement();
        
        Map<Requirement, Collection<Capability>> sourceResources = repository.findProviders(Collections.singleton(requirement));
        if (sourceResources.isEmpty() || sourceResources.get(requirement).isEmpty()) {
            return null;
        }
        
        Iterator<Capability> capabilities = sourceResources.get(requirement).iterator();
        while (capabilities.hasNext()) {
            Capability capability = capabilities.next();
            Resource resource = capability.getResource();
            
            List<Capability> contentCapabilities = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
            if (contentCapabilities != null && contentCapabilities.size() == 1) {
                Capability content = contentCapabilities.get(0);
                URI uri = (URI) content.getAttributes().get(ContentNamespace.CAPABILITY_URL_ATTRIBUTE);
                if (uri != null) {
                    return m_connectionFactory.createConnection(uri.toURL()).getInputStream();
                }
            }
        }
        
        return null;
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

    private List<Version> getVersionsFromOBR(URL obrBaseUrl, String agentID) throws XPathExpressionException, IOException {
        Repository repository = createRepository();
        
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", String.format("(&(osgi.identity=%s)(version=*)(type=*))", agentID))
            .buildSyntheticRequirement();
        
        Map<Requirement, Collection<Capability>> sourceResources = repository.findProviders(Collections.singleton(requirement));
        if (sourceResources.isEmpty() || sourceResources.get(requirement).isEmpty()) {
            return Collections.emptyList();
        }
        
        Iterator<Capability> capabilities = sourceResources.get(requirement).iterator();
        List<Version> versions = new ArrayList<>();
        
        while (capabilities.hasNext()) {
            Capability capability = capabilities.next();
            
            Resource resource = capability.getResource();
            List<Capability> identities = resource.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
            Version version = null;
            if (identities != null && identities.size() == 1){
                Capability id = identities.get(0);
                version = (Version) id.getAttributes().get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
            }
            
            URI uri = null;
            List<Capability> contentCapabilities = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
            if (contentCapabilities != null && contentCapabilities.size() == 1) {
                Capability content = contentCapabilities.get(0);
                uri = (URI) content.getAttributes().get(ContentNamespace.CAPABILITY_URL_ATTRIBUTE);
            }
            
            if (version != null && uri != null) {
                versions.add(version);
            }
        }

        return versions;
    }

    private Repository createRepository() throws MalformedURLException {
        FixedIndexedRepo fixedIndexedRepo = new FixedIndexedRepo();
        
        AceUrlConnector aceUrlConnector = new AceUrlConnector(m_connectionFactory);
        Registry registry = new RegistryImpl(aceUrlConnector);
        fixedIndexedRepo.setRegistry(registry);
        
        Map<String, String> properties = new HashMap<>();
        properties.put(FixedIndexedRepo.PROP_LOCATIONS, createOBRURL().toString());
        fixedIndexedRepo.setProperties(properties);
        return fixedIndexedRepo;
    }
    
    private URL createOBRURL() throws MalformedURLException {
        try {
            return new URL(m_obrURL, m_repositoryXML);
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Error retrieving index.xml from " + m_obrURL);
            throw e;
        }
    }
    
    private void handlePackageDelivery(String agentID, Version version, HttpServletRequest request, HttpServletResponse response) throws AceRestException {
        try (InputStream is = getAgentFromOBR(m_obrURL, agentID, version)){            
            if (is == null) {
                throw (AceRestException) new AceRestException(HttpServletResponse.SC_NOT_FOUND, "Agent not found in OBR.");
            }

            // Wrap response to add support for range requests
            response = new ContentRangeResponseWrapper(request, response);
            response.setContentType(BUNDLE_MIMETYPE);

            try (OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytes;
                while ((bytes = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytes);
                }
                os.flush();
            }
        }
        catch (IllegalArgumentException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid").initCause(e);
        }
        catch (IOException e) {
            throw (AceRestException) new AceRestException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not deliver package").initCause(e);
        }
    }

    private void handleVersionsRequest(List<Version> versions, HttpServletResponse response) throws AceRestException {
        response.setContentType(TEXT_MIMETYPE);
        try (ServletOutputStream output = response.getOutputStream()){
            for (Version version : versions) {
                output.print(version.toString());
                output.print("\n");
            }
        }
        catch (IOException e) {
            throw new AceRestException(HttpServletResponse.SC_BAD_REQUEST, "Request URI is invalid");
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
