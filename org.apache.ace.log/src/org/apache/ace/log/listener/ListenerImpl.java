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

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.log.Log;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

/**
 * This class implements all listening actions to be done. It listens for BundleEvents, FrameworkEvents and events related to
 * Deployment Packages. Whenever an event is received, it is transformed as defined in AuditEvent, and consequently logged in
 * the AuditLog.
 */
public class ListenerImpl implements BundleListener, FrameworkListener, EventHandler {

    private final String TOPIC_INSTALL = "org/osgi/service/deployment/INSTALL";
    private final String TOPIC_UNINSTALL = "org/osgi/service/deployment/UNINSTALL";
    private final String TOPIC_COMPLETE = "org/osgi/service/deployment/COMPLETE";

    private final String TOPIC_DEPLOYMENTPACKAGE_INSTALL = "org/apache/ace/deployment/INSTALL";

    volatile BundleContext m_context;
    volatile Log m_auditLog;

    private final List<Runnable> m_queue = new ArrayList<>();

    public ListenerImpl(BundleContext context, Log log) {
        m_context = context;
        m_auditLog = log;
    }

    /**
     * Whenever a BundleEvent is received, an event is logged on the AuditLog. The event details logged are first transformed as
     * defined in AuditEvent before actually being logged.
     */
    public void bundleChanged(final BundleEvent event) {
        synchronized (m_queue) {
            m_queue.add(new Runnable() {
                public void run() {
                    int eventType = AuditEvent.BUNDLE_BASE;
                    Properties props = new Properties();
                    Bundle bundle = event.getBundle();
                    props.put(AuditEvent.KEY_ID, Long.toString(bundle.getBundleId()));

                    switch (event.getType()) {
                        case BundleEvent.INSTALLED:
                            eventType = AuditEvent.BUNDLE_INSTALLED;
                            if (bundle.getSymbolicName() != null) {
                                props.put(AuditEvent.KEY_NAME, bundle.getSymbolicName());
                            }
                            String version = (String) bundle.getHeaders().get(Constants.BUNDLE_VERSION);
                            if (version != null) {
                                props.put(AuditEvent.KEY_VERSION, version);
                            }
                            props.put(AuditEvent.KEY_LOCATION, bundle.getLocation());
                            break;
                        case BundleEvent.RESOLVED:
                            eventType = AuditEvent.BUNDLE_RESOLVED;
                            break;
                        case BundleEvent.STARTED:
                            eventType = AuditEvent.BUNDLE_STARTED;
                            break;
                        case BundleEvent.STOPPED:
                            eventType = AuditEvent.BUNDLE_STOPPED;
                            break;
                        case BundleEvent.UNRESOLVED:
                            eventType = AuditEvent.BUNDLE_UNRESOLVED;
                            break;
                        case BundleEvent.UPDATED:
                            eventType = AuditEvent.BUNDLE_UPDATED;
                            version = (String) bundle.getHeaders().get(Constants.BUNDLE_VERSION);
                            if (version != null) {
                                props.put(AuditEvent.KEY_VERSION, version);
                            }
                            props.put(AuditEvent.KEY_LOCATION, bundle.getLocation());
                            break;
                        case BundleEvent.UNINSTALLED:
                            eventType = AuditEvent.BUNDLE_UNINSTALLED;
                            break;
                        case BundleEvent.STARTING:
                            eventType = AuditEvent.BUNDLE_STARTING;
                            break;
                        case BundleEvent.STOPPING:
                            eventType = AuditEvent.BUNDLE_STOPPING;
                            break;
                    }
                    m_auditLog.log(eventType, props);
                }
            });
            m_queue.notifyAll();
        }
    }

    /**
     * Whenever a FrameworkEvent is received, an event is logged on the AuditLog. The event details logged are first transformed
     * as defined in AuditEvent before actually being logged.
     */
    public void frameworkEvent(final FrameworkEvent event) {
        synchronized (m_queue) {
            m_queue.add(new Runnable() {
                public void run() {
                    int eventType = AuditEvent.FRAMEWORK_BASE;
                    Properties props = new Properties();
                    Bundle bundle = event.getBundle();

                    if (bundle != null) {
                        props.put(AuditEvent.KEY_ID, Long.toString(bundle.getBundleId()));
                    }

                    String msg = null;
                    String type = null;
                    Throwable exception = event.getThrowable();
                    if (exception != null) {
                        msg = exception.getMessage();
                        type = exception.getClass().getName();
                    }

                    switch (event.getType()) {
                        case FrameworkEvent.INFO:
                            eventType = AuditEvent.FRAMEWORK_INFO;
                            if (msg != null) {
                                props.put(AuditEvent.KEY_MSG, msg);
                            }
                            if (type != null) {
                                props.put(AuditEvent.KEY_TYPE, type);
                            }
                            break;
                        case FrameworkEvent.WARNING:
                            eventType = AuditEvent.FRAMEWORK_WARNING;
                            if (msg != null) {
                                props.put(AuditEvent.KEY_MSG, msg);
                            }
                            if (type != null) {
                                props.put(AuditEvent.KEY_TYPE, type);
                            }
                            break;
                        case FrameworkEvent.ERROR:
                            eventType = AuditEvent.FRAMEWORK_ERROR;
                            if (msg != null) {
                                props.put(AuditEvent.KEY_MSG, msg);
                            }
                            if (type != null) {
                                props.put(AuditEvent.KEY_TYPE, type);
                            }
                            break;
                        case FrameworkEvent.PACKAGES_REFRESHED:
                            eventType = AuditEvent.FRAMEWORK_REFRESH;
                            break;
                        case FrameworkEvent.STARTED:
                            eventType = AuditEvent.FRAMEWORK_STARTED;
                            break;
                        case FrameworkEvent.STARTLEVEL_CHANGED:
                            eventType = AuditEvent.FRAMEWORK_STARTLEVEL;
                            break;
                    }
                    m_auditLog.log(eventType, props);
                }
            });
            m_queue.notifyAll();
        }
    }

