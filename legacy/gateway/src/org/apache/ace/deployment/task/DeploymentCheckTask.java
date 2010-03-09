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
import java.net.URL;
import java.util.Dictionary;
import java.util.Properties;

import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.log.LogService;

/**
 * Task that checks for a new version and sends out an event if there is a new version. It does not actually
 * download or install it.
 */
public class DeploymentCheckTask extends DeploymentTaskBase implements Runnable {
    private static final String TOPIC_UPDATE_AVAILABLE = "org/apache/ace/deployment/UPDATEAVAILABLE";

    /**
     * When run a check is made if a higher version is available on the remote. If so, send out an event.
     */
    public void run() {
        try {
            String gatewayID = m_identification.getID();
            URL host = m_discovery.discover();

            Version highestLocalVersion = getHighestLocalVersion();

            if (host == null) {
                //expected if there's no discovered
                //ps or relay server
                m_log.log(LogService.LOG_INFO, "Highest remote: unknown / Highest local: " + highestLocalVersion);
                return;
            }

            URL url = new URL(host, "deployment/" + gatewayID + "/versions/");
            Version highestRemoteVersion = getHighestRemoteVersion(url);
            m_log.log(LogService.LOG_INFO, "Highest remote: " + highestRemoteVersion + " / Highest local: " + highestLocalVersion);
            if ((highestRemoteVersion != null) && ((highestLocalVersion == null) || (highestRemoteVersion.compareTo(highestLocalVersion) > 0))) {
                Properties properties = new Properties();
                properties.put("deploymentpackage.localversion", ((highestLocalVersion == null) ? Version.emptyVersion : highestLocalVersion));
                properties.put("deploymentpackage.remoteversion", highestRemoteVersion);
                m_eventAdmin.postEvent(new Event(TOPIC_UPDATE_AVAILABLE, (Dictionary) properties));
            }
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Error creating endpoint url",e );
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Error checking for update", e);
        }
    }
}
