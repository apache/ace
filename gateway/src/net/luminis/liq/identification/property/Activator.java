package net.luminis.liq.identification.property;

import net.luminis.liq.identification.Identification;
import net.luminis.liq.identification.property.constants.IdentificationConstants;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
       manager.add(createService()
            .setInterface(new String[] {Identification.class.getName()}, null)
            .setImplementation(PropertyBasedIdentification.class)
            .add(createConfigurationDependency()
                .setPid(IdentificationConstants.IDENTIFICATION_PID))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false))
            );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}