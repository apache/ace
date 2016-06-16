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

import static org.apache.ace.agent.impl.ReflectionUtil.configureField;
import static org.apache.ace.agent.impl.ReflectionUtil.invokeMethod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import org.apache.ace.agent.DeploymentHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.InstallationFailedException;
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

@SuppressWarnings({"restriction","deprecation"})
public class DeploymentHandlerImpl extends UpdateHandlerBase implements DeploymentHandler {

    /**
     * Internal EventAdmin that delegates to actual InternalEvents. Used to inject into the DeploymentAdmin only. If it
     * can find an EventAdmin service in the framework, it will also send events to that service. It does so without
     * ever trying to import any API, because of two reasons:
     * <ol>
     * <li>We want to isolate the management agent as well as possible from the rest of the framework.</li>
     * <li>We have an internal copy of the EventAdmin API, which means we cannot be exposed to another version anyway.</li>
     * </ol>
     */
    final class EventAdminBridge implements EventAdmin {
        private final BundleContext m_context;

        public EventAdminBridge(BundleContext context) {
            m_context = context;
        }

        @Override
        public void postEvent(Event event) {
            getEventsHandler().postEvent(event.getTopic(), getPayload(event));
            invokeExternalEventAdmin("postEvent", event);
        }

        @Override
        public void sendEvent(Event event) {
            getEventsHandler().sendEvent(event.getTopic(), getPayload(event));
            invokeExternalEventAdmin("sendEvent", event);
        }

        /**
         * Bridges events from out local event-handling methods to the first EventAdmin service. As we do not have a
         * dependency on the (external!) EventAdmin API we cannot always call like we normally would do for
         * OSGi-services. Instead, we need to do some advanced reflection trickery in order to call an EventAdmin.
         */
        private void invokeExternalEventAdmin(String method, Event event) {
            try {
                // try to find an EventAdmin service
                ServiceReference<?>[] refs = m_context.getAllServiceReferences(EventAdmin.class.getName(), null);
                if (refs != null && refs.length > 0) {
                    // if we've found one (or more) we pick the first match
                    Object svc = m_context.getService(refs[0]);
                    if (svc != null) {
                        try {
                            // if the service is still around, we use the instance to find its classloader
                            // and obtain a reference to its "Event" class
                            Class<?> clazz = svc.getClass().getClassLoader().loadClass(Event.class.getName());
                            // and try to find a constructor
                            Constructor<?> ctor = clazz.getConstructor(String.class, Map.class);
                            // instantiate the event, using the topic and payload
                            Object eventAdminEvent = ctor.newInstance(event.getTopic(), getPayload(event));
                            // and now try to find the supplied method (either postEvent or sendEvent)
                            Method methodReference = svc.getClass().getMethod(method, clazz);
                            // and invoke it
                            methodReference.invoke(svc, eventAdminEvent);
                        }
                        finally {
                            // make sure we always unget our service reference
                            m_context.ungetService(refs[0]);
                        }
                    }
                }
            }
            catch (Exception e) {
                // there is a lot that can go wrong, but not much we can do at this point
                // beyond logging the error message
                logError("Failed to invoke EventAdmin: %s", e, e.getMessage());
            }
        }

        private Map<String, String> getPayload(Event event) {
            Map<String, String> payload = new HashMap<>();
            for (String propertyName : event.getPropertyNames()) {
                payload.put(propertyName, event.getProperty(propertyName).toString());
            }
            return payload;
        }
    }

    /**
     * Internal LogService that wraps delegates to actual InternalLogger. Used to inject into the DeploymentAdmin only.
     */
    final class LogServiceBridge implements LogService {
        @Override
        public void log(int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(int level, String message, Throwable exception) {
            invokeInternalLogService(level, message, exception);
        }

        @Override
        public void log(ServiceReference sr, int level, String message) {
            log(level, message, null);
        }

        @Override
        public void log(ServiceReference sr, int level, String message, Throwable exception) {
            log(level, message, exception);
        }

        private void invokeInternalLogService(int level, String message, Throwable exception) {
            switch (level) {
                case LogService.LOG_ERROR:
                    logError(message, exception);
                    break;
                case LogService.LOG_WARNING:
                    logWarning(message, exception);
                    break;
                case LogService.LOG_INFO:
                    logInfo(message, exception);
                    break;
                case LogService.LOG_DEBUG:
                default:
                    logDebug(message, exception);
                    break;
            }
        }
    }

    private final DeploymentAdmin m_deploymentAdmin;

    private final boolean m_ownDeploymentAdmin;

    public DeploymentHandlerImpl(BundleContext bundleContext, PackageAdmin packageAdmin) {
        super("deployment");
        m_ownDeploymentAdmin = true;
        m_deploymentAdmin = new DeploymentAdminImpl();
        configureField(m_deploymentAdmin, BundleContext.class, bundleContext);
        configureField(m_deploymentAdmin, PackageAdmin.class, packageAdmin);
        configureField(m_deploymentAdmin, EventAdmin.class, new EventAdminBridge(bundleContext));
        configureField(m_deploymentAdmin, LogService.class, new LogServiceBridge());
    }

    DeploymentHandlerImpl(DeploymentAdmin deploymentAdmin) {
        super("deployment");
        m_ownDeploymentAdmin = false;
        m_deploymentAdmin = deploymentAdmin;
    }

    @Override
    public SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException {
        return getAvailableVersions(getEndpoint(getServerURL(), getIdentification()));
    }

    @Override
    public DownloadHandle getDownloadHandle(Version version, boolean fixPackage) throws RetryAfterException {
        return getDownloadHandle(getPackageURL(version, fixPackage));
    }

    @Override
    public InputStream getInputStream(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getInputStream(getPackageURL(version, fixPackage));
    };

    @Override
    public Version getInstalledVersion() {
        Version highestVersion = Version.emptyVersion;
        String identification = getIdentification();

        DeploymentPackage[] installedPackages = m_deploymentAdmin.listDeploymentPackages();
        for (DeploymentPackage installedPackage : installedPackages) {
            String packageId = installedPackage.getName();
            Version packageVersion = installedPackage.getVersion();

            if (identification.equals(packageId) && packageVersion.compareTo(highestVersion) > 0) {
                highestVersion = packageVersion;
            }
        }
        return highestVersion;
    };

    @Override
    public String getName() {
        return "deployment";
    };

    @Override
    public long getSize(Version version, boolean fixPackage) throws RetryAfterException, IOException {
        return getPackageSize(getPackageURL(version, fixPackage));
    }

    @Override
    public void install(InputStream inputStream) throws InstallationFailedException, IOException {
        try {
            m_deploymentAdmin.installDeploymentPackage(inputStream);
        }
        catch (DeploymentException exception) {
            Throwable cause = exception.getCause();
            // Properly handle possible server overload...
            if (cause instanceof RetryAfterException) {
                throw (RetryAfterException) cause;
            }
            throw new InstallationFailedException("Installation of deployment package failed!", exception);
        }
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

    private URL getPackageURL(Version version, boolean fixPackage) throws RetryAfterException {
        return getEndpoint(getServerURL(), getIdentification(), fixPackage ? getInstalledVersion() : Version.emptyVersion, version);
    }
}
