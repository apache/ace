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
package org.apache.ace.repository.impl;

import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_BASE_DIR;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_CUSTOMER;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_FILE_EXTENSION;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_INITIAL_CONTENT;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_LIMIT;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_MASTER;
import static org.apache.ace.repository.RepositoryConstants.REPOSITORY_NAME;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.RepositoryReplication;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

/**
 * A <code>ManagedServiceFactory</code> responsible for creating a (<code>Replication</code>)<code>Repository</code>
 * instance for each valid configuration that is received from the <code>ConfigurationAdmin</code>.
 */
public class RepositoryFactory implements ManagedServiceFactory {

    public static class Entry {
        private final String m_customer;
        private final String m_name;

        public Entry(String customer, String name) {
            m_customer = customer;
            m_name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Entry other = (Entry) obj;
            if (!m_customer.equals(other.m_customer)) {
                return false;
            }
            if (!m_name.equals(other.m_name)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 37;
            int result = 1;
            result = prime * result + ((m_customer == null) ? 0 : m_customer.hashCode());
            result = prime * result + ((m_name == null) ? 0 : m_name.hashCode());
            return result;
        }
        
        @Override
        public String toString() {
            return String.format("%s :: %s", m_customer, m_name);
        }
    }

    private final ConcurrentMap<String, Component> m_instances = new ConcurrentHashMap<>();
    private final ConcurrentMap<Entry, String> m_index = new ConcurrentHashMap<>();
    private final DependencyManager m_manager;

    /* injected by dependency manager */
    private volatile LogService m_log;
    private volatile BundleContext m_context;
    // locally managed
    private File m_tempDir;

    public RepositoryFactory(DependencyManager manager) {
        m_manager = manager;
    }

    public void deleted(String pid) {
        // remove repository service...
        Component service = m_instances.remove(pid);
        if (service != null) {
            RepositoryImpl repository = (RepositoryImpl) service.getInstance();
            File repoDir = repository.getDir();

            m_manager.remove(service);
            
            // update our local index...
            Map<Entry, String> index = new HashMap<>(m_index);
            for (Map.Entry<Entry, String> entry : index.entrySet()) {
                if (pid.equals(entry.getValue())) {
                    m_index.remove(entry.getKey(), entry.getValue());
                }
            }

            // remove persisted data...
            deleteRepositoryStore(pid, repoDir);
        }
    }

    @Override
    public String getName() {
        return "RepositoryFactory";
    }

    /**
     * Called by Felix DM.
     */
    public void init() throws IOException {
        m_tempDir = ensureDirectoryAvailable(m_context.getDataFile("tmp"));
    }

