package net.luminis.liq.identification.ifconfig;

import net.luminis.liq.identification.Identification;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
       manager.add(createService()
            .setInterface(new String[] {Identification.class.getName()}, null)
            .setImplementation(IfconfigIdentification.class)
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}