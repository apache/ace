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
package org.apache.ace.test.bundlestop;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

public class SystemBundleStopper implements EventHandler, BundleListener {

    private BundleContext m_context;
    private boolean m_deploymentPackageIsInstalled = false;
    private boolean m_isFrameWorkStarted = false;

    /**
     * This service only listens for completed events. If it gets one,
     * the systembundle should be stopped.
     */
    public synchronized void handleEvent(Event arg0) {
        if (m_isFrameWorkStarted || (m_context.getBundle(0).getState() == Bundle.ACTIVE)) {
            stopSystemBundle();
        } else {
            m_deploymentPackageIsInstalled = true;
        }
    }

    public synchronized void bundleChanged(BundleEvent event) {
        if ((event.getBundle().getBundleId() == 0) && (event.getType() == BundleEvent.STARTED)) {
            if (m_deploymentPackageIsInstalled) {
                stopSystemBundle();
            } else {
                m_isFrameWorkStarted  = true;
            }
        }
    }

    public void stopSystemBundle() {
        try {
            m_context.getBundle(0).stop();
        }
        catch (BundleException e) {
            System.err.println("Error stopping systembundle. Performing an un-clean exit now.");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
