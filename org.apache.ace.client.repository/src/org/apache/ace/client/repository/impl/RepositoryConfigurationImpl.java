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
package org.apache.ace.client.repository.impl;

import static org.apache.ace.client.repository.repository.RepositoryConstants.KEY_DEPLOYMENT_VERSION_LIMITS;
import static org.apache.ace.client.repository.repository.RepositoryConstants.KEY_OBR_LOCATION;
import static org.apache.ace.client.repository.repository.RepositoryConstants.KEY_SHOW_UNREGISTERED_TARGETS;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.ace.client.repository.repository.RepositoryConfiguration;

/**
 * Helper class to make a type-safe configuration object for holding the configuration of repositories.
 * <p>
 * This class is thread-safe.
 * </p>
 */
final class RepositoryConfigurationImpl implements RepositoryConfiguration {
    private static final boolean DEFAULT_SHOW_UNREGISTERED_TARGETS = true;
    private static final int DEFAULT_DEPLOYMENT_VERSION_LIMIT = -1;
    private static final URL DEFAULT_OBR_LOCATION;

    static {
        try {
            DEFAULT_OBR_LOCATION = new URL("http://localhost:8080/obr/");
        }
        catch (MalformedURLException exception) {
            throw new RuntimeException("Invalid default URL!", exception);
        }
    }

    private final ReadWriteLock m_lock = new ReentrantReadWriteLock();

    private boolean m_showUnregisteredTargets;
    private int m_deploymentVersionLimit;
    private URL m_obrLocation;

    /**
     * Creates a new {@link RepositoryConfigurationImpl} instance.
     */
    public RepositoryConfigurationImpl() {
        m_showUnregisteredTargets = DEFAULT_SHOW_UNREGISTERED_TARGETS;
        m_deploymentVersionLimit = DEFAULT_DEPLOYMENT_VERSION_LIMIT;
        m_obrLocation = DEFAULT_OBR_LOCATION;
    }

    /**
     * Creates a new {@link RepositoryConfigurationImpl} instance.
     */
    public RepositoryConfigurationImpl(RepositoryConfiguration defaultConfig) {
        m_showUnregisteredTargets = defaultConfig.isShowUnregisteredTargets();
        m_deploymentVersionLimit = defaultConfig.getDeploymentVersionLimit();
        m_obrLocation = defaultConfig.getOBRLocation();
    }

    /**
     * Creates a new {@link RepositoryConfigurationImpl} instance.
     */
    public RepositoryConfigurationImpl(RepositoryConfiguration defaultConfig, Map<String, Object> map) {
        m_showUnregisteredTargets = parseBoolean(map.get(KEY_SHOW_UNREGISTERED_TARGETS), defaultConfig.isShowUnregisteredTargets());
        m_deploymentVersionLimit = parseInteger(map.get(KEY_DEPLOYMENT_VERSION_LIMITS), defaultConfig.getDeploymentVersionLimit());
        m_obrLocation = parseURL(map.get(KEY_OBR_LOCATION), defaultConfig.getOBRLocation());
    }

    @Override
    public int getDeploymentVersionLimit() {
        Lock lock = m_lock.readLock();
        lock.lock();
        try {
            return m_deploymentVersionLimit;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public URL getOBRLocation() {
        Lock lock = m_lock.readLock();
        lock.lock();
        try {
            return m_obrLocation;
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isShowUnregisteredTargets() {
        Lock lock = m_lock.readLock();
        lock.lock();
        try {
            return m_showUnregisteredTargets;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Sets the number of deployment versions to retain per target.
     * 
     * @param deploymentVersionLimit
     *            the limit to set, > 0 or -1 if no limit is to be imposed.
     */
    public void setDeploymentVersionLimit(int deploymentVersionLimit) {
        if (deploymentVersionLimit == 0) {
            throw new IllegalArgumentException("Deployment version limit cannot be zero!");
        }

        Lock lock = m_lock.writeLock();
        lock.lock();
        try {
            m_deploymentVersionLimit = deploymentVersionLimit;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * @param base
     *            the base OBR location to set, should not be <code>null</code>.
     */
    public void setObrLocation(URL base) {
        if (base == null) {
            throw new IllegalArgumentException("Base URL cannot be null!");
        }

        Lock lock = m_lock.writeLock();
        lock.lock();
        try {
            m_obrLocation = base;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * @param showUnregisteredTargets
     *            the showUnregisteredTargets to set, can be <code>null</code>.
     */
    public void setShowUnregisteredTargets(boolean showUnregisteredTargets) {
        Lock lock = m_lock.writeLock();
        lock.lock();
        try {
            m_showUnregisteredTargets = showUnregisteredTargets;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Updates the configuration atomically.
     * 
     * @param dict
     *            the dictionary with the new configuration options, can be <code>null</code> in case the default values
     *            should be used.
     */
    public void update(Dictionary<String, ?> dict) {
        Lock lock = m_lock.writeLock();
        lock.lock();
        try {
            if (dict == null) {
                m_showUnregisteredTargets = DEFAULT_SHOW_UNREGISTERED_TARGETS;
                m_deploymentVersionLimit = DEFAULT_DEPLOYMENT_VERSION_LIMIT;
                m_obrLocation = DEFAULT_OBR_LOCATION;
            }
            else {
                m_showUnregisteredTargets = parseBoolean(dict.get(KEY_SHOW_UNREGISTERED_TARGETS), m_showUnregisteredTargets);
                m_deploymentVersionLimit = parseInteger(dict.get(KEY_DEPLOYMENT_VERSION_LIMITS), m_deploymentVersionLimit);
                m_obrLocation = parseURL(dict.get(KEY_OBR_LOCATION), m_obrLocation);
            }
        }
        finally {
            lock.unlock();
        }
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private static int parseInteger(Object value, int defaultValue) {
        if (value instanceof Integer) {
            return ((Integer) value).intValue();
        }
        else if (value instanceof String) {
            try {
                return Integer.valueOf((String) value);
            }
            catch (NumberFormatException exception) {
                // Ignore, use default value...
            }
        }
        return defaultValue;
    }

    private static URL parseURL(Object value, URL defaultValue) {
        if (value instanceof URL) {
            return (URL) value;
        }
        else if (value instanceof String) {
            try {
                String url = (String) value;
                // ensure OBR-url always ends with a single forward slash...
                return new URL(url.replaceAll("/*$", "/"));
            }
            catch (MalformedURLException exception) {
                // Ignore, use default value...
            }
        }
        return defaultValue;
    }
}
