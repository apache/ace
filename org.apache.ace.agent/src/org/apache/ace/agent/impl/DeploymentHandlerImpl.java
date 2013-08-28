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
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.RetryAfterException;
import org.apache.felix.deploymentadmin.DeploymentAdminImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentException;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;
import org.osgi.service.packageadmin.PackageAdmin;

public class DeploymentHandlerImpl extends UpdateHandlerBase implements DeploymentHandler {

    private final DeploymentAdmin m_deploymentAdmin;
    private final boolean m_ownDeploymentAdmin;

    public DeploymentHandlerImpl(BundleContext bundleContext, PackageAdmin packageAdmin) {
        super("deployment");
        m_ownDeploymentAdmin = true;
        m_deploymentAdmin = new DeploymentAdminImpl();
        configureField(m_deploymentAdmin, BundleContext.class, bundleContext);
        configureField(m_deploymentAdmin, PackageAdmin.class, packageAdmin);
        configureField(m_deploymentAdmin, EventAdmin.class, new EventAdminBridge());
        configureField(m_deploymentAdmin, LogService.class, new LogServiceBridge());
    }

    DeploymentHandlerImpl(DeploymentAdmin deploymentAdmin) {
        super("deployment");
        m_ownDeploymentAdmin = false;
        m_deploymentAdmin = deploymentAdmin;
    }

    @Override
    protected void onStart() throws Exception {
        if (m_ownDeploymentAdmin) {
            invokeMethod(m_deploymentAdmin, "start", new Class<?>[] {}, new Object[] {});
        }
    }

    @Override
    protected void onStop() throws Exception {
        if (m_ownDeploymentAdmin) {
            invokeMethod(m_deploymentAdmin, "stop", new Class<?>[] {}, new Object[] {});
        }
    }

    @Override
    public Version getInstalledVersion() {
        Version highestVersion = Version.emptyVersion;
        DeploymentPackage[] installedPackages = m_deploymentAdmin.listDeploymentPackages();
        for (DeploymentPackage installedPackage : installedPackages) {
            if (installedPackage.getName().equals(getIdentification())
                && installedPackage.getVersion().compareTo(highestVersion) > 0) {
                highestVersion = installedPackage.getVersion();
            }
        }
        return highestVersion;
    }

    @Override
    public void deployPackage(InputStream inputStream) throws DeploymentException {
        m_deploymentAdmin.installDeploymentPackage(inputStream);
    }

    @Override
    public long getPackageSize(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getPackageSize(getPackageURL(version, fixPackage));
    };

    @Override
    public InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getInputStream(getPackageURL(version, fixPackage));
    };

    @Override
    public DownloadHandle getDownloadHandle(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getDownloadHandle(getPackageURL(version, fixPackage));
    };

    @Override
    public SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException {
        return getAvailableVersions(getEndpoint(getServerURL(), getIdentification()));
    };

    private URL getPackageURL(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        URL url = getEndpoint(getServerURL(), getIdentification(), fixPackage ? getInstalledVersion() : Version.emptyVersion, version);
        return url;
    }

    private URL getEndpoint(URL serverURL, String identification) {
        try {
            return new URL(serverURL, "deployment/" + identification + "/versions/");
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private URL getEndpoint(URL serverURL, String identification, Version from, Version to) {
        try {
            if (from == null || from.equals(Version.emptyVersion)) {
                return new URL(serverURL, "deployment/" + identification + "/versions/" + to.toString());
            }
            else {
                return new URL(serverURL, "deployment/" + identification + "/versions/" + to.toString() + "?current=" + from);
            }
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void configureField(Object object, Class<?> iface, Object instance) {
        // Note: Does not check super classes!
        Field[] fields = object.getClass().getDeclaredFields();
        AccessibleObject.setAccessible(fields, true);
        for (int j = 0; j < fields.length; j++) {
            if (fields[j].getType().equals(iface)) {
                try {
                    fields[j].set(object, instance);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Coudld not set field " + fields[j].getName() + " on " + object);
                }
            }
        }
    }

    private static Object invokeMethod(Object object, String methodName, Class<?>[] signature, Object[] parameters) {
        // Note: Does not check super classes!
        Class<?> clazz = object.getClass();
        try {
            Method method = clazz.getDeclaredMethod(methodName, signature);
            return method.invoke(object, parameters);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Internal EventAdmin that delegates to actual InternalEvents. Used to inject into the DeploymentAdmin only.
     */
    class EventAdminBridge implements EventAdmin {

        @Override
        public void postEvent(Event event) {
            getEventsHandler().postEvent(event.getTopic(), getPayload(event));
        }

        @Override
        public void sendEvent(Event event) {
            getEventsHandler().postEvent(event.getTopic(), getPayload(event));
        }

        private Map<String, String> getPayload(Event event) {
            Map<String, String> payload = new HashMap<String, String>();
            for (String propertyName : event.getPropertyNames()) {
                payload.put(propertyName, event.getProperty(propertyName).toString());
            }
            return payload;
        }
    }

    /**
     * Internal LogService that wraps delegates to actual InternalLogger. Used to inject into the DeploymentAdmin only.
     */
    class LogServiceBridge implements LogService {

        @Override
        public void log(int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            switch (level) {
                case LogService.LOG_WARNING:
                    logWarning(message, exception);
                    break;
                case LogService.LOG_INFO:
                    logInfo(message, exception);
                    break;
                case LogService.LOG_DEBUG:
                    logDebug(message, exception);
                    break;
                default:
                    logError(message, exception);
                    break;
            }
        }

        @Override
        public void log(ServiceReference sr, int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            log(level, message, exception);
        }
    }

}