    /**
     * Only expects events related to Deployment Packages. Whenever an event is received, the event is logged on the AuditLog.
     * The event details logged are first transformed as defined in AuditEvent before actually being logged.
     */
    public void handleEvent(final Event event) {
        synchronized (m_queue) {
            m_queue.add(new Runnable() {
                public void run() {
                    int eventType = AuditEvent.DEPLOYMENTADMIN_BASE;
                    Dictionary<String, Object> props = new Hashtable<>();

                    String topic = event.getTopic();

                    if (topic.equals(TOPIC_DEPLOYMENTPACKAGE_INSTALL)) {
                        String url = (String) event.getProperty("deploymentpackage.url");
                        String version = (String) event.getProperty("deploymentpackage.version");

                        eventType = AuditEvent.DEPLOYMENTCONTROL_INSTALL;
                        props.put(AuditEvent.KEY_VERSION, version);
                        props.put(AuditEvent.KEY_NAME, url);
                    }
                    else if (topic.equals(TOPIC_INSTALL)) {
                        String deplPackName = (String) event.getProperty("deploymentpackage.name");
                        eventType = AuditEvent.DEPLOYMENTADMIN_INSTALL;
                        props.put(AuditEvent.KEY_NAME, deplPackName);
                    }

                    else if (topic.equals(TOPIC_UNINSTALL)) {
                        String deplPackName = (String) event.getProperty("deploymentpackage.name");
                        eventType = AuditEvent.DEPLOYMENTADMIN_UNINSTALL;
                        props.put(AuditEvent.KEY_NAME, deplPackName);
                    }
                    else if (topic.equals(TOPIC_COMPLETE)) {
                        String deplPackName = (String) event.getProperty("deploymentpackage.name");

                        // to retrieve the version, DeploymentAdmin has to be used
                        ServiceReference<DeploymentAdmin> ref = m_context.getServiceReference(DeploymentAdmin.class);
                        if (ref != null) {
                            DeploymentAdmin deplAdmin = m_context.getService(ref);
                            if (deplAdmin != null) {
                                DeploymentPackage dp = deplAdmin.getDeploymentPackage(deplPackName);
                                if (dp != null) {
                                    Version version = dp.getVersion();
                                    if (version != null) {
                                        props.put(AuditEvent.KEY_VERSION, version.toString());
                                    }
                                }
                                // after use, release the service as is it not needed anymore
                                m_context.ungetService(ref);
                            }
                        }

                        eventType = AuditEvent.DEPLOYMENTADMIN_COMPLETE;
                        props.put(AuditEvent.KEY_NAME, deplPackName);
                        Boolean success = (Boolean) event.getProperty("successful");
                        props.put(AuditEvent.KEY_SUCCESS, success.toString());
                    }

                    m_auditLog.log(eventType, props);
                }
            });
            m_queue.notifyAll();
        }
    }

    synchronized void startInternal() {
        initInternal();
        if (!m_thread.isAlive()) {
            m_thread.start();
        }
    }

    synchronized void stopInternal() {
        if (m_thread != null) {
            m_thread.interrupt();
            try {
                m_thread.join();
            }
            catch (InterruptedException e) {
                // Not much we can do
            }
            m_thread = null;
        }
    }

    private Thread m_thread = null;

    synchronized void initInternal() {
        if ((m_thread == null) || (!m_thread.isAlive())) {
            m_thread = new Thread("AuditLogListenerThread") {
                public void run() {

                    Runnable next = null;
                    do {
                        synchronized (m_queue) {
                            while (m_queue.isEmpty() && !isInterrupted()) {
                                try {
                                    m_queue.wait();
                                }
                                catch (InterruptedException ex) {
                                    interrupt();
                                }
                            }
                            if (!m_queue.isEmpty()) {
                                next = (Runnable) m_queue.remove(0);
                            }
                            else {
                                next = null;
                            }
                        }
                        if (next != null) {
                            try {
                                next.run();
                            }
                            catch (Exception ex) {
                                // Not much we can do
                                // FIXME:
                                ex.printStackTrace(System.err);
                            }
                        }
                    }
                    while (next != null);
                }
            };
            m_thread.setDaemon(true);
        }
    }
}