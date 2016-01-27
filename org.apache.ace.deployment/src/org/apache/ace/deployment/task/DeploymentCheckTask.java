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
package org.apache.ace.deployment.task;

import java.net.MalformedURLException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.deployment.service.DeploymentService;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

/**
 * Task that checks for a new version and sends out an event if there is a new version. It does not actually
 * download or install it.
 */
public class DeploymentCheckTask implements Runnable {

    private static final String TOPIC_UPDATE_AVAILABLE = "org/apache/ace/deployment/UPDATEAVAILABLE";

    private volatile LogService m_log;
    private volatile EventAdmin m_eventAdmin;
    private volatile DeploymentService m_service;

    /**
     * When run a check is made if a higher version is available on the remote. If so, send out an event.
     */
    public void run() {
        try {
            Version localVersion = m_service.getHighestLocalVersion();
            Version remoteVersion = m_service.getHighestRemoteVersion();

            if (remoteVersion == null) {
                // expected if there's no discovered ps or relay server
                // ACE-220: lower log level; not of real interest...
                m_log.log(LogService.LOG_DEBUG, "Highest remote: unknown / Highest local: " + localVersion);
                return;
            }

            // ACE-220: lower log level; not of real interest...
            m_log.log(LogService.LOG_DEBUG, "Highest remote: " + remoteVersion + " / Highest local: " + localVersion);

            if ((remoteVersion != null) && ((localVersion == null) || (remoteVersion.compareTo(localVersion) > 0))) {
                m_eventAdmin.postEvent(createEvent(localVersion, remoteVersion));
            }
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Error creating endpoint url", e);
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Error checking for update", e);
        }
    }

    /**
     * Creates an event for notifying listeners that a new version can be installed.
     * 
     * @param localVersion the highest local version;
     * @param remoteVersion the higest remote version.
     * @return a new {@link Event} instance, never <code>null</code>.
     */
    private Event createEvent(Version localVersion, Version remoteVersion) {
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put("deploymentpackage.localversion", ((localVersion == null) ? Version.emptyVersion : localVersion));
        properties.put("deploymentpackage.remoteversion", remoteVersion);
        return new Event(TOPIC_UPDATE_AVAILABLE, properties);
    }
}