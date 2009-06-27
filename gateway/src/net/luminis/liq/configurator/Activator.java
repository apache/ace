package net.luminis.liq.configurator;

import java.io.File;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setImplementation(new Configurator(new File(
                getProperty(context.getProperty(Activator.class.getPackage().getName() + "CONFIG_DIR"), "conf")),
                getProperty(context.getProperty(Activator.class.getPackage().getName() + "POLL_INTERVAL"), 2000)))
            .add(createServiceDependency()
                .setService(ConfigurationAdmin.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    public String getProperty(String prop, String def) {
        return (prop == null) ? def : prop;
    }

    public long getProperty(String prop, long def) {
        return (prop == null) ? def : Long.parseLong(prop);
    }
}
