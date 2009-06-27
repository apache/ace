package net.luminis.liq.deployment.deploymentadmin;

import net.luminis.liq.deployment.Deployment;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(Deployment.class.getName(), null)
            .setImplementation(DeploymentAdminDeployer.class)
            .add(createServiceDependency().setService(DeploymentAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}