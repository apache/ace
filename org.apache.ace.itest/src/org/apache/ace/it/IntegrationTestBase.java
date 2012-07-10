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
import static org.apache.ace.test.utils.Util.properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import static junit.framework.Assert.*;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.ComponentDependencyDeclaration;
import org.apache.felix.dm.ComponentStateListener;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.ServiceDependency;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Base class for integration tests. There is no technical reason to use this, but it might make
 * your life easier.<br>
 * <br>
 * {@link org.apache.ace.it.ExampleTest} shows a minimal example of an integration test.
 *
 */
public class IntegrationTestBase extends TestCase {
    /**
     * If we have to wait for a service, wait this amount of seconds.
     */
    private static final int SERVICE_TIMEOUT = 5;

    protected BundleContext m_bundleContext = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    protected DependencyManager m_dependencyManager;

    /**
     * The 'before' callback will be called after the components from {@link #getDependencies} have been
     * added, but you cannot necessarily rely on injected members here. You can use the {@link #configure} and
     * {@link #configureFactory} methods, as well as the {@link #getService} methods.<br>
     * <br>
     * The {@link #before} callback is most useful for configuring services that have been provisioned
     * in the 'configuration' method.
     */
    protected void before() throws Exception {}

    /**
     * Gets a list of components that must be started before the test is started; this useful to
     * (a) add additional services, e.g. services that should be picked up by the service under
     * test, or (b) to declare 'this' as a component, and get services injected.
     */
    protected Component[] getDependencies() {
        return new Component[0];
    }

    /**
     * Write configuration for a single service. For example,
     * <pre>
     *   configure("org.apache.felix.http",
     *     "org.osgi.service.http.port", "1234");
     * </pre>
     */
    protected void configure(String pid, String... configuration) throws IOException {
        Properties props = properties(configuration);
        Configuration config = getConfiguration(pid);
        config.update(props);
    }

    /**
     * Creates a factory configuration with the given properties, just like {@link #configure}.
     * @return The PID of newly created configuration.
     */
    protected String configureFactory(String factoryPid, String... configuration) throws IOException {
        Properties props = properties(configuration);
        Configuration config = createFactoryConfiguration(factoryPid);
        config.update(props);
        return config.getPid();
    }

    public void setUp() throws Exception {
    	System.out.println("setup");
        m_dependencyManager = new DependencyManager(m_bundleContext);
        Component[] components = getDependencies();
        ComponentCounter listener = new ComponentCounter(components);

        // Register our listener for all the services...
        for (Component component : components) {
            component.addStateListener(listener);
        }

        // Then give them to the dependency manager...
        for (Component component : components) {
            m_dependencyManager.add(component);
        }

        // Call back the implementation...
        before();

        // And wait for all components to come online.
        try {
            if (!listener.waitForEmpty(SERVICE_TIMEOUT, SECONDS)) {
                fail("Not all components were started. Still missing the following:\n" + listener.componentsString());
            }
        }
        catch (InterruptedException e) {
            fail("Interrupted while waiting for services to get started.");
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T getService(Class<T> serviceClass, String filterString) throws InvalidSyntaxException {
        T serviceInstance = null;

        ServiceTracker serviceTracker;
        if (filterString == null) {
            serviceTracker = new ServiceTracker(m_bundleContext, serviceClass.getName(), null);
        }
        else {
            String classFilter = "(" + Constants.OBJECTCLASS + "=" + serviceClass.getName() + ")";
            filterString = "(&" + classFilter + filterString + ")";
            serviceTracker = new ServiceTracker(m_bundleContext, m_bundleContext.createFilter(filterString), null);
        }
        serviceTracker.open();
        try {
            serviceInstance = (T) serviceTracker.waitForService(SERVICE_TIMEOUT * 1000);

            if (serviceInstance == null) {
                fail(serviceClass + " service not found.");
            }
            else {
                return serviceInstance;
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
            fail(serviceClass + " service not available: " + e.toString());
        }

        return serviceInstance;
    }

    protected <T> T getService(Class<T> serviceClass) {
        try {
            return getService(serviceClass, null);
        }
        catch (InvalidSyntaxException e) {
            return null;
            // Will not happen, since we don't pass in a filter.
        }
    }

    private Configuration getConfiguration(String pid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        return admin.getConfiguration(pid, null);
    }

    private Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        return admin.createFactoryConfiguration(factoryPid, null);
    }

    // Dependency Manager bridge methods

    protected Component createComponent() {
        return m_dependencyManager.createComponent();
    }

    protected ServiceDependency createServiceDependency() {
        return m_dependencyManager.createServiceDependency();
    }


    private static class ComponentCounter implements ComponentStateListener {
        private final List<Component> m_components = new ArrayList<Component>();
        private final CountDownLatch m_latch;

        public ComponentCounter(Component[] components) {
            m_components.addAll(Arrays.asList(components));
            m_latch = new CountDownLatch(components.length);
        }

        public void starting(Component component) {
        }

        public void started(Component component) {
            m_components.remove(component);
            m_latch.countDown();
        }

        public void stopping(Component component) {
        }

        public void stopped(Component component) {
        }

        public boolean waitForEmpty(long timeout, TimeUnit unit) throws InterruptedException {
            return m_latch.await(timeout, unit);
        }

        public String componentsString() {
            StringBuilder result = new StringBuilder();
            for (Component component : m_components) {
                result.append(component).append('\n');
                for (ComponentDependencyDeclaration dependency : (List<ComponentDependencyDeclaration>) component.getDependencies()) {
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
    };
}
