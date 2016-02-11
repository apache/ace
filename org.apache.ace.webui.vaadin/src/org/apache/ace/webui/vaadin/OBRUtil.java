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

package org.apache.ace.webui.vaadin;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.ace.bnd.registry.RegistryImpl;
import org.apache.ace.bnd.repository.AceUrlConnector;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.webui.domain.OBREntry;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.ContentNamespace;

import aQute.bnd.deployer.repository.FixedIndexedRepo;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.service.Registry;

/**
 * Utility methods for handling OBRs.
 */
public final class OBRUtil {

    /**
     * Returns all available OBR entries that can be added to the artifact repository.
     * 
     * @param connectionFactory
     * @param artifactRepository
     * @param obrBaseUrl
     * @param repositoryName
     * @return
     * @throws XPathExpressionException
     * @throws IOException
     */
    public static List<OBREntry> getAvailableOBREntries(ConnectionFactory connectionFactory, ArtifactRepository artifactRepository, URL obrBaseUrl, String repositoryName) throws XPathExpressionException, IOException {
        List<OBREntry> entries = parseOBRRepository(connectionFactory, obrBaseUrl, repositoryName);
        entries.removeAll(getUsedOBRArtifacts(artifactRepository, obrBaseUrl));
        return entries;
    }

    /**
     * Delete an entry from the OBR.
     * 
     * @param entry
     *            The OBREntry
     * @param obrBaseUrl
     *            The OBR base url
     * @return The HTTP response code
     * @throws IOException
     *             If the HTTP operation fails
     */
    public static int deleteOBREntry(ConnectionFactory connectionFactory, OBREntry entry, URL obrBaseUrl) throws IOException {
        HttpURLConnection connection = null;

        try {
            URL endpointUrl = new URL(obrBaseUrl, entry.getUri());
            connection = (HttpURLConnection) connectionFactory.createConnection(endpointUrl);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("DELETE");
            connection.connect();
            return connection.getResponseCode();
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Converts a given artifact object to an OBR entry.
     * 
     * @param artifactObject
     *            the artifact object to convert;
     * @param obrBase
     *            the obr base url.
     * @return an OBR entry instance, never <code>null</code>.
     */
    private static OBREntry convertToOBREntry(ArtifactObject artifactObject, String obrBase) {
        String name = artifactObject.getName();
        String symbolicName = artifactObject.getAttribute(BundleHelper.KEY_SYMBOLICNAME);
        String version = artifactObject.getAttribute(BundleHelper.KEY_VERSION);
        String relativeURL = artifactObject.getURL().substring(obrBase.length());
        return new OBREntry(name, symbolicName, version, relativeURL);
    }

    /**
     * Builds a list of all OBR artifacts currently in use.
     * 
     * @param obrBaseUrl
     *            the base URL of the OBR, cannot be <code>null</code>.
     * @return a list of used OBR entries, never <code>null</code>.
     * @throws IOException
     *             in case an artifact repository is not present.
     */
    private static List<OBREntry> getUsedOBRArtifacts(ArtifactRepository artifactRepository, URL obrBaseUrl) throws IOException {
        final String baseURL = obrBaseUrl.toExternalForm();

        List<OBREntry> fromRepository = new ArrayList<>();

        List<ArtifactObject> artifactObjects = artifactRepository.get();
        artifactObjects.addAll(artifactRepository.getResourceProcessors());

        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if ((artifactURL != null) /*&& artifactURL.startsWith(baseURL)*/) {
                // we now know this artifact comes from the OBR we are querying,
                // so we are interested.
                fromRepository.add(convertToOBREntry(ao, baseURL));
            }
        }
        return fromRepository;
    }

    /**
     * Get all resources from an OSGi R5 repository
     * 
     * @param obrBaseUrl
     *            the base URL to access the OBR, cannot be <code>null</code>.
     * @return a list of parsed OBR entries, never <code>null</code>.
     * @throws XPathExpressionException
     *             in case OBR repository is invalid, or incorrect;
     * @throws IOException
     *             in case of problems accessing the 'index.xml' file.
     */
    private static List<OBREntry> parseOBRRepository(final ConnectionFactory connectionFactory, URL obrBaseUrl, String repositoryName) throws XPathExpressionException, IOException {
        FixedIndexedRepo fixedIndexedRepo = new FixedIndexedRepo();
        
        AceUrlConnector aceUrlConnector = new AceUrlConnector(connectionFactory);
        Registry registry = new RegistryImpl(aceUrlConnector);
        fixedIndexedRepo.setRegistry(registry);
        
        Map<String, String> properties = new HashMap<>();
        properties.put(FixedIndexedRepo.PROP_LOCATIONS, new URL(obrBaseUrl, repositoryName).toString());
        fixedIndexedRepo.setProperties(properties);
        
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", "(&(osgi.identity=*)(version=*)(type=*))")
            .buildSyntheticRequirement();
        
        Map<Requirement, Collection<Capability>> sourceResources = fixedIndexedRepo.findProviders(Collections.singleton(requirement));
        if (sourceResources.isEmpty() || sourceResources.get(requirement).isEmpty()) {
            return Collections.emptyList();
        }
        List<OBREntry> obrList = new ArrayList<>();
        Iterator<Capability> capabilities = sourceResources.get(requirement).iterator();
        while (capabilities.hasNext()) {
            Capability capability = capabilities.next();
            
            Resource resource = capability.getResource();
            List<Capability> identities = resource.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
            String bsn = null;
            Version version = null;
            if (identities != null && identities.size() == 1){
                Capability id = identities.get(0);
                bsn = (String) id.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE);
                version = (Version) id.getAttributes().get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
            }
            
            URI uri = null;
            List<Capability> contentCapabilities = resource.getCapabilities(ContentNamespace.CONTENT_NAMESPACE);
            if (contentCapabilities != null && contentCapabilities.size() == 1) {
                Capability content = contentCapabilities.get(0);
                uri = (URI) content.getAttributes().get(ContentNamespace.CAPABILITY_URL_ATTRIBUTE);
            }
            
            if (bsn != null && uri != null) {
                obrList.add(new OBREntry(bsn, bsn, version.toString(), uri.toString().substring(obrBaseUrl.toString().length())));
            } else {
                throw new IllegalStateException("No Identity or multiple identities");
            }
        }

        return obrList;
    }
}
