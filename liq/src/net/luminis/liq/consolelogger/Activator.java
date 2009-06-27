package net.luminis.liq.consolelogger;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(LogService.class.getName(), null)
            .setImplementation(Logger.class)
        );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