    /**
     * Creates a new instance if the supplied dictionary contains a valid configuration. A configuration is valid if
     * <code>RepositoryConstants.REPOSITORY_NAME</code> and <code>RepositoryConstants.REPOSITORY_CUSTOMER</code>
     * properties are present, not empty and the combination of the two is unique in respect to other previously created
     * instances. Finally a property <code>RepositoryConstants.REPOSITORY_MASTER</code> should be present and be either
     * <code>true</code> or <code>false</code>.
     * 
     * @param pid
     *            A unique identifier for the instance, generated by <code>ConfigurationAdmin</code> normally.
     * @param dict
     *            The configuration properties for the instance, see description above.
     * @throws ConfigurationException
     *             If any of the above explanation fails <b>or</b>when there is an internal error creating the
     *             repository.
     */
    public void updated(String pid, Dictionary<String, ?> dict) throws ConfigurationException {
        String customer = (String) dict.get(REPOSITORY_CUSTOMER);
        if ((customer == null) || "".equals(customer)) {
            throw new ConfigurationException(REPOSITORY_CUSTOMER, "Repository customer has to be specified.");
        }

        String name = (String) dict.get(REPOSITORY_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(REPOSITORY_NAME, "Repository name has to be specified.");
        }
        
        // Check whether the combination of customer and name is unique...
        Entry newEntry = new Entry(customer, name);
        String oldPid = m_index.putIfAbsent(newEntry, pid);
        if (oldPid != null && !pid.equals(oldPid)) {
            throw new ConfigurationException(null, "Name and customer combination already exists");
        }

        String master = (String) dict.get(REPOSITORY_MASTER);
        if (!("false".equalsIgnoreCase(master.trim()) || "true".equalsIgnoreCase(master.trim()))) {
            throw new ConfigurationException(REPOSITORY_MASTER, "Have to specify whether the repository is the master or a slave.");
        }
        boolean isMaster = Boolean.parseBoolean(master);

        String fileExtension = (String) dict.get(REPOSITORY_FILE_EXTENSION);
        if ((fileExtension == null) || "".equals(fileExtension.trim())) {
            fileExtension = "";
        }

        String baseDirName = (String) dict.get(REPOSITORY_BASE_DIR);
        File baseDir;
        if (baseDirName == null || "".equals(baseDirName.trim())) {
            baseDir = m_context.getDataFile("repos");
        }
        else {
            baseDir = new File(baseDirName);
        }

        String limit = (String) dict.get(REPOSITORY_LIMIT);
        long limitValue = Long.MAX_VALUE;
        if (limit != null) {
            try {
                limitValue = Long.parseLong(limit);
            }
            catch (NumberFormatException nfe) {
                throw new ConfigurationException(REPOSITORY_LIMIT, "Limit has to be a number, was: " + limit);
            }
            if (limitValue < 1) {
                throw new ConfigurationException(REPOSITORY_LIMIT, "Limit has to be at least 1, was " + limit);
            }
        }

        String initialContents = (String) dict.get(REPOSITORY_INITIAL_CONTENT);

        Component service = m_manager.createComponent()
            .setInterface(new String[] { RepositoryReplication.class.getName(), Repository.class.getName() }, dict)
            .setImplementation(createRepositoryStore(pid, baseDir, isMaster, limitValue, fileExtension, initialContents))
            .add(m_manager.createServiceDependency().setService(LogService.class).setRequired(false));

        Component oldService = m_instances.putIfAbsent(pid, service);
        if (oldService == null) {
            // new instance...
            m_manager.add(service);
        }
        else {
            // update existing instance...
            RepositoryImpl store = (RepositoryImpl) oldService.getInstance();

            // be a little pedantic about the ignored properties...
            if (!baseDir.equals(store.getDir())) {
                m_log.log(LogService.LOG_WARNING, "Cannot update base directory of repository from " + store.getDir() + " to " + baseDir);
            }
            if (!fileExtension.equals(store.getFileExtension())) {
                m_log.log(LogService.LOG_WARNING, "Cannot update file extension of repository from " + store.getFileExtension() + " to " + fileExtension);
            }

            store.updated(isMaster, limitValue);
        }
    }

    private RepositoryImpl createRepositoryStore(String pid, File baseDir, boolean isMaster, long limitValue, String fileExtension, String initialContents) {
        File dir = ensureDirectoryAvailable(new File(baseDir, pid));
        RepositoryImpl store = new RepositoryImpl(dir, m_tempDir, fileExtension, isMaster, limitValue);
        if ((initialContents != null) && isMaster) {
            try {
                // Do not even try to commit initial contents for existing repositories...
                if (store.getRange().getHigh() == 0L) {
                    store.commit(new ByteArrayInputStream(initialContents.getBytes()), 0L);
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Unable to set initial contents of the repository.", e);
            }
        }
        return store;
    }

    private void deleteRepositoryStore(String pid, File repoDir) {
        if (repoDir.exists() && repoDir.isDirectory()) {
            File[] files = repoDir.listFiles();
            for (int i = 0; (files != null) && (i < files.length); i++) {
                files[i].delete();
            }
            if (!repoDir.delete()) {
                m_log.log(LogService.LOG_WARNING, "Unable to clean up files in " + repoDir.getAbsolutePath() + " after removing repository!");
            }
        }
    }

    private File ensureDirectoryAvailable(File dir) {
        if (dir == null) {
            throw new IllegalArgumentException("Unable to use file system!");
        }
        if (dir.exists() && dir.isFile()) {
            dir.delete();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalArgumentException("Unable to create directory: " + dir.getAbsolutePath() + "!");
        }
        return dir;
    }
}
