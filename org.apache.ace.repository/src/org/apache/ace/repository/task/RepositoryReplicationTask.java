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
package org.apache.ace.repository.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletResponse;

import org.amdatu.scheduling.Job;
import org.amdatu.scheduling.constants.Constants;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.RepositoryReplication;
import org.apache.felix.dm.Component;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Repository replication task. Uses discovery to find the server it talks to. Subsequently it checks which local
 * repositories are configured and tries to synchronize them with remote copies. Only pulls stuff in, it does not push
 * stuff out.
 */
public class RepositoryReplicationTask implements Job, ManagedService {
    private static final String KEY_SYNC_INTERVAL = "syncInterval";

    private final ConcurrentMap<ServiceReference<RepositoryReplication>, RepositoryReplication> m_replicators = new ConcurrentHashMap<>();
    
    private volatile Component m_component;
    private volatile Discovery m_discovery;
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogService m_log;

    /**
     * Called by Felix DM when a {@link RepositoryReplication} service becomes available.
     */
    public void add(ServiceReference<RepositoryReplication> ref, RepositoryReplication service) {
        if (m_replicators.putIfAbsent(ref, service) != null) {
            m_log.log(LogService.LOG_WARNING, "Ignoring duplicate repository replication service for '" + ref.getProperty("name") + "'!");
        }
    }

    /**
     * Called by Felix DM when a {@link RepositoryReplication} service goes away.
     */
    public void remove(ServiceReference<RepositoryReplication> ref, RepositoryReplication service) {
        if (!m_replicators.remove(ref, service)) {
            m_log.log(LogService.LOG_WARNING, "Repository replication service '" + ref.getProperty("name") + "' not removed?!");
        }
    }

    /**
     * Replicates all current known repositories.
     */
    @Override
    public void execute() {
        // Take a snapshot of the current available replicators...
        Map<ServiceReference<RepositoryReplication>, RepositoryReplication> replicators = new HashMap<>(m_replicators);

        // The URL to the server to replicate...
        URL master = m_discovery.discover();

        for (Entry<ServiceReference<RepositoryReplication>, RepositoryReplication> entry : replicators.entrySet()) {
            ServiceReference<RepositoryReplication> ref = entry.getKey();
            RepositoryReplication repository = entry.getValue();

            try {
                replicate(master, ref, repository);
            }
            catch (Exception e) {
                m_log.log(LogService.LOG_WARNING, "Replicating repository '" + ref.getProperty("name") + "' failed!", e);
            }
        }
    }

    private HttpURLConnection createConnection(URL url) throws IOException {
        return (HttpURLConnection) m_connectionFactory.createConnection(url);
    }

    private URL createGetURL(URL master, String customer, String name, long version) throws MalformedURLException {
        return new URL(master, String.format("/replication/get?customer=%s&name=%s&version=%d", customer, name, version));
    }

    private URL createQueryURL(URL master, String customer, String name) throws MalformedURLException {
        return new URL(master, String.format("/replication/query?customer=%s&name=%s", customer, name));
    }

    private boolean replicateRepository(URL master, String customer, String name, RepositoryReplication repository, HttpURLConnection queryConn) throws IOException {
        SortedRangeSet localRange = repository.getRange();
        boolean result = false;

        BufferedReader reader = new BufferedReader(new InputStreamReader(queryConn.getInputStream()));
        try {
            String line = reader.readLine();
            int i = line.lastIndexOf(',');
            if (i <= 0) {
                return result;
            }

            SortedRangeSet remoteRange = new SortedRangeSet(line.substring(i + 1));

            // check the limit of the repository
            long limit = repository.getLimit();
            if (limit == Long.MAX_VALUE) {
                // no limit, sync all
                SortedRangeSet delta = localRange.diffDest(remoteRange);
                RangeIterator iterator = delta.iterator();
                while (iterator.hasNext()) {
                    long version = iterator.next();
                    replicateVersion(master, customer, name, repository, version);
                    result = true;
                }
            }
            else {
                // limit, try to get the the 'limit' newest versions
                SortedRangeSet union = localRange.union(remoteRange);
                RangeIterator iterator = union.reverseIterator();
                while (iterator.hasNext() && limit > 0) {
                    long version = iterator.next();
                    if (!localRange.contains(version)) {
                        replicateVersion(master, customer, name, repository, version);
                    }
                    limit--;
                    result = true;
                }
            }

            return result;
        }
        finally {
            reader.close();
        }
    }

    private void replicate(URL master, ServiceReference<RepositoryReplication> ref, RepositoryReplication repository) throws IOException {
        String customer = (String) ref.getProperty("customer");
        String name = (String) ref.getProperty("name");

        HttpURLConnection connection = createConnection(createQueryURL(master, customer, name));
        try {
            int rc = connection.getResponseCode();
            if (rc == HttpServletResponse.SC_OK) {
                if (replicateRepository(master, customer, name, repository, connection)) {
                    m_log.log(LogService.LOG_DEBUG, String.format("Repository '%s' (%s) successfully replicated...", name, customer));
                }
            }
            else {
                String msg = connection.getResponseMessage();
                m_log.log(LogService.LOG_WARNING, String.format("Could not replicate repository '%s' (%s). Server response: %s (%d)", name, customer, msg, rc));
            }
        }
        finally {
            connection.disconnect();
        }
    }

    private void replicateVersion(URL master, String customer, String name, RepositoryReplication repository, long version) throws IOException {
        HttpURLConnection conn = createConnection(createGetURL(master, customer, name, version));
        try {
            repository.put(conn.getInputStream(), version);

            m_log.log(LogService.LOG_DEBUG, String.format("\tVersion %d of repository '%s' (%s) successfully replicated...", version, name, customer));
        }
        finally {
            conn.disconnect();
        }
    }
    
    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
        Long interval = null;
        if (properties != null) {
            Object value = properties.get(KEY_SYNC_INTERVAL);
            if (value != null) {
                try {
                    interval = Long.valueOf(value.toString());
                }catch (NumberFormatException e) {
                    throw new ConfigurationException("interval", "Interval must be a valid Long value", e);
                }
            } else {
                throw new ConfigurationException("interval", "Interval is required");
            }
            
            Dictionary<Object,Object> serviceProps = m_component.getServiceProperties();
            
            serviceProps.put(Constants.REPEAT_FOREVER, true);
            serviceProps.put(Constants.REPEAT_INTERVAL_PERIOD, "millisecond");
            serviceProps.put(Constants.REPEAT_INTERVAL_VALUE, interval);
            
            m_component.setServiceProperties(serviceProps);
        }

    }
}
