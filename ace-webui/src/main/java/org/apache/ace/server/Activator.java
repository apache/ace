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
import javax.servlet.http.HttpSession;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;

public class Activator extends DependencyActivatorBase {
    private static volatile BundleContext m_context;
    private static volatile Activator m_instance;
    
    private volatile SessionFactory m_sessionFactory;
    private volatile LogService m_log;

    static Activator instance() {
        return m_instance;
    }
    
    static BundleContext getContext() {
        return m_context;
    }
    
    LogService getLog() {
        return m_log;
    }
    
    static void destroySession(String sessionID) {
        m_instance.m_sessionFactory.destroySession(sessionID);
    }
    
    /**
     * Gets a base directory for this bundle's data; you can use this directory 
     * to pass to {@link SessionFramework#getFramework(javax.servlet.http.HttpSession, File)}.
     */
    static File getBaseDir() {
        return m_context.getDataFile("webui");
    }
    
    
    /**
     * Gets an instance of the given service, belonging to the session context.
     */
    static <T> T getService(HttpServletRequest request, Class<T> clazz) throws Exception {
        // lookup the session
        String id;
        if (request == null) {
            System.out.println("!!! Request is null... could not find session service " + clazz.getSimpleName());
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            // create session
            session = request.getSession(true);
            id = session.getId();
            instance().m_sessionFactory.createSession(id);
//            System.out.println("NEW Session " + id +  " requests service " + clazz.getSimpleName());
        }
        else {
            id = session.getId();
//            System.out.println("XST Session " + id +  " requests service " + clazz.getSimpleName());
        }
        
        BundleContext bundleContext = m_context;
        ServiceReference[] refs = bundleContext.getServiceReferences(clazz.getName(), "(" + SessionFactory.SERVICE_SID + "=" + id + ")");
        ServiceReference reference = null;
        if (refs != null && refs.length == 1) {
            reference  = refs[0];
        }
        if (reference != null) {
            @SuppressWarnings("unchecked")
            T result = (T) bundleContext.getService(reference);
//            System.out.println("Returned service for " + clazz.getSimpleName() + " is " + result);
            return result;
        }
        /*
        // we were not able to find the reference immediately, try again for a few seconds...
        ServiceTracker tracker = new ServiceTracker(bundleContext, "&((" + Constants.OBJECTCLASS + "=" + clazz.getName() + ")(" + SessionFactory.SERVICE_SID + "=" + id + "))", null);
        tracker.open();
        @SuppressWarnings("unchecked")
        T result = (T) tracker.waitForService(5000);
        if (result == null) {
            ServiceReference logRef = bundleContext.getServiceReference(LogService.class.getName());
            if (logRef != null) {
                ((LogService) bundleContext.getService(logRef)).log(LogService.LOG_ERROR, "Error finding service " + clazz.getName());
            }
        }
        System.out.println("After a little while, the returned service for " + clazz.getSimpleName() + " is " + result);
        return result;
         */
        System.out.println("!!! Could not find session service " + clazz.getSimpleName());
        return null;
    }
    
    public static <T> T getService(Class<T> clazz) {
        BundleContext bundleContext = m_context;
        ServiceReference reference = bundleContext.getServiceReference(clazz.getName());
        if (reference != null) {
            @SuppressWarnings("unchecked")
            T result = (T) bundleContext.getService(reference);
            System.out.println("Returned service for " + clazz.getSimpleName() + " is " + result);
            return result;
        }
        System.out.println("!!! Could not find service " + clazz.getSimpleName());
        return null;
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
        
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency().setRequired(true).setService(SessionFactory.class))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }
    @Override
    public void destroy(BundleContext context, DependencyManager manager)
    throws Exception {
    }


}
