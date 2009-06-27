package net.luminis.liq.deployment.servlet;
import javax.servlet.http.HttpServlet;

import net.luminis.liq.deployment.provider.DeploymentProvider;
import net.luminis.liq.deployment.streamgenerator.StreamGenerator;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String PID = "net.luminis.liq.deployment.servlet";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(HttpServlet.class.getName(), null)
            .setImplementation(DeploymentServlet.class)
            .add(createServiceDependency().setService(StreamGenerator.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentProvider.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            .add(createConfigurationDependency().setPropagate(true).setPid(PID)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}