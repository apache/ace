package net.luminis.liq.obr.storage.file;

import net.luminis.liq.obr.metadata.MetadataGenerator;
import net.luminis.liq.obr.storage.BundleStore;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public static final String PID = "net.luminis.liq.obr.storage.file";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(BundleStore.class.getName(), null)
            .setImplementation(BundleFileStore.class)
            .add(createConfigurationDependency()
                .setPid(PID))
            .add(createServiceDependency()
                .setService(MetadataGenerator.class)
                .setRequired(true))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do here
    }
}
