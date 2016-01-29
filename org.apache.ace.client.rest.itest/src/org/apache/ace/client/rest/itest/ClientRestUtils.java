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
package org.apache.ace.client.rest.itest;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.ace.client.rest.util.Client;
import org.apache.ace.client.rest.util.WebResourceException;
import org.apache.ace.client.rest.util.WebResource;
import org.apache.ace.test.utils.FileUtils;

import com.google.gson.Gson;

/**
 * Helper methods for talking to the ACE client through REST.
 */
public class ClientRestUtils {

    /** Asserts that a list of entities exist by trying to GET them. */
    public static void assertEntitiesExist(WebResource... entities) throws Exception {
        for (WebResource r : entities) {
            System.out.println(r.getURI().toString());
            r.getString();
        }
    }

    /**
     * Asserts that a collection of resources exist by trying to GET the list, validate the number of items and finally
     * GET each item.
     */
    public static void assertResources(Gson gson, WebResource w2, String type, int number) throws IOException {
        String[] artifacts = gson.fromJson(w2.path(type).getString(), String[].class);
        assertEquals("Wrong number of " + type + "s", number, artifacts.length);
        for (String id : artifacts) {
            w2.path(type + "/" + id).getString();
        }
    }

    /** Creates an association between an artifact and a feature. */
    public static WebResource createAssociationA2F(Client c, WebResource work, String left, String right) throws IOException {
        return createEntity(c, work, "artifact2feature", "{attributes: {leftEndpoint: \"(artifactName=" + left + ")\", rightEndpoint=\"(name=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }

    /** Creates an association between a distribution and a target. */
    public static WebResource createAssociationD2T(Client c, WebResource work, String left, String right) throws IOException {
        return createEntity(c, work, "distribution2target", "{attributes: {leftEndpoint: \"(name=" + left + ")\", rightEndpoint=\"(id=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }

    /** Creates an association between a feature and a distribution. */
    public static WebResource createAssociationF2D(Client c, WebResource work, String left, String right) throws IOException {
        return createEntity(c, work, "feature2distribution", "{attributes: {leftEndpoint: \"(name=" + left + ")\", rightEndpoint=\"(name=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }

    /** Creates a bundle artifact. */
    public static WebResource createBundleArtifact(Client c, WebResource work, String name, String bsn, String v, File file) throws IOException {
        return createBundleArtifact(c, work, name, bsn, v, file.toURI().toURL().toExternalForm());
    }

    /** Creates a bundle artifact. */
    public static WebResource createBundleArtifact(Client c, WebResource work, String name, String bsn, String v, String url) throws IOException {
        return createArtifact(c, work, name, bsn, v, url, "application/vnd.osgi.bundle");
    }

    /** Creates a bundle artifact. */
    public static WebResource createArtifact(Client c, WebResource work, String name, String bsn, String v, String url, String mimetype) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "Bundle-SymbolicName: \"" + bsn + "\", " +
            "Bundle-Version: \"" + v + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            "url: \"" + url + "\"" +
            "}, tags: {}}");
    }

    /** Creates a configuration artifact. */
    public static WebResource createConfiguration(Client c, WebResource work, String name, String url, String mimetype, String filename, String processorID) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "filename: \"" + filename + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            "url: \"" + url + "\", " +
            "processorPid: \"" + processorID + "\"" +
            "}, tags: {}}");
    }

    /** creates a new REST-client. */
    public static Client createClient() {
        Client client = Client.create();
        client.getProperties().put(Client.PROPERTY_FOLLOW_REDIRECTS, false);
        return client;
    }

    /** Creates a distribution. */
    public static WebResource createDistribution(Client c, WebResource work, String name) throws IOException {
        return createEntity(c, work, "distribution", "{attributes: {name: \"" + name + "\"}, tags: {}}");
    }

    /** Creates an entity. */
    public static WebResource createEntity(Client c, WebResource work, String type, String data) throws IOException {
        WebResource entity = work.path(type);
        try {
            entity.post(data);
            throw new IOException("Could not create " + type + " with data " + data);
        }
        catch (WebResourceException e2) {
            return c.resource(e2.getResponse().getLocation());
        }
    }

    /** Creates a feature. */
    public static WebResource createFeature(Client c, WebResource work, String name) throws IOException {
        return createEntity(c, work, "feature", "{attributes: {name: \"" + name + "\"}, tags: {}}");
    }

    /** Creates a resource processor bundle artifact. */
    public static WebResource createResourceProcessor(Client c, WebResource work, String name, String bsn, String v, String url, String mimetype, String processorID) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "description: \"\", " +
            "Bundle-SymbolicName: \"" + bsn + "\", " +
            "Bundle-Version: \"" + v + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            "Deployment-ProvidesResourceProcessor: \"" + processorID + "\", " +
            "DeploymentPackage-Customizer: \"true\", " +
            "url: \"" + url + "\"" +
            "}, tags: {}}");
    }

    /** Creates a target. */
    public static WebResource createTarget(Client c, WebResource work, String name, String... tags) throws IOException {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < tags.length; i += 2) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(tags[i] + ": \"" + tags[i + 1] + "\"");
        }
        return createEntity(c, work, "target", "{attributes: {id: \"" + name + "\", autoapprove: \"true\"}, tags: {" + result.toString() + "}}");
    }

    public static File createTmpConfigOnDisk(String config) throws Exception {
        File file = File.createTempFile("template", ".xml");
        file.deleteOnExit();
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        try {
            bw.write(config);
            return file;
        }
        finally {
            bw.close();
        }
    }

    /** Creates a new workspace. */
    public static WebResource createWorkspace(String host, Client c) {
        WebResource r = c.resource(host.concat("/client/work/"));
        try {
            r.post();
            fail("We should have been redirected to a new workspace.");
        }
        catch (WebResourceException e) {
            assertEquals("Expected a valid redirect after creating a workspace", 302, e.getResponse().getStatus());
            return c.resource(e.getResponse().getLocation());
        }
        catch (IOException e) {
            fail("Unexpected exception: " + e.getMessage());
        }
        return null; // to keep the compiler happy, it does not understand what fail() does
    }

    public static void deleteResources(Gson gson, WebResource workspace) throws IOException {
        deleteResources(gson, workspace, "artifact");
        deleteResources(gson, workspace, "artifact2feature");
        deleteResources(gson, workspace, "feature");
        deleteResources(gson, workspace, "feature2distribution");
        deleteResources(gson, workspace, "distribution");
        deleteResources(gson, workspace, "distribution2target");
        deleteResources(gson, workspace, "target");
    }

    public static void deleteResources(Gson gson, WebResource workspace, String type) throws IOException {
        String[] artifacts = gson.fromJson(workspace.path(type).getString(), String[].class);
        for (String id : artifacts) {
            workspace.path(type + "/" + id).delete();
        }
    }

    public static void ensureCleanStore(String path) throws IOException {
        File store = new File(path);
        if (store.exists()) {
            if (!store.isDirectory()) {
                throw new IllegalStateException("Configured store is not a directory: " + store.getAbsolutePath());
            }
            FileUtils.removeDirectoryWithContent(store);
        }
        if (!store.mkdirs()) {
            throw new IllegalStateException("Failed to create store directory: " + store.getAbsolutePath());
        }
    }
}
