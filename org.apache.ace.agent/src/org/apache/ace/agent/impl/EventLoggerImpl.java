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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ace.agent.EventListener;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.log.AuditEvent;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;

/**
 * Service component that listens for
 * 
 */
public class EventLoggerImpl extends ComponentBase implements BundleListener, FrameworkListener, EventListener {

    public static final String EVENTLOGGER_FEEDBACKCHANNEL = "auditlog";
    public static final String TOPIC_INSTALL = "org/osgi/service/deployment/INSTALL";
    public static final String TOPIC_UNINSTALL = "org/osgi/service/deployment/UNINSTALL";
    public static final String TOPIC_COMPLETE = "org/osgi/service/deployment/COMPLETE";

    private final BundleContext m_bundleContext;
    private final AtomicBoolean m_isStarted;

    public EventLoggerImpl(BundleContext bundleContext) {
        super("auditlogger");

        m_bundleContext = bundleContext;
        m_isStarted = new AtomicBoolean(false);
    }

    @Override
    protected void onInit() throws Exception {
        getEventsHandler().addListener(this);
    }

    @Override
    protected void onStart() throws Exception {
        if (m_isStarted.compareAndSet(false, true)) {
            m_bundleContext.addBundleListener(this);
            m_bundleContext.addFrameworkListener(this);
        }
    }

    @Override
    protected void onStop() throws Exception {
        if (m_isStarted.compareAndSet(true, false)) {
            getEventsHandler().removeListener(this);

            m_bundleContext.removeBundleListener(this);
            m_bundleContext.removeFrameworkListener(this);
        }
    }

    @Override
    public void handle(String topic, Map<String, String> payload) {
        if (!m_isStarted.get()) {
            return;
        }

        int eventType = AuditEvent.DEPLOYMENTADMIN_BASE;
        Map<String, String> props = new HashMap<String, String>();

        if (TOPIC_INSTALL.equals(topic)) {
            String deplPackName = payload.get("deploymentpackage.name");
            eventType = AuditEvent.DEPLOYMENTADMIN_INSTALL;
            props.put(AuditEvent.KEY_NAME, deplPackName);
        }
        else if (TOPIC_UNINSTALL.equals(topic)) {
            String deplPackName = payload.get("deploymentpackage.name");
            eventType = AuditEvent.DEPLOYMENTADMIN_UNINSTALL;
            props.put(AuditEvent.KEY_NAME, deplPackName);
        }
        else if (TOPIC_COMPLETE.equals(topic)) {
            eventType = AuditEvent.DEPLOYMENTADMIN_COMPLETE;
            props.put(AuditEvent.KEY_NAME, payload.get("deploymentpackage.name"));
            props.put(AuditEvent.KEY_VERSION, getDeploymentHandler().getInstalledVersion().toString());
            props.put(AuditEvent.KEY_SUCCESS, payload.get("successful"));
        }
        writeAuditEvent(eventType, props);
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        if (!m_isStarted.get()) {
            return;
        }

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
        writeAuditEvent(eventType, props);
    }

    @Override
    public void frameworkEvent(FrameworkEvent event) {
        if (!m_isStarted.get()) {
            return;
        }
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
        writeAuditEvent(eventType, props);
    }

    private void writeAuditEvent(int eventType, Map<String, String> payload) {
        try {
            FeedbackChannel channel = getFeedbackHandler().getChannel(EVENTLOGGER_FEEDBACKCHANNEL);
            if (channel != null) {
                channel.write(eventType, payload);
            }
            else {
//                logDebug("Feedback event *not* written as no channel is available!");
            }
        }
        catch (IOException e) {
            logWarning("Failed to write feedback event!", e);
        }
    }
}
