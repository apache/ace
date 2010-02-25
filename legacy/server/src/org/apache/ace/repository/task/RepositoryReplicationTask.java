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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.repository.RangeIterator;
import org.apache.ace.repository.RepositoryReplication;
import org.apache.ace.repository.SortedRangeSet;
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
    private volatile Discovery m_discovery;
    private volatile LogService m_log;
    private volatile BundleContext m_context;

    public void run() {
        try {
            URL host = m_discovery.discover();
            ServiceReference[] refs = m_context.getServiceReferences(RepositoryReplication.class.getName(), null);
            for (ServiceReference ref : refs) {
                RepositoryReplication repository = (RepositoryReplication) m_context.getService(ref);
                SortedRangeSet localRange = repository.getRange();
                Object customer = ref.getProperty("customer");
                Object name = ref.getProperty("name");
                String filter = "customer=" + customer + "&name=" + name;
                URL query = new URL(host, "/replication/query?" + filter);
                HttpURLConnection connection = (HttpURLConnection) query.openConnection();
                if (connection.getResponseCode() == HttpServletResponse.SC_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    try {
                        String line = reader.readLine();
                        int i = line.lastIndexOf(',');
                        if (i > 0) {
                            SortedRangeSet remoteRange = new SortedRangeSet(line.substring(i + 1));
                            SortedRangeSet delta = localRange.diffDest(remoteRange);
                            RangeIterator iterator = delta.iterator();
                            while (iterator.hasNext()) {
                                long version = iterator.next();
                                URL get = new URL(host, "/replication/get?" + filter + "&version=" + version);
                                HttpURLConnection connection2 = (HttpURLConnection) get.openConnection();
                                repository.put(connection2.getInputStream(), version);
                            }
                        }
                    }
                    catch (Exception e) {
                        m_log.log(LogService.LOG_WARNING, "Error parsing remote range", e);
                    }
                }
                else {
                    m_log.log(LogService.LOG_WARNING, "Could not sync repository for customer " + customer + " name " + name + " because: " + connection.getResponseMessage() + " (" + connection.getResponseCode() + ")");
                }
                m_context.ungetService(ref);
            }
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_WARNING, "Error while replicating", e);
        }
    }
}
