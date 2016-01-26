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

import org.apache.ace.log.Log;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Keep track of whether the log is available. If available, use the real log, else use the cache version. When the real
 * log becomes available, flush all events from the cache to the real log.
 *
 */
public class LogTracker implements ServiceTrackerCustomizer<Log, Log> {
    private BundleContext m_context;
    private LogProxy m_proxy;

    public LogTracker(BundleContext context, LogProxy proxy) {
        m_context = context;
        m_proxy = proxy;
    }

    /**
     * Called when the log service has been added. As result, the real log service will be used instead of the cache.
     */
    @Override
    public Log addingService(ServiceReference<Log> ref) {
        // get the service based upon the reference, and return it
        // make sure the real Log will be used, and all events in the
        // cache are being flushed to the real Log.
        Log externalLog = m_context.getService(ref);
        m_proxy.setLog(externalLog);
        return externalLog;
    }

    /**
     * Called when the Log service is not available anymore. As result, the cache version of the Log will be used until
     * the Log service is added again.
     */
    @Override
    public void removedService(ServiceReference<Log> ref, Log log) {
        // make sure the LogCache is used instead of the real Log
        m_proxy.setLog(null);
        // unget the service again
        m_context.ungetService(ref);
    }

    @Override
    public void modifiedService(ServiceReference<Log> ref, Log log) {
        // do nothing
    }
}
