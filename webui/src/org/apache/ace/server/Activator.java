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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;

public class Activator extends DependencyActivatorBase {
    private static volatile BundleContext m_context;
    private static volatile Activator m_instance;
    private static final boolean DEBUG = true;

    private volatile PackageAdmin m_packageAdmin;
    
    static Activator instance() {
        return m_instance;
    }
    
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
     * Returns a list of exports that consists of everything this bundle imports. We use
     * PackageAdmin to figure this out at runtime.
     */
    static String getExports() {
        StringBuffer imports = new StringBuffer();
        for (Bundle b : m_context.getBundles()) {
            ExportedPackage[] eps = instance().m_packageAdmin.getExportedPackages(b);
            if (eps != null) {
                for (ExportedPackage ep : eps) {
                    Bundle[] ibs = ep.getImportingBundles();
                    if (ibs != null) {
                        for (Bundle ib : ibs) {
                            if (ib.getBundleId() == m_context.getBundle().getBundleId()) {
                                if (imports.length() > 0) {
                                    imports.append(',');
                                }
                                imports.append(ep.getName() + ";version=" + ep.getVersion());
                            }
                        }
                    }
                }
            }
        }
        return imports.toString();
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
            HttpSession session = request.getSession();
            Cookie[] cookies = request.getCookies();
            StringBuffer cc = new StringBuffer();
            for (Cookie c : cookies) {
                if (cc.length() > 0) { cc.append("; "); }
                cc.append(c.getName() + " = " + c.getValue());
            }
            System.out.println("Getting service for request " + request.getRequestURI() + " cookie " + cc + " with session " + session);
            bundleContext = SessionFramework.getFramework(session, Activator.getBaseDir()).getBundleContext();
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
        m_instance = this;

        GWT.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            public void onUncaughtException(Throwable e) {
                ServiceReference logRef = m_context
                    .getServiceReference(LogService.class.getName());
                if (logRef != null) {
                    ((LogService) m_context.getService(logRef)).log(
                        LogService.LOG_ERROR, "Uncaught exception in GWT", e);
                }
                else {
                    System.err.println("Uncaught exception in GWT");
                    e.printStackTrace(System.err);
                }
            }
        });
        
        manager.add(createService()
            .setImplementation(this)
            .add(createServiceDependency().setRequired(true).setService(PackageAdmin.class))
            );
    }
    @Override
    public void destroy(BundleContext context, DependencyManager manager)
    throws Exception {
    }

}
