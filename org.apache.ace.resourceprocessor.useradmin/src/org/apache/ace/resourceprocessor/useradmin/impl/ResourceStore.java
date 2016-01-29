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
package org.apache.ace.resourceprocessor.useradmin.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.osgi.framework.BundleContext;
import org.osgi.service.deploymentadmin.spi.ResourceProcessorException;

/**
 * The ResourceStore keeps track of resources, keeps them around in a separate storage for installing and removing, and
 * keeps track of which resource belongs to which deployment package. The information stored by the ResourceStore will
 * be persisted as often as possible, to allow crash recovery.
 */
abstract class ResourceStore
{
    private static final int BUFFER_SIZE = 1024;

    private static final String TEMP_DIR = "resources";

    private final BundleContext m_context;
    Map<String, String> m_resources;

    ResourceStore(BundleContext context) {
        m_context = context;

        File baseDir = m_context.getDataFile(TEMP_DIR);

        m_resources = new HashMap<>();

        // Fill our resources overview with the data that is available on disk.
        File[] deploymentPackageList = baseDir.listFiles();
        if (deploymentPackageList != null) {
            for (File resourceDirectory : deploymentPackageList) {
                if (resourceDirectory.isDirectory()) {
                    String[] fileList = resourceDirectory.list();
                    if (fileList != null) {
                        for (String resourceName : fileList) {
                            m_resources.put(resourceName, resourceDirectory.getName());
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a resource to persistent storage and handles the administration.
     *
     * @param deploymentPackageName
     *            the name of a deployment package
     * @param name
     *            the name of the resource
     * @param stream
     *            a stream from which the resource with <code>name</code> can be read
     */
    public void addResource(String deploymentPackageName, String name, InputStream stream) throws IOException, ResourceProcessorException {
        synchronized (m_resources) {
            File resourceDirectory = new File(m_context.getDataFile(TEMP_DIR), deploymentPackageName);
            resourceDirectory.mkdirs();

            File resourceFile = new File(resourceDirectory, name);
            if (resourceFile.exists()) {
                resourceFile.delete();
            }

            FileOutputStream resourceStream = null;
            try {
                resourceFile.createNewFile();
                resourceStream = new FileOutputStream(resourceFile);

                byte[] buf = new byte[BUFFER_SIZE];
                for (int count = stream.read(buf); count != -1; count = stream.read(buf)) {
                    resourceStream.write(buf, 0, count);
                }
            }
            finally {
                if (resourceStream != null) {
                    try {
                        resourceStream.close();
                    }
                    catch (IOException ioe) {
                        // nothing to do
                    }
                }
            }

            try {
                InputStream input = new FileInputStream(resourceFile);
                validate(input);
                input.close();
            }
            catch (Exception e) {
                resourceFile.delete();
                throw new ResourceProcessorException(ResourceProcessorException.CODE_OTHER_ERROR, "Error validating resource.", e);
            }

            m_resources.put(name, deploymentPackageName);
        }
    }

    /**
     * Checks the validity of a resource.
     *
     * @param resource
     *            a stream containing the resource
     * @throws Exception
     *             when something is wrong with the resource
     */
    public abstract void validate(InputStream resource) throws Exception;

    /**
     * Marks the start of a deployment process.
     */
    public abstract void begin();

    /**
     * Marks the end of a deployment process.
     */
    public abstract void end();

    /**
     * Installs a given resource.
     *
     * @param resourceName
     *            the name of the resource
     */
    public abstract void install(String resourceName) throws IOException;

    /**
     * Uninstalls a given resource.
     *
     * @param resourceName
     *            the name of the resource
     */
    public abstract void uninstall(String resourceName) throws IOException;

    /**
     * Gets the names of all driver bundles that belong to a given deployment package.
     *
     * @param deploymentPackageName
     *            the name of a deployment package
     * @return a list of the names of all driver bundles that belong to <code>deploymentPackageName</code>
     */
    public List<String> getResources(String deploymentPackageName) {
        synchronized (m_resources) {
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : m_resources.entrySet()) {
                if (entry.getValue().equals(deploymentPackageName)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }
    }

    /**
     * Gets the name of the deployment package to which a given resource belongs.
     *
     * @param resourceName
     *            the name of a resource
     * @return the name of the deployment package to which <code>resourceName</code> belongs, or <code>null</code> if
     *         this resource is unknown
     */
    public String getDeploymentPackage(String resourceName) {
        return m_resources.get(resourceName);
    }

    /**
     * Gets the stream belonging to a given resource.
     *
     * @param name
     *            the name of a the resource
     * @return an InputStream providing access to the named resource. It is the caller's task to close it.
     * @throws java.io.IOException
     *             when an exception occurs accessing the resource
     */
    protected InputStream getResource(String name) throws IOException {
        File resource = new File(new File(m_context.getDataFile(TEMP_DIR), m_resources.get(name)), name);
        return new FileInputStream(resource);
    }
}
