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
package org.apache.ace.agent.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.ace.agent.AgentControl;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.log.AuditEvent;
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
 * Service component that listens for
 * 
 */
// TODO quick copy & paste & simplify from org.apache.ace.log.listener.*
// TODO Which event types to log must be configurable
// TODO split into separate listeners
public class EventLoggerImpl implements BundleListener, FrameworkListener, EventHandler {

    /*
     * FIXME This is a simplified quick copy and paste of org.apache.ace.log.listener.* without caching and async. I
     * think that is OK. However we need to revisit all logging/monitoring and this logic should probably be made
     * configurable split up is separate components.
     * 
     * @see EvenLoggerFactory as well
     */

    public static final String EVENTLOGGER_FEEDBACKCHANNEL = "auditlog";

    public static final String[] TOPICS_INTEREST = new String[] { "org/osgi/service/deployment/*", "org/apache/ace/deployment/*" };

    public static final String TOPIC_INSTALL = "org/osgi/service/deployment/INSTALL";
    public static final String TOPIC_UNINSTALL = "org/osgi/service/deployment/UNINSTALL";
    public static final String TOPIC_COMPLETE = "org/osgi/service/deployment/COMPLETE";
    public static final String TOPIC_DEPLOYMENTPACKAGE_INSTALL = "org/apache/ace/deployment/INSTALL";

    private final BundleContext m_bundleContext;
    private final AgentControl m_agentControl;

    public EventLoggerImpl(AgentControl agentControl, BundleContext bundleContext) {
        m_agentControl = agentControl;
        m_bundleContext = bundleContext;
    }

    @Override
    public void handleEvent(Event event) {
        int eventType = AuditEvent.DEPLOYMENTADMIN_BASE;
        Map<String, String> props = new HashMap<String, String>();

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
            ServiceReference ref = m_bundleContext.getServiceReference(DeploymentAdmin.class.getName());
            if (ref != null) {
                DeploymentAdmin deplAdmin = (DeploymentAdmin) m_bundleContext.getService(ref);
                if (deplAdmin != null) {
                    DeploymentPackage dp = deplAdmin.getDeploymentPackage(deplPackName);
                    if (dp != null) {
                        Version version = dp.getVersion();
                        if (version != null) {
                            props.put(AuditEvent.KEY_VERSION, version.toString());
                        }
                    }
                    // after use, release the service as is it not needed anymore
                    m_bundleContext.ungetService(ref);
                }
            }
            eventType = AuditEvent.DEPLOYMENTADMIN_COMPLETE;
            props.put(AuditEvent.KEY_NAME, deplPackName);
            props.put(AuditEvent.KEY_SUCCESS, (String) event.getProperty("successful"));
        }
        writeEvent(eventType, props);
    }

    @Override
    public void frameworkEvent(FrameworkEvent event) {
        int eventType = AuditEvent.FRAMEWORK_BASE;
        Map<String, String> props = new HashMap<String, String>();
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
        writeEvent(eventType, props);
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        int eventType = AuditEvent.BUNDLE_BASE;
        Map<String, String> props = new HashMap<String, String>();
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
        writeEvent(eventType, props);
    }

    private void writeEvent(int eventType, Map<String, String> payload) {
        try {
            FeedbackChannel channel = m_agentControl.getFeedbackHandler()
                .getChannel(EVENTLOGGER_FEEDBACKCHANNEL);
            if (channel != null) {
                channel.write(eventType, payload);
            }
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
