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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.webui.domain.OBREntry;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Utility methods for handling OBRs.
 */
public final class OBRUtil {
    private static final String XPATH_QUERY = "/repository/resource[@uri]";

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

        List<OBREntry> fromRepository = new ArrayList<OBREntry>();

        List<ArtifactObject> artifactObjects = artifactRepository.get();
        artifactObjects.addAll(artifactRepository.getResourceProcessors());

        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if ((artifactURL != null) && artifactURL.startsWith(baseURL)) {
                // we now know this artifact comes from the OBR we are querying,
                // so we are interested.
                fromRepository.add(convertToOBREntry(ao, baseURL));
            }
        }
        return fromRepository;
    }

    /**
     * Parses the 'repository.xml' from OBR.
     * 
     * @param obrBaseUrl
     *            the base URL to access the OBR, cannot be <code>null</code>.
     * @return a list of parsed OBR entries, never <code>null</code>.
     * @throws XPathExpressionException
     *             in case OBR repository is invalid, or incorrect;
     * @throws IOException
     *             in case of problems accessing the 'repository.xml' file.
     */
    private static List<OBREntry> parseOBRRepository(ConnectionFactory connectionFactory, URL obrBaseUrl, String repositoryName) throws XPathExpressionException, IOException {
        InputStream input = null;
        NodeList resources = null;
        try {
            URL url = new URL(obrBaseUrl, repositoryName);
            URLConnection connection = connectionFactory.createConnection(url);
            // We always want the newest repository.xml file.
            connection.setUseCaches(false);

            input = connection.getInputStream();

            XPath xpath = XPathFactory.newInstance().newXPath();
            // this XPath expressing will find all 'resource' elements which
            // have an attribute 'uri'.
            resources = (NodeList) xpath.evaluate(XPATH_QUERY, new InputSource(input), XPathConstants.NODESET);
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

        List<OBREntry> obrList = new ArrayList<OBREntry>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();

            String uri = getNamedItemText(attr, "uri");
            String name = getNamedItemText(attr, "presentationname");
            String symbolicname = getNamedItemText(attr, "symbolicname");
            String version = getNamedItemText(attr, "version");

            if (name == null || name.equals("")) {
                if (symbolicname != null && !symbolicname.equals("")) {
                    name = symbolicname;
                }
                else {
                    name = new File(uri).getName();
                }
            }

            obrList.add(new OBREntry(name, symbolicname, version, uri));
        }

        return obrList;
    }
}
