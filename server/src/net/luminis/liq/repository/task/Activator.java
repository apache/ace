package net.luminis.liq.repository.task;

import java.util.Properties;

import net.luminis.liq.discovery.Discovery;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        // TODO we need to fix these property constants
        props.put("taskName", RepositoryReplicationTask.class.getName());
        props.put("description", "Synchronizes repositories.");
        manager.add(createService()
            .setInterface(Runnable.class.getName(), props)
            .setImplementation(RepositoryReplicationTask.class)
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
