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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.osgi.framework.Version;
import org.osgi.service.log.LogService;

/**
 * Implementation of the <code>Updater</code> interface that updates software configurations by using the
 * <code>DeploymentService</code> to determine the current local version and to actually install new versions.
 */
public class DeploymentUpdateTask extends DeploymentTaskBase implements Runnable {
    /**
     * When run a check is made if a higher version is available on the remote. If so, an attempt is made to install
     * this new version.
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
                // no local version or local version lower than remote, install the update
                installVersion(url, highestRemoteVersion, highestLocalVersion);
            }
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Error creating endpoint url", e);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Error accessing resources", e);
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Error installing update", e);
        }
    }
}
