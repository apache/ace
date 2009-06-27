package net.luminis.liq.obr.servlet;

import javax.servlet.http.HttpServlet;

import net.luminis.liq.obr.storage.BundleStore;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String PID = "net.luminis.liq.obr.servlet";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(HttpServlet.class.getName(), null)
            .setImplementation(BundleServlet.class)
            .add(createConfigurationDependency()
                .setPropagate(true)
                .setPid(PID))
            .add(createServiceDependency()
                .setService(BundleStore.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}
