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
package org.apache.ace.gogo.repo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.ace.bnd.repository.AceObrRepository;
import org.osgi.framework.Version;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.indexer.ResourceIndexer;
import org.osgi.service.indexer.impl.RepoIndex;
import org.osgi.service.repository.Repository;

import aQute.bnd.deployer.repository.AbstractIndexedRepo;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.service.Strategy;

public class RepositoryUtil {

    public static AceObrRepository createRepository(String type, String location) throws Exception {
        Map<String, String> properties = new HashMap<>();
        properties.put(AceObrRepository.PROP_REPO_TYPE, type);
        properties.put(AceObrRepository.PROP_LOCATIONS, location);
        AceObrRepository repository = new AceObrRepository();
        repository.setProperties(properties);
        return repository;
    }

    public static URL indexDirectory(String directory) throws Exception {

        File rootDir = new File(directory);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IOException("Not a directory: " + directory);
        }

        File indexFile = new File(rootDir, "index.xml");
        Set<File> files = new HashSet<>();
        Stack<File> dirs = new Stack<>();
        dirs.push(rootDir);
        while (!dirs.isEmpty()) {
            File dir = dirs.pop();
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    dirs.push(file);
                }
                else {
                    files.add(file);
                }
            }
        }

        RepoIndex indexer = new RepoIndex();
        Map<String, String> config = new HashMap<>();
        config.put(ResourceIndexer.REPOSITORY_NAME, "empty");
        config.put(ResourceIndexer.PRETTY, "true");
        config.put(ResourceIndexer.ROOT_URL, rootDir.getAbsoluteFile().toURI().toURL().toString());

        FileOutputStream out = new FileOutputStream(indexFile);
        try {
            indexer.index(files, out, config);
        }
        finally {
            out.close();
        }
        return indexFile.toURI().toURL();
    }

    // FIXME ACE only
    public static boolean deleteResource(Resource resource) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL endpointUrl = new URL(getUrl(resource));
            connection = (HttpURLConnection) endpointUrl.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("DELETE");
            connection.connect();
            return connection.getResponseCode() == 200;
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static Requirement getRequirement(String filter) throws Exception {

        if (filter == null || filter.equals("")) {
            return null;
        }

        String namespace = "osgi.identity";
        if (filter.indexOf("(") < 0) {
            throw new Exception("Illegal filter");
        }
        if (filter.indexOf(":") > 0 && filter.indexOf(":") < filter.indexOf("(")) {
            namespace = filter.substring(0, filter.indexOf(":"));
            filter = filter.substring(filter.indexOf(":") + 1);
        }
        Requirement requirement = new CapReqBuilder(namespace)
            .addDirective("filter", filter)
            .buildSyntheticRequirement();
        return requirement;
    }

    /**
     * Construct a Resource filename with a specified version in the form that ACE OBR understands.
     * 
     * @param resource
     *            The resource
     * @param version
     *            The version
     * @return The name
     */
    public static String getFileName(Resource resource, Version version) {
        String location = getUrl(resource);
        String extension = location.substring(location.lastIndexOf(".") + 1);
        return getIdentity(resource) + "-" + version + "." + extension;
    }

    /**
     * Construct a Resource filename in the form that ACE OBR understands.
     * 
     * @param resource
     *            The resource
     * @return The name
     */
    public static String getFileName(Resource resource) {
        return getFileName(resource, getVersion(resource));
    }

    public static String getString(Resource resource) {
        return getIdentity(resource) + "/" + getVersion(resource) + "/" + getType(resource) + " - " + getUrl(resource);
    }

    public static String getIdentity(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.identity");
        if (attrs == null)
            return null;
        return (String) attrs.get("osgi.identity");

    }

    public static Requirement getIdentityVersionRequirement(Resource resource) {
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", String.format("(&(osgi.identity=%s)(version=%s)(type=*))", getIdentity(resource), getVersion(resource)))
            .buildSyntheticRequirement();
        return requirement;
    }

    public static List<Resource> copyResources(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, String bsn) throws Exception {
        return copyResources(sourceRepo, targetRepo, bsn, "*");
    }

    public static List<Resource> copyResources(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, String bsn, String version) throws Exception {
        return copyResources(sourceRepo, targetRepo, bsn, version, "*");
    }

    public static List<Resource> copyResources(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, String bsn, String version, String type) throws Exception {
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", String.format("(&(osgi.identity=%s)(version=%s)(type=%s))", bsn, version, type))
            .buildSyntheticRequirement();
        return copyResources(sourceRepo, targetRepo, requirement);
    }

    public static List<Resource> copyResources(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, Requirement requirement) throws Exception {
        List<Resource> sourceResources = findResources(sourceRepo, requirement);
        List<Resource> targetResources = new ArrayList<>();
        for (Resource resource : sourceResources) {
            targetResources.add(copyResource(sourceRepo, targetRepo, resource));
        }
        return targetResources;
    }

    public static List<Resource> copyResources(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, List<Resource> resources) throws Exception {
        List<Resource> targetResources = new LinkedList<>();
        for (Resource resource : resources) {
            Resource targetResource = copyResource(sourceRepo, targetRepo, resource);
            targetResources.add(targetResource);
        }
        return targetResources;
    }

    public static Resource copyResource(AbstractIndexedRepo sourceRepo, AbstractIndexedRepo targetRepo, Resource resource) throws Exception {

        File file = sourceRepo.get(getIdentity(resource), getVersion(resource).toString(), Strategy.EXACT, null);
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            if (targetRepo instanceof AceObrRepository) {
                // ACE OBR can handle non bundle resource if we pass a filename
                AceObrRepository aceToRepo = (AceObrRepository) targetRepo;
                aceToRepo.upload(input, getFileName(resource), getMimetype(resource));
            }
            else {
                targetRepo.put(input, null);
            }
            targetRepo.reset();

            List<Resource> resultResources = findResources(targetRepo, getIdentity(resource), getVersion(resource).toString());
            if (resultResources == null || resultResources.size() == 0) {
                throw new IllegalStateException("Unable to locate target resource after copy: " + resource);
            }
            return resultResources.get(0);
        }
        finally {
            if (input != null)
                input.close();
        }
    }

    public static void uploadResource(AbstractIndexedRepo targetRepo, URL location, String filename) throws Exception {
        InputStream input = null;
        try {
            input = location.openStream();
            if (targetRepo instanceof AceObrRepository) {
                // ACE OBR can handle non bundle resource if we pass a filename
                AceObrRepository aceToRepo = (AceObrRepository) targetRepo;
                aceToRepo.upload(input, filename, null);
            }
            else {
                targetRepo.put(input, null);
            }
        }
        finally {
            if (input != null)
                input.close();
        }
    }

    public static List<Resource> findResources(Repository repository) {
        return findResources(repository, "*");
    }

    public static List<Resource> findResources(Repository repository, String bsn) {
        return findResources(repository, bsn, "*");
    }

    public static List<Resource> findResources(Repository repository, String bsn, String version) {
        return findResources(repository, bsn, version, "*");
    }

    public static List<Resource> findResources(Repository repository, String bsn, String version, String type) {
        Requirement requirement = new CapReqBuilder("osgi.identity")
            .addDirective("filter", String.format("(&(osgi.identity=%s)(version=%s)(type=%s))", bsn, version, type))
            .buildSyntheticRequirement();
        return findResources(repository, requirement);
    }

    public static List<Resource> findResources(Repository repository, Requirement requirement) {
        if (requirement == null) {
            // FIXME maybe we can just pass null
            requirement = new CapReqBuilder("osgi.identity")
                .addDirective("filter", "(&(osgi.identity=*)(version=*)(type=*))")
                .buildSyntheticRequirement();
        }

        Map<Requirement, Collection<Capability>> sourceResources = repository.findProviders(Collections.singleton(requirement));
        if (sourceResources.isEmpty() || sourceResources.get(requirement).isEmpty()) {
            return Collections.emptyList();
        }
        List<Resource> resources = new ArrayList<>();
        Iterator<Capability> capabilities = sourceResources.get(requirement).iterator();
        while (capabilities.hasNext()) {
            Capability capability = capabilities.next();
            resources.add(capability.getResource());
        }
        return resources;
    }

    public static Version getVersion(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.identity");
        if (attrs == null)
            return Version.emptyVersion;
        Version version = (Version) attrs.get("version");
        return version == null ? Version.emptyVersion : version;
    }

    public static List<Version> getVersions(List<Resource> resources) {
        List<Version> versions = new ArrayList<>();
        for (Resource resource : resources) {
            versions.add(getVersion(resource));
        }
        return versions;
    }

    public static String getType(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.identity");
        if (attrs == null)
            return null;
        return (String) attrs.get("type");
    }

    public static String getUrl(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.content");
        if (attrs == null)
            return null;
        URI url = (URI) attrs.get("url");
        return url == null ? null : url.toString();
    }

    public static String getMimetype(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.content");
        if (attrs == null)
            return null;

        String mime = (String) attrs.get("mime");
        if (mime == null) {
            // FIXME this is a work around for OBR not supporting mimetype
            String url = getUrl(resource);
            if (url.endsWith(".jar")) {
                mime = "application/vnd.osgi.bundle";
            }
            else if (url.endsWith(".xml")) {
                mime = "application/xml:osgi-autoconf";
            }
        }
        return mime;
    }

    public static String getSHA(Resource resource) {
        Map<String, Object> attrs = getNamespaceAttributes(resource, "osgi.content");
        if (attrs == null)
            return null;
        return (String) attrs.get("osgi.content");
    }

    private static Map<String, Object> getNamespaceAttributes(Resource resource, String namespace) {
        List<Capability> caps = resource.getCapabilities(namespace);
        if (caps.isEmpty())
            return null;
        Map<String, Object> attrs = caps.get(0).getAttributes();
        if (attrs == null)
            return null;
        return attrs;
    }
}
