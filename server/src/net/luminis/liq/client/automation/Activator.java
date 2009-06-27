package net.luminis.liq.client.automation;


import net.luminis.liq.client.repository.RepositoryAdmin;
import net.luminis.liq.client.repository.repository.DeploymentVersionRepository;
import net.luminis.liq.client.repository.repository.GatewayRepository;
import net.luminis.liq.client.repository.stateful.StatefulGatewayRepository;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;


/**
 * Bundle activator for the gateway operator automation.
 */
public class Activator extends DependencyActivatorBase {

    /**
     * Initialize and set dependencies
     */
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setImplementation(AutoGatewayOperator.class)
            .add(createConfigurationDependency().setPid(AutoGatewayOperator.PID))
            .add(createServiceDependency().setRequired(true).setService(UserAdmin.class))
            .add(createServiceDependency().setRequired(true).setService(GatewayRepository.class))
            .add(createServiceDependency().setRequired(true).setService(StatefulGatewayRepository.class))
            .add(createServiceDependency().setRequired(true).setService(DeploymentVersionRepository.class))
            .add(createServiceDependency().setRequired(true).setService(RepositoryAdmin.class))
            .add(createServiceDependency().setRequired(false).setService(LogService.class))
        );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
