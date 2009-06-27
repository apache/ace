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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.impl.CachedRepositoryImpl;
import org.apache.ace.resourceprocessor.useradmin.UserAdminConfigurator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * UpdateUserAdminTask processes the contents of a repository containing
 * an XML description of the users that should be present in this system's
 * user admin.<br>
 * <br>
 * From the first run on, this task will keep a local copy of the user repository,
 * so login can happen when the server is offline.
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

    private volatile UserAdminConfigurator m_configurator; /* Will be injected by dependency manager */
    private volatile LogService m_log; /* Will be injected by dependency manager */
    private volatile BundleContext m_context; /* Will be injected by dependency manager */

    private CachedRepository m_repo;
    private File m_properties;

    public void run() {
        try {
            if (!m_repo.isCurrent()) {
                m_configurator.setUsers(m_repo.checkout(true));
                m_log.log(LogService.LOG_DEBUG, "UpdateUserAdminTask updates to a new version: " + m_repo.getMostRecentVersion());
                saveVersion(m_properties, m_repo.getMostRecentVersion());
            }
        }
        catch (IOException e) {
            // If anything went wrong, this means the remote repository is not available;
            // this also means the UserAdmin is left undisturbed.
        }
    }

    public void start() {
        try {
            // Try to read the server data
            m_configurator.setUsers(m_repo.checkout(true));
        }
        catch (IOException e) {
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

    public void updated(Dictionary dict) throws ConfigurationException {
        if (dict != null) {
            String locationString = (String) dict.get(KEY_REPOSITORY_LOCATION);
            if (locationString == null) {
                throw new ConfigurationException(KEY_REPOSITORY_LOCATION, "Property missing.");
            }
            URL location;
            try {
                location = new URL(locationString);
            }
            catch (MalformedURLException e) {
                throw new ConfigurationException(KEY_REPOSITORY_LOCATION, "Location " + locationString + " is not a valid URL.");
            }
            String customer = (String) dict.get(KEY_REPOSITORY_CUSTOMER);
            if (customer == null) {
                throw new ConfigurationException(KEY_REPOSITORY_CUSTOMER, "Property missing.");
            }
            String name = (String) dict.get(KEY_REPOSITORY_NAME);
            if (name == null) {
                throw new ConfigurationException(KEY_REPOSITORY_NAME, "Property missing.");
            }

            String fileRoot = FILE_ROOT + File.separator + location.getAuthority().replace(':', '-') + location.getPath().replace('/', '\\') + File.separator + customer + File.separator + name + File.separator;
            File local = getFile(fileRoot + "local");
            File backup = getFile(fileRoot + "backup");
            m_properties = getFile(fileRoot + "properties");

            m_repo = new CachedRepositoryImpl(null, location, customer, name, local, backup, loadVersion(m_properties));
        }
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

}
