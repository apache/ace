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
package org.apache.ace.server;

import java.io.File;

import javax.servlet.http.HttpServletRequest;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

public class Activator extends DependencyActivatorBase {
    private static volatile BundleContext m_context;
    
    private static final boolean DEBUG = false;

    static BundleContext getContext() {
        return m_context;
    }
    
    /**
     * Gets a base directory for this bundle's data; you can use this directory 
     * to pass to {@link SessionFramework#getFramework(javax.servlet.http.HttpSession, File)}.
     */
    static File getBaseDir() {
        return m_context.getDataFile("webui");
    }
    
    /**
     * Gets an instance of the given service, coming from the session-dependent
     * framework. For debug purposes, it can return the service from the 'outer' framework.
     * (set the {@link #DEBUG} flag to <code>true</code> to use this feature)
     */
    static <T> T getService(HttpServletRequest request, Class<T> clazz) throws Exception {
        BundleContext bundleContext;
        if (DEBUG) {
            bundleContext = m_context;
        }
        else {
            bundleContext = SessionFramework.getFramework(request.getSession(), Activator.getBaseDir()).getBundleContext();
        }
        
        ServiceReference reference = bundleContext.getServiceReference(clazz.getName());
        if (reference != null) {
            @SuppressWarnings("unchecked")
            T result = (T) bundleContext.getService(reference);
            return result;
        }
        
        // we were not able to find the reference immediately, try again for a few seconds...
        ServiceTracker tracker = new ServiceTracker(bundleContext, "(" + Constants.OBJECTCLASS + "=" + clazz.getName() + ")", null);
        tracker.open();
        @SuppressWarnings("unchecked")
        T result = (T) tracker.waitForService(5000);
        if (result == null) {
            ServiceReference logRef = bundleContext.getServiceReference(LogService.class.getName());
            if (logRef != null) {
                ((LogService) bundleContext.getService(logRef)).log(LogService.LOG_ERROR, "Error finding service " + clazz.getName());
            }
        }
        return result;
    }

    @Override
    public void init(BundleContext context, DependencyManager manager)
    throws Exception {
        m_context = context;
    }
    @Override
    public void destroy(BundleContext context, DependencyManager manager)
    throws Exception {
    }

}
