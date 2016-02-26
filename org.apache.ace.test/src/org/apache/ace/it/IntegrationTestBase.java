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
package org.apache.ace.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.ace.test.utils.Util.dictionary;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.ComponentDependencyDeclaration;
import org.apache.felix.dm.ComponentState;
import org.apache.felix.dm.ComponentStateListener;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.ServiceDependency;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import junit.framework.TestCase;

/**
 * Base class for integration tests. There is no technical reason to use this, but it might make your life easier.<br>
 * <br>
 * {@link org.apache.ace.it.ExampleTest} shows a minimal example of an integration test.
 * 
 */
public class IntegrationTestBase extends TestCase {
    private static class ComponentCounter implements ComponentStateListener {
        private final List<Component> m_components = new ArrayList<>();
        private final CountDownLatch m_latch;

        public ComponentCounter(Component[] components) {
            m_components.addAll(Arrays.asList(components));
            m_latch = new CountDownLatch(components.length);
        }

        public String componentsString() {
            StringBuilder result = new StringBuilder();
            for (Component component : m_components) {
                result.append(component).append('\n');
                for (ComponentDependencyDeclaration dependency : component.getComponentDeclaration().getComponentDependencies()) {
                    result.append("  ")
                        .append(dependency.toString())
                        .append(" ")
                        .append(ComponentDependencyDeclaration.STATE_NAMES[dependency.getState()])
                        .append('\n');
                }
                result.append('\n');
            }
            return result.toString();
        }

        public boolean waitForEmpty(long timeout, TimeUnit unit) throws InterruptedException {
            return m_latch.await(timeout, unit);
        }

        @Override
        public void changed(Component component, ComponentState state) {
            if (state == ComponentState.TRACKING_OPTIONAL && m_components.remove(component)) {
                m_latch.countDown();
            }
        }
    }

    /**
     * If we have to wait for a service, wait this amount of seconds.
     */
    private static final int SERVICE_TIMEOUT = 15;

    private final Map<String, ServiceTracker<?, ?>> m_trackedServices = new HashMap<>();
    private final List<Configuration> m_trackedConfigurations = new ArrayList<>();

    private boolean m_cleanConfigurations = true;
    private boolean m_closeServiceTrackers = true;

    protected BundleContext m_bundleContext;
    protected DependencyManager m_dependencyManager;
    protected Component m_eventLoggingComponent;
    protected Component m_loggingComponent;

