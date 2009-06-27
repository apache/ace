package net.luminis.liq.test.http.listener;

import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Activator for the integration test.
 */
public class Activator extends TestActivatorBase {

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createService()
            .setImplementation(new ServletConfiguratorIntegrationTest(manager))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return new Class[] { ServletConfiguratorIntegrationTest.class };
    }

}
