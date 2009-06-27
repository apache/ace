package net.luminis.liq.repository.servlet;


import javax.servlet.http.HttpServlet;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String REPOSITORY_PID = "net.luminis.liq.repository.servlet.RepositoryServlet";
    public static final String REPOSITORY_REPLICATION_PID = "net.luminis.liq.repository.servlet.RepositoryReplicationServlet";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(HttpServlet.class.getName(), null)
            .setImplementation(RepositoryServlet.class)
            .add(createConfigurationDependency()
                .setPropagate(true)
                .setPid(REPOSITORY_PID))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));

        manager.add(createService()
            .setInterface(HttpServlet.class.getName(), null)
            .setImplementation(RepositoryReplicationServlet.class)
            .add(createConfigurationDependency()
                .setPropagate(true)
                .setPid(REPOSITORY_REPLICATION_PID))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}