    /**
     * Overridden to ensure that our {@link #tearDown()} method is always called, even when {@link #setUp()} fails with
     * an exception (by default, JUnit does not call this method when the set up fails).
     * 
     * @see junit.framework.TestCase#runBare()
     */
    @Override
    public final void runBare() throws Throwable {
        Throwable exception = null;
        try {
            setUp();

            runTest();
        }
        catch (Throwable running) {
            exception = running;
        }
        finally {
            try {
                tearDown();
            }
            catch (Throwable tearingDown) {
                if (exception == null) {
                    exception = tearingDown;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Write configuration for a single service. For example,
     * 
     * <pre>
     * configure(&quot;org.apache.felix.http&quot;,
     *     &quot;org.osgi.service.http.port&quot;, &quot;1234&quot;);
     * </pre>
     * 
     * @param pid
     *            the configuration PID to configure;
     * @param configuration
     *            the configuration key/values (as pairs).
     */
    protected void configure(String pid, String... configuration) throws IOException {
        Dictionary<String, Object> props = dictionary(configuration);
        Configuration config = getConfiguration(pid);
        config.update(props);
        m_trackedConfigurations.add(config);
    }

    /**
     * The 'after' callback will be called after all components from {@link #getDependencies} have been started.<br>
     * <br>
     * The {@link #after} callback is most useful for configuring additional services after all mandatory services are
     * resolved.
     */
    protected void configureAdditionalServices() throws Exception {
    }

    /**
     * Creates a factory configuration with the given properties, just like {@link #configure}.
     * 
     * @return The PID of newly created configuration.
     */
    protected String configureFactory(String factoryPid, String... configuration) throws IOException {
        Dictionary<String, Object> props = dictionary(configuration);
        Configuration config = createFactoryConfiguration(factoryPid);
        config.update(props);
        m_trackedConfigurations.add(config);
        return config.getPid();
    }

    /**
     * Configures the "org.apache.felix.http" and waits until the service is actually ready to process requests.
     * <p>
     * The reason that this method exists is that configuring the Felix HTTP bundle causes it to actually stop and
     * restart, which is done asynchronously. This means that we cannot be sure that depending code is always able to
     * directly use the HTTP service after its been configured.
     * </p>
     * 
     * @param port
     *            the new port to run the HTTP service on;
     * @param configuration
     *            the extra (optional) configuration key/values (as pairs).
     * @see #configure(String, String...)
     */
    protected void configureHttpService(int port, String... configuration) throws IOException, InterruptedException {
        final String httpPID = "org.apache.felix.http";
        final String portProperty = "org.osgi.service.http.port";
        final String expectedPort = Integer.toString(port);

        // Do not track this configuration (yet)...
        Dictionary<String, Object> props = dictionary(configuration);
        props.put(portProperty, expectedPort);

        Configuration config = getConfiguration(httpPID);
        config.update(props);

        // This ugly warth is necessary as Felix HTTP currently brings the entire service down & up if it gets
        // reconfigured. There is no other way for us to tell whether the server is ready to accept calls...
        URL url = new URL(String.format("http://localhost:%d/", port));
        int tries = 50;
        boolean ready = false;
        do {
            Thread.sleep(50);

            try (InputStream is = url.openStream()) {
                is.close();
                ready = true;
            }
            catch (FileNotFoundException exception) {
                // Ok; expected...
                ready = true;
            }
            catch (IOException exception) {
                // Not there yet...
            }
        }
        while (!ready && tries-- > 0);

        if (tries == 0) {
            throw new IOException("Failed waiting on HTTP service?!");
        }
    }

    /**
     * The 'before' callback will be called after the components from {@link #getDependencies} have been added, but you
     * cannot necessarily rely on injected members here. You can use the {@link #configure} and
     * {@link #configureFactory} methods, as well as the {@link #getService} methods.<br>
     * <br>
     * The {@link #before} callback is most useful for configuring services that have been provisioned in the
     * 'configuration' method.
     */
    protected void configureProvisionedServices() throws Exception {
    }

    /**
     * Bridge method for dependency manager.
     * 
     * @return a new {@link Component}.
     */
    protected Component createComponent() {
        return m_dependencyManager.createComponent();
    }

    /**
     * Creates a new factory configuration.
     * 
     * @param factoryPid
     *            the PID of the factory to create a new configuration for.
     * @return a new {@link Configuration} object, never <code>null</code>.
     * @throws IOException
     *             if access to the persistent storage failed.
     */
    protected Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        Configuration config = admin.createFactoryConfiguration(factoryPid, null);
        m_trackedConfigurations.add(config);
        return config;
    }

    /**
     * Bridge method for dependency manager.
     * 
     * @return a new {@link ServiceDependency}.
     */
    protected ServiceDependency createServiceDependency() {
        return m_dependencyManager.createServiceDependency();
    }

    /**
     * Disables logging to the console.
     */
    protected synchronized void disableEventLogging() {
        if (m_eventLoggingComponent != null) {
            DependencyManager dm = m_dependencyManager;
            dm.remove(m_eventLoggingComponent);
            m_eventLoggingComponent = null;
        }
    }

    /**
     * Disables logging to the console.
     */
    protected synchronized void disableLogging() {
        if (m_loggingComponent != null) {
            DependencyManager dm = m_dependencyManager;
            dm.remove(m_loggingComponent);
            m_loggingComponent = null;
        }
    }

    protected void doTearDown() throws Exception {
        // Nop
    }

    /**
     * Enables logging events to the console. Mainly useful when debugging tests.
     */
    protected synchronized void enableEventLogging() {
        DependencyManager dm = m_dependencyManager;
        m_eventLoggingComponent = dm.createComponent()
            .setInterface(EventHandler.class.getName(), new Properties() {
                {
                    put(EventConstants.EVENT_TOPIC, "*");
                }
            })
            .setImplementation(new EventHandler() {
                @Override
                public void handleEvent(Event event) {
                    System.out.print("[EVENT] " + event.getTopic());
                    for (String key : event.getPropertyNames()) {
                        System.out.print(" " + key + "=" + event.getProperty(key));
                    }
                    System.out.println();
                }
            });
        dm.add(m_eventLoggingComponent);
    }

    /**
     * Enables logging to the console. Mainly useful when debugging tests.
     */
    protected synchronized void enableLogging() {
        if (m_loggingComponent == null) {
            DependencyManager dm = m_dependencyManager;
            m_loggingComponent = dm.createComponent()
                .setInterface(LogService.class.getName(), new Properties() {
                    {
                        put(Constants.SERVICE_RANKING, Integer.valueOf(1000));
                    }
                })
                .setImplementation(new LogService() {
                    @Override
                    public void log(int level, String message) {
                        log(null, level, message, null);
                    }

                    @Override
                    public void log(int level, String message, Throwable exception) {
                        log(null, level, message, exception);
                    }

                    @Override
                    public void log(ServiceReference sr, int level, String message) {
                        log(sr, level, message, null);
                    }

                    @Override
                    public void log(ServiceReference sr, int level, String message, Throwable exception) {
                        System.out.println("[LOG] " +
                            (sr == null ? "" : sr + " ") +
                            level + " " +
                            message + " " +
                            (exception == null ? "" : exception));
                    }
                });
            dm.add(m_loggingComponent);
        }
    }

    /**
     * Gets an existing configuration or creates a new one, in case it does not exist.
     * 
     * @param pid
     *            the PID of the configuration to return.
     * @return a {@link Configuration} instance, never <code>null</code>.
     * @throws IOException
     *             if access to the persistent storage failed.
     */
    protected Configuration getConfiguration(String pid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        Configuration configuration = admin.getConfiguration(pid, null);
        m_trackedConfigurations.add(configuration);
        return configuration;
    }

    /**
     * Gets a list of components that must be started before the test is started; this useful to (a) add additional
     * services, e.g. services that should be picked up by the service under test, or (b) to declare 'this' as a
     * component, and get services injected.
     */
    protected Component[] getDependencies() {
        return new Component[0];
    }

    /**
     * Returns a list of strings representing the result of the given request URL.
     * 
     * @param requestURL
     *            the URL to access and return the response as strings.
     * @return a list of strings, never <code>null</code>.
     * @throws IOException
     *             in case accessing the requested URL failed.
     */
    protected List<String> getResponse(String requestURL) throws IOException {
        return getResponse(new URL(requestURL));
    }

    /**
     * Returns a list of strings representing the result of the given request URL.
     * 
     * @param requestURL
     *            the URL to access and return the response as strings.
     * @return a list of strings, never <code>null</code>.
     * @throws IOException
     *             in case accessing the requested URL failed.
     */
    protected List<String> getResponse(URL requestURL) throws IOException {
        List<String> result = new ArrayList<>();
        URLConnection conn = requestURL.openConnection();
        try (InputStream in = conn.getInputStream()) {
            final StringBuilder element = new StringBuilder();
            int b;
            while ((b = in.read()) > 0) {
                switch (b) {
                    case '\n':
                        result.add(element.toString());
                        element.setLength(0);
                        break;
                    default:
                        element.append((char) b);
                }
            }
            if (element.length() > 0) {
                result.add(element.toString());
            }
        }
        finally {
            NetUtils.closeConnection(conn);
        }
        return result;
    }

    /**
     * Convenience method to return an OSGi service.
     * 
     * @param serviceClass
     *            the service class to return.
     * @return a service instance, can be <code>null</code>.
     */
    protected <T> T getService(Class<T> serviceClass) {
        try {
            return getService(serviceClass, null);
        }
        catch (InvalidSyntaxException e) {
            return null;
            // Will not happen, since we don't pass in a filter.
        }
    }

    /**
     * Convenience method to return an OSGi service.
     * 
     * @param serviceClass
     *            the service class to return;
     * @param filterString
     *            the (optional) filter string, can be <code>null</code>.
     * @return a service instance, can be <code>null</code>.
     */
    @SuppressWarnings("unchecked")
    protected <T> T getService(Class<T> serviceClass, String filterString) throws InvalidSyntaxException {
        T serviceInstance = null;

        if (filterString != null && !"".equals(filterString)) {
            filterString = String.format("(&(%s=%s)%s)", Constants.OBJECTCLASS, serviceClass.getName(), filterString);
        }
        else {
            filterString = String.format("(%s=%s)", Constants.OBJECTCLASS, serviceClass.getName());
        }

        ServiceTracker<?, ?> serviceTracker = m_trackedServices.get(filterString);
        if (serviceTracker == null) {
            serviceTracker = new ServiceTracker<>(m_bundleContext, FrameworkUtil.createFilter(filterString), null);
            serviceTracker.open();

            m_trackedServices.put(filterString, serviceTracker);
        }

        try {
            serviceInstance = (T) serviceTracker.waitForService(SERVICE_TIMEOUT * 1000);

            if (serviceInstance == null) {
                fail(serviceClass + " service not found.");
            }

            return serviceInstance;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            serviceTracker.close();
            fail(serviceClass + " service not available: " + e.toString());
        }

        return serviceInstance;
    }

    /**
     * Utility method to determine the number of test cases in the implementing class.
     * <p>
     * Test cases are considered <em>public</em> methods starting their name with "test".
     * </p>
     * 
     * @return a test count, >= 0.
     */
    protected final int getTestCount() {
        int count = 0;

        for (Method m : getClass().getMethods()) {
            if (m.getName().startsWith("test")) {
                count++;
            }
        }

        return count;
    }

    /**
     * @param filter
     * @return an array of configurations, can be <code>null</code>.
     */
    protected Configuration[] listConfigurations(String filter) throws IOException, InvalidSyntaxException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        return admin.listConfigurations(filter);
    }

    /**
     * @param filter
     * @return the number of configurations that match the given filter, &gt;= 0.
     */
    protected int countConfigurations(String filter) throws IOException, InvalidSyntaxException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        Configuration[] configs = admin.listConfigurations(filter);
        return configs == null ? 0 : configs.length;
    }

    /**
     * @param type
     * @return the number of services are registered with the given type, &gt;= 0.
     */
    protected int countServices(Class<?> type) throws IOException, InvalidSyntaxException {
        return countServices(String.format("(%s=%s)", Constants.OBJECTCLASS, type.getName()));
    }

    /**
     * @param filter
     * @return the number of services that match the given filter, &gt;= 0.
     */
    protected int countServices(String filter) throws IOException, InvalidSyntaxException {
        ServiceReference<?>[] serviceRefs = m_bundleContext.getServiceReferences((String) null, filter);
        return serviceRefs == null ? 0 : serviceRefs.length;
    }

    /**
     * Sets whether or not any of the tracked configurations should be automatically be deleted when ending a test.
     * 
     * @param aClean
     *            <code>true</code> (the default) to clean configurations, <code>false</code> to disable this behaviour.
     */
    protected void setAutoDeleteTrackedConfigurations(boolean aClean) {
        m_cleanConfigurations = aClean;
    }

    /**
     * Set up of this test case.
     */
    protected final void setUp() throws Exception {
        m_bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
        m_dependencyManager = new DependencyManager(m_bundleContext);

        Component[] components = getDependencies();
        ComponentCounter listener = new ComponentCounter(components);

        // Register our listener for all the services...
        for (Component component : components) {
            component.add(listener);
        }

        // Then give them to the dependency manager...
        for (Component component : components) {
            m_dependencyManager.add(component);
        }

        // Call back the implementation...
        configureProvisionedServices();

        // And wait for all components to come online.
        try {
            if (!listener.waitForEmpty(SERVICE_TIMEOUT, SECONDS)) {
                fail("Not all components were started. Still missing the following:\n" + listener.componentsString());
            }

            // XXX it appears we run into race conditions between the setup and configuration of our services, use a
            // little delay to get things settled seems to help here...
            TimeUnit.MILLISECONDS.sleep(Integer.getInteger("org.apache.ace.it.testDelay", 500));

            configureAdditionalServices();
        }
        catch (InterruptedException e) {
            fail("Interrupted while waiting for services to get started.");
        }
    }

    @Override
    protected final void tearDown() throws Exception {
        try {
            doTearDown();
        }
        finally {
            if (m_cleanConfigurations) {
                for (Configuration c : m_trackedConfigurations) {
                    try {
                        c.delete();
                    }
                    catch (Exception exception) {
                        // Ignore...
                    }
                }
                m_trackedConfigurations.clear();
            }
            if (m_closeServiceTrackers) {
                for (ServiceTracker<?, ?> st : m_trackedServices.values()) {
                    try {
                        st.close();
                    }
                    catch (Exception exception) {
                        // Ignore...
                    }
                }
                m_trackedServices.clear();
            }
        }
    }
}
