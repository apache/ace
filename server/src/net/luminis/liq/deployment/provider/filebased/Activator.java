package net.luminis.liq.deployment.provider.filebased;

import net.luminis.liq.deployment.provider.DeploymentProvider;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String PID = "net.luminis.liq.deployment.provider.filebased";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(DeploymentProvider.class.getName(), null)
            .setImplementation(FileBasedProvider.class)
            .add(createConfigurationDependency()
                .setPid(PID)
             )
             .add(createServiceDependency()
                 .setService(LogService.class)
                 .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext arg0, DependencyManager arg1) throws Exception {
        // TODO Auto-generated method stub
    }
}
