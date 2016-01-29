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
package org.apache.ace.log.listener;

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.ace.log.Log;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Activator for the bundle that listens to all life-cycle events, and logs them to the log service. The BundleEvents,
 * FrameworkEvents and the events related to Deployment Packages are relevant for the audit log.
 * <p>
 * Furthermore this bundle takes care of the situation when the real log is not yet available within the framework, by
 * using a cache that temporarily stores the log entries, and flushing those when the real log service comes up.
 * BundleEvents and Framework events are always available, but events related to Deployment Packages will only be
 * available when the EventAdmin is present.
 */
public class Activator implements BundleActivator {
    private static final String LOG_NAME = "auditlog";

    private final static String[] topics = new String[] { "org/osgi/service/deployment/*", "org/apache/ace/deployment/*" };
    private ServiceTracker<Log, Log> m_logTracker;
    private ListenerImpl m_listener;

    public void start(BundleContext context) throws Exception {
        LogProxy logProxy = new LogProxy();
        m_listener = new ListenerImpl(context, logProxy);
        m_listener.startInternal();
        // listen for bundle and framework events
        context.addBundleListener(m_listener);
        context.addFrameworkListener(m_listener);

        // listen for deployment events
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put(EventConstants.EVENT_TOPIC, topics);
        context.registerService(EventHandler.class.getName(), m_listener, dict);

        // keep track of when the real log is available
        LogTracker logTrackerCust = new LogTracker(context, logProxy);
        m_logTracker = new ServiceTracker<>(context, context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Log.class.getName() + ")(name=" + LOG_NAME + "))"), logTrackerCust);
        m_logTracker.open();
    }

    public void stop(BundleContext context) throws Exception {
        // cleanup
        m_logTracker.close();
        context.removeFrameworkListener(m_listener);
        context.removeBundleListener(m_listener);
        m_listener.stopInternal();
    }
}
