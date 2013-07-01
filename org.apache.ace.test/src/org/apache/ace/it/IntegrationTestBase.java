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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

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
    private static class ComponentCounter implements ComponentStateListener {
        private final List<Component> m_components = new ArrayList<Component>();
        private final CountDownLatch m_latch;

        public ComponentCounter(Component[] components) {
            m_components.addAll(Arrays.asList(components));
            m_latch = new CountDownLatch(components.length);
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

        public void started(Component component) {
            m_components.remove(component);
            m_latch.countDown();
        }

        public void starting(Component component) {
        }

        public void stopped(Component component) {
        }

        public void stopping(Component component) {
        }

        public boolean waitForEmpty(long timeout, TimeUnit unit) throws InterruptedException {
            return m_latch.await(timeout, unit);
        }
    }
    
    /**
     * If we have to wait for a service, wait this amount of seconds.
     */
    private static final int SERVICE_TIMEOUT = 5;
    protected BundleContext m_bundleContext;

    protected DependencyManager m_dependencyManager;

    /**
     * The 'after' callback will be called after all components from {@link #getDependencies} have been
     * started.<br>
     * <br>
     * The {@link #after} callback is most useful for configuring additional services after all mandatory 
     * services are resolved.
     */
    protected void configureAdditionalServices() throws Exception {}

    /**
     * The 'before' callback will be called after the components from {@link #getDependencies} have been
     * added, but you cannot necessarily rely on injected members here. You can use the {@link #configure} and
     * {@link #configureFactory} methods, as well as the {@link #getService} methods.<br>
     * <br>
     * The {@link #before} callback is most useful for configuring services that have been provisioned
     * in the 'configuration' method.
     */
    protected void configureProvisionedServices() throws Exception {}

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
     * @param factoryPid the PID of the factory to create a new configuration for.
     * @return a new {@link Configuration} object, never <code>null</code>.
     * @throws IOException if access to the persistent storage failed.
     */
    protected Configuration createFactoryConfiguration(String factoryPid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        return admin.createFactoryConfiguration(factoryPid, null);
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
     * Gets an existing configuration or creates a new one, in case it does not exist.
     * 
     * @param pid the PID of the configuration to return.
     * @return a {@link Configuration} instance, never <code>null</code>.
     * @throws IOException if access to the persistent storage failed.
     */
    protected Configuration getConfiguration(String pid) throws IOException {
        ConfigurationAdmin admin = getService(ConfigurationAdmin.class);
        return admin.getConfiguration(pid, null);
    }

    /**
     * Gets a list of components that must be started before the test is started; this useful to
     * (a) add additional services, e.g. services that should be picked up by the service under
     * test, or (b) to declare 'this' as a component, and get services injected.
     */
    protected Component[] getDependencies() {
        return new Component[0];
    }

    /**
     * Returns a list of strings representing the result of the given request URL.
     * 
     * @param requestURL the URL to access and return the response as strings.
     * @return a list of strings, never <code>null</code>.
     * @throws IOException in case accessing the requested URL failed.
     */
    protected List<String> getResponse(String requestURL) throws IOException {
    	return getResponse(new URL(requestURL));
    }

    /**
     * Returns a list of strings representing the result of the given request URL.
     * 
     * @param requestURL the URL to access and return the response as strings.
     * @return a list of strings, never <code>null</code>.
     * @throws IOException in case accessing the requested URL failed.
     */
    protected List<String> getResponse(URL requestURL) throws IOException {
        List<String> result = new ArrayList<String>();
        InputStream in = null;
        try {
            in = requestURL.openConnection().getInputStream();
            byte[] response = new byte[in.available()];
            in.read(response);

            final StringBuilder element = new StringBuilder();
            for (byte b : response) {
                switch(b) {
                    case '\n' :
                        result.add(element.toString());
                        element.delete(0, element.length());
                        break;
                    default :
                        element.append((char) b);
                }
            }
            if (element.length() > 0) {
            	result.add(element.toString());
            }
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // no problem.
            }
        }
        return result;
    }

    /**
     * Convenience method to return an OSGi service.
     * 
     * @param serviceClass the service class to return.
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
     * @param serviceClass the service class to return;
     * @param filterString the (optional) filter string, can be <code>null</code>.
     * @return a service instance, can be <code>null</code>.
     */
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
            component.addStateListener(listener);
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
            
        	configureAdditionalServices();
        	
            // Wait for CM to settle or we may get "socket closed" due to HTTP service restarts
            // TODO fix this, it slows down all tests
            Thread.sleep(2000);
        }
        catch (InterruptedException e) {
            fail("Interrupted while waiting for services to get started.");
        }
    }
}
