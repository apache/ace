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
package org.apache.ace.http.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;

/**
 * Service responsible for registrating HttpServlets at HttpServices.<p>
 *
 * When a HttpServlet is being added or removed, the callback methods in this class are being
 * called via the DependencyManager. A Servlet is being added to all HttpServices currently
 * available or removed from all available HttpServices.<p>
 *
 * In case a HttpService is being added or removed, other callback methods are being called
 * via the DependencyManager. When a HttpService is added, all previously registered Servlets
 * are being registered to this new HttpService. In case of removal, all Servlet endpoints are
 * being removed from the HttpService that is going to be removed.<p>
 */
public class Activator extends DependencyActivatorBase {

    private volatile LogService m_log; // injected
    private final Set<ServiceReference> m_httpServices = new HashSet<ServiceReference>();
    private final Map<ServiceReference, String> m_servlets = new HashMap<ServiceReference, String>();
    private BundleContext m_context;

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        m_context = context;
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false))
            .add(createServiceDependency()
                .setService(HttpService.class)
                .setAutoConfig(false)
                .setCallbacks("addHttpService", "removeHttpService"))
            .add(createServiceDependency()
                .setService(HttpServlet.class)
                .setAutoConfig(false)
                .setCallbacks("addHttpServlet", "changeHttpServlet", "removeHttpServlet")));
    }

    /**
     * Callback method used in case a HttpServlet is being added. This Servlet is being added
     * to all available HttpServices under the endpoint configured via the Configurator.
     *
     * @param ref  reference to the Servlet
     */
    public synchronized void addHttpServlet(ServiceReference ref) {
        // register servlet to all HttpServices
        String endpoint = (String)ref.getProperty(HttpConstants.ENDPOINT);
        m_servlets.put(ref, endpoint);
        Servlet servlet = (Servlet)m_context.getService(ref);
        for (ServiceReference reference : m_httpServices) {
            HttpService httpService = (HttpService) m_context.getService(reference);
            try {
                if ((httpService != null) && (endpoint != null) && (servlet != null)) {
                    httpService.registerServlet(endpoint, servlet, null, null);
                }
                else {
                    m_log.log(LogService.LOG_WARNING, "Unable to register servlet with endpoint '" + endpoint + "'");
                }
            }
            catch (Exception e) {
                m_log.log(LogService.LOG_WARNING, "Already registered under existing endpoint", e);
            }
        }
    }

    public synchronized void changeHttpServlet(ServiceReference ref) {
        removeServlet(ref, m_servlets.get(ref));
        addHttpServlet(ref);
    }

    /**
     * Callback method used in case a HttpServlet is being removed. This Servlet is being removed
     * from all available HttpServices using the endpoint configured via the Configurator.
     *
     * @param ref  reference to the Servlet
     */
    public synchronized void removeHttpServlet(ServiceReference ref) {
        // remove servlet from all HttpServices
        String endpoint = (String)ref.getProperty(HttpConstants.ENDPOINT);
        removeServlet(ref, endpoint);
    }

    private void removeServlet(ServiceReference ref, String endpoint) {
        m_servlets.remove(ref);
        for (ServiceReference reference : m_httpServices) {
            HttpService httpService = (HttpService) m_context.getService(reference);
            if ((httpService != null) && (endpoint != null)) {
                try {
                    httpService.unregister(endpoint);
                }
                catch (Exception e) {
                    m_log.log(LogService.LOG_WARNING, "Servlet cannot be unregistered, maybe not registered under this endpoint", e);
                }
            }
        }
    }

    /**
     * Callback method used in case a HttpService is being added. To this Service all previously
     * registered Servlet are added under the endpoints of the Servlets (which are configured
     * via the Configurator).
     *
     * @param ref  reference to the Service
     */
    public synchronized void addHttpService(ServiceReference ref, HttpService httpService) {
        m_httpServices.add(ref);
        // register all servlets to this new HttpService
        for (ServiceReference reference : m_servlets.keySet()) {
            Servlet servlet = (Servlet)m_context.getService(reference);
            String endpoint = (String)reference.getProperty(HttpConstants.ENDPOINT);
            if ((servlet != null) && (endpoint != null)) {
                try {
                    httpService.registerServlet(endpoint, servlet, null, null);
                }
                catch (Exception e) {
                    m_log.log(LogService.LOG_WARNING, "Already registered under existing endpoint", e);
                }
            }
        }
    }

    /**
     * Callback method used in case a HttpService is being removed. From this Service all previously
     * registered Servlet are removed using the endpoints of the Servlets (which are configured
     * via the Configurator).
     *
     * @param ref  reference to the Service
     */
    public synchronized void removeHttpService(ServiceReference ref, HttpService httpService) {
        m_httpServices.remove(ref);
        // remove references from the unregistered HttpService
        unregisterEndpoints(httpService);
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager arg1) throws Exception {
        for (ServiceReference httpRef : m_httpServices) {
            HttpService httpService = (HttpService)m_context.getService(httpRef);
            if (httpService != null) {
                unregisterEndpoints(httpService);
            }
        }
        m_httpServices.clear();
        m_servlets.clear();
        m_context = null;
    }

    /**
     * Unregisters all Servlets (via their endpoints) from the HttpService being passed to
     * this method.
     *
     * @param httpService  the HttpService that is being unregistered
     */
    private void unregisterEndpoints(HttpService httpService) {
        for (ServiceReference reference : m_servlets.keySet()) {
            String endpoint = (String)reference.getProperty(HttpConstants.ENDPOINT);
            if (endpoint != null) {
                try {
                    httpService.unregister(endpoint);
                }
                catch (Exception e) {
                    m_log.log(LogService.LOG_WARNING, "Servlet cannot be unregistered, maybe not registered under this endpoint", e);
                }
            }
        }
    }
}
