package net.luminis.liq.test.deployment;

import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.http.HttpService;

public class Activator extends TestActivatorBase {
    @SuppressWarnings("unchecked")
    private Class[] m_classes = new Class[] { DeploymentIntegrationTest.class };

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return m_classes;
    }

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createService()
            .setImplementation(DeploymentIntegrationTest.class)
            .add(createServiceDependency().setService(HttpService.class).setRequired(true))
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentAdmin.class).setRequired(true)));
    }
}
