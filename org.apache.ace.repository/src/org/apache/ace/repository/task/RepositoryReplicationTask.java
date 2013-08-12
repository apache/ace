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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.RepositoryReplication;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

/**
 * Repository replication task. Uses discovery to find the server it talks to.
 * Subsequently it checks which local repositories are configured and tries to
 * synchronize them with remote copies. Only pulls stuff in, it does not push
 * stuff out.
 */
public class RepositoryReplicationTask implements Runnable {
    private volatile BundleContext m_context;
    private volatile Discovery m_discovery;
    private volatile ConnectionFactory m_connectionFactory;
    private volatile LogService m_log;
    private final Map<ServiceReference, RepositoryReplication> m_replicators = new HashMap<ServiceReference, RepositoryReplication>();
    
    public void add(ServiceReference ref, RepositoryReplication service) {
        synchronized (m_replicators) {
            m_replicators.put(ref,  service);
        }
    }
    
    public void remove(ServiceReference ref) {
        synchronized (m_replicators) {
            m_replicators.remove(ref);
        }
    }
    
    public void run() {
        Entry<ServiceReference,RepositoryReplication>[] replicators;
        synchronized (m_replicators) {
            Set<Entry<ServiceReference,RepositoryReplication>> entries = m_replicators.entrySet();
            replicators = entries.toArray(new Entry[entries.size()]);
        }
        
        try {
            for (Entry<ServiceReference,RepositoryReplication> entry : replicators) {
                replicate(entry);
            }
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_WARNING, "Error while replicating", e);
        }
    }

    private void replicate(Entry<ServiceReference, RepositoryReplication> entry) throws MalformedURLException, IOException {
        ServiceReference ref = entry.getKey();
        RepositoryReplication repository = entry.getValue();
        String filter = "customer=" + ref.getProperty("customer") + "&name=" + ref.getProperty("name");
        URL host = m_discovery.discover();
        URL query = new URL(host, "/replication/query?" + filter);
   
        HttpURLConnection connection = (HttpURLConnection) m_connectionFactory.createConnection(query);
        if (connection.getResponseCode() == HttpServletResponse.SC_OK) {
            SortedRangeSet localRange = repository.getRange();
   
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            try {
                String line = reader.readLine();
                int i = line.lastIndexOf(',');
                if (i > 0) {
                    SortedRangeSet remoteRange = new SortedRangeSet(line.substring(i + 1));
        
                    // check the limit of the repository
                    long limit = repository.getLimit();
                    if (limit == Long.MAX_VALUE) {
                        // no limit, sync all
                        SortedRangeSet delta = localRange.diffDest(remoteRange);
                        RangeIterator iterator = delta.iterator();
                        while (iterator.hasNext()) {
                            long version = iterator.next();
                            URL get = new URL(host, "/replication/get?" + filter + "&version=" + version);
                            HttpURLConnection connection2 = (HttpURLConnection) m_connectionFactory.createConnection(get);
                            repository.put(connection2.getInputStream(), version);
                        }
                    }
                    else {
                        // limit, try to get the the 'limit' newest versions
                        SortedRangeSet union = localRange.union(remoteRange);
                        RangeIterator iterator = union.reverseIterator();
                        while (iterator.hasNext() && limit > 0) {
                            long version = iterator.next();
                            if (!localRange.contains(version)) {
                                URL get = new URL(host, "/replication/get?" + filter + "&version=" + version);
                                HttpURLConnection connection2 = (HttpURLConnection) m_connectionFactory.createConnection(get);
                                repository.put(connection2.getInputStream(), version);
                            }
                            limit--;
                        }
                    }
                }
            }
            catch (Exception e) {
                m_log.log(LogService.LOG_WARNING, "Error parsing remote range", e);
            }
            finally {
                reader.close();
            }
        }
        else {
            m_log.log(LogService.LOG_WARNING, "Could not sync repository for customer: " + ref.getProperty("customer") + ", name: " + ref.getProperty("name") + ", because: " + connection.getResponseMessage() + " (" + connection.getResponseCode() + ")");
        }
    }
}