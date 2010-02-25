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
package org.apache.ace.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.services.OBRBundleDescriptor;
import org.apache.ace.client.services.OBRService;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class OBRServiceImpl extends RemoteServiceServlet implements OBRService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 268104499426401403L;

    private static final String URL_BASE = "http://localhost:8080/obr/";
    
    public OBRBundleDescriptor[] getBundles() throws Exception {
        return getBundles(URL_BASE);
    }
    
    public OBRBundleDescriptor[] getBundles(String obrBaseUrl) throws Exception {
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        
        URL obrBase = new URL(obrBaseUrl);
        
        // retrieve the repository.xml as a stream
        URL url = null;
        try {
            url = new URL(obrBase, "repository.xml");
        }
        catch (MalformedURLException e) {
            System.err.println("Error retrieving repository.xml from " + obrBase);
            throw e;
        }

        InputStream input = null;
        NodeList resources = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false); //We always want the newest repository.xml file.
            input = connection.getInputStream();

            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                // this XPath expressing will find all 'resource' elements which have an attribute 'uri'.
                resources = (NodeList) xpath.evaluate("/repository/resource[@uri]", new InputSource(input), XPathConstants.NODESET);
            }
            catch (XPathExpressionException e) {
                System.err.println("Error evaluating XPath expression.");
                e.printStackTrace(System.err);
                throw e;
            }
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
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

        // Create a list of filenames from the OBR
        List<String> fromOBR = new ArrayList<String>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            fromOBR.add(resource.getAttributes().getNamedItem("uri").getTextContent());
        }

        // Create a list of filenames from the ArtifactRepository
        List<String> fromRepository = new ArrayList<String>();
        List<ArtifactObject> artifactObjects = ar.get();
        artifactObjects.addAll(ar.getResourceProcessors());
        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if (artifactURL.startsWith(obrBase.toExternalForm())) {
                // we now know this artifact comes from the OBR we are querying, so we are interested.
                fromRepository.add(new File(artifactURL).getName());
            }
        }

        // remove all urls we already know
        fromOBR.removeAll(fromRepository);
        if (fromOBR.isEmpty()) {
            System.err.println("No data in obr...");
            return null;
        }
        
        // Create a list of all bundle names
        List<OBRBundleDescriptor> result = new ArrayList<OBRBundleDescriptor>();
        for (String s : fromOBR) {
            result.add(new OBRBundleDescriptor(s, new URL(obrBase, s).toExternalForm()));
        }
        
        return result.toArray(new OBRBundleDescriptor[result.size()]);
    }

    public void importBundle(OBRBundleDescriptor bundle) throws Exception {
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        
        ar.importArtifact(new URL(bundle.getUrl()), false);
    }
}
