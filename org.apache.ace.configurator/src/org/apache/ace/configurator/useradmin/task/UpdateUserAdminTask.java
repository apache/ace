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
package org.apache.ace.configurator.useradmin.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.BackupRepository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.ext.impl.CachedRepositoryImpl;
import org.apache.ace.repository.ext.impl.FilebasedBackupRepository;
import org.apache.ace.resourceprocessor.useradmin.UserAdminConfigurator;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * UpdateUserAdminTask processes the contents of a repository containing an XML description of the users that should be
 * present in this system's user admin.<br>
 * <br>
 * From the first run on, this task will keep a local copy of the user repository, so login can happen when the server
 * is offline.
 */
public class UpdateUserAdminTask implements Runnable, ManagedService {
    /**
     * The UpdateUserAdminTask is also used as its taskName for the scheduler.
     */
    public static final String PID = UpdateUserAdminTask.class.getName();

    public static final String KEY_REPOSITORY_LOCATION = "repositoryLocation";
    public static final String KEY_REPOSITORY_CUSTOMER = "repositoryCustomer";
    public static final String KEY_REPOSITORY_NAME = "repositoryName";

    private static final String FILE_ROOT = "userrepositories";
    private static final String VERSION = "version";

    // Will by injected by Dependency Manager...
    private volatile UserAdminConfigurator m_configurator;
    private volatile LogService m_log;
    private volatile BundleContext m_context;

    private CachedRepository m_repo;
    private BackupRepository m_backup;
    private String m_repoFilter;
    private File m_properties;

    /**
     * Called by Dependency Manager upon initialization of this component.
     * <p>
     * Due to the dependency on the configuration; the {@link #updated(Dictionary)} method is already called!
     * </p>
     * 
     * @param comp
     *            this component, cannot be <code>null</code>.
     */
    public void init(Component comp) {
        final DependencyManager dm = comp.getDependencyManager();
        // Add the required dependency to the remote repository...
        comp.add(dm.createServiceDependency()
            .setService(Repository.class, m_repoFilter)
            .setCallbacks("addRepo", "removeRepo")
            .setInstanceBound(true)
            .setRequired(true)
            );
    }

    /**
     * Checks whether there are updates to the remote repository, and if so, updates the users' backend with its
     * contents.
     * 
     * @see java.lang.Runnable#run()
     */
    public void run() {
        try {
            if (!m_repo.isCurrent()) {
                m_log.log(LogService.LOG_DEBUG, "UpdateUserAdminTask updating to latest version...");

                m_configurator.setUsers(m_repo.checkout(true));

                saveVersion(m_properties, m_repo.getMostRecentVersion());

                m_log.log(LogService.LOG_DEBUG, "UpdateUserAdminTask updated to latest version: " + m_repo.getMostRecentVersion());
            }
        }
        catch (ConnectException e) {
            // ACE-199: log only a single line, instead of a complete stack trace...
            m_log.log(LogService.LOG_WARNING, "Failed to update UserAdmin repository. Connection refused (is the server down?!)");
        }
        catch (IOException e) {
            // If anything went wrong, this means the remote repository is not available;
            // this also means the UserAdmin is left undisturbed.
            m_log.log(LogService.LOG_WARNING, "Failed to update UserAdmin repository.", e);

            try {
                m_log.log(LogService.LOG_DEBUG, "UpdateUserAdminTask failed to load remote data; falling back to local data.");
                // If reading remote fails, try to set whatever we have locally
                m_configurator.setUsers(m_repo.getLocal(true));
            }
            catch (IOException e2) {
                // No problem, now we just have an empty user admin...
                m_log.log(LogService.LOG_DEBUG, "UpdateUserAdminTask failed to load local data.");
            }
        }
    }

    /**
     * Called by Dependency Manager upon starting of this component.
     * 
     * @param comp
     *            this component, cannot be <code>null</code>.
     */
    public void start(Component comp) {
        // ACE-452: run at least once at start-up...
        run();
    }

    public void updated(Dictionary dict) throws ConfigurationException {
        if (dict != null) {
            String customer = (String) dict.get(KEY_REPOSITORY_CUSTOMER);
            if (customer == null) {
                throw new ConfigurationException(KEY_REPOSITORY_CUSTOMER, "Property missing.");
            }
            String name = (String) dict.get(KEY_REPOSITORY_NAME);
            if (name == null) {
                throw new ConfigurationException(KEY_REPOSITORY_NAME, "Property missing.");
            }

            String fileRoot = FILE_ROOT + File.separator + customer + File.separator + name + File.separator;

            File local = getFile(fileRoot + "local");
            File backup = getFile(fileRoot + "backup");
            m_backup = new FilebasedBackupRepository(local, backup);

            m_properties = getFile(fileRoot + "properties");

            m_repoFilter = "(&(customer=" + customer + ")(name=" + name + "))";
        }
    }

    /**
     * Creates the cached repository when given a remote repository.
     * 
     * @param remoteRepo
     *            the remote repository to add, cannot be <code>null</code>.
     */
    final void addRepo(Repository remoteRepo) {
        m_repo = new CachedRepositoryImpl(remoteRepo, m_backup, loadVersion(m_properties));
    }

    /**
     * Removes the cached repository when given a remote repository.
     * 
     * @param remoteRepo
     *            the remote repository to remove, cannot be <code>null</code>.
     */
    final void removeRepo(Repository remoteRepo) {
        // Ensure the latest version is properly stored...
        saveVersion(m_properties, m_repo.getMostRecentVersion());

        m_repo = null;
    }

    private File getFile(String name) {
        File result = m_context.getDataFile(name);
        if (!result.exists()) {
            result.getParentFile().mkdirs();
            try {
                if (!result.createNewFile()) {
                    m_log.log(LogService.LOG_ERROR, "Error creating new file " + name);
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Error creating new file " + name, e);
            }
        }
        return result;
    }

    /**
     * Loads the most recent version from the given properties file.
     */
    private long loadVersion(File propertiesfile) {
        long result = CachedRepositoryImpl.UNCOMMITTED_VERSION;
        try {
            Properties props = new Properties();
            props.loadFromXML(new FileInputStream(propertiesfile));
            result = Long.parseLong((String) props.get(VERSION));
        }
        catch (IOException ioe) {
            // We have no data; no problem.
        }
        return result;
    }

    /**
     * Saves the most recent version to the given properties file.
     */
    private void saveVersion(File properties, Long version) {
        Properties props = new Properties();
        props.put(VERSION, version.toString());
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(properties);
            props.storeToXML(fileOutputStream, null);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "UpdateUserAdminTask failed to save local version number.");
        }
    }
}
