package net.luminis.liq.discovery.property;

import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.discovery.property.constants.DiscoveryConstants;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary properties = new Hashtable();
        properties.put(Constants.SERVICE_PID, DiscoveryConstants.DISCOVERY_PID);
        manager.add(createService()
                        .setInterface(new String[] {Discovery.class.getName()}, properties)
                        .setImplementation(PropertyBasedDiscovery.class)
                        .add(createConfigurationDependency()
                            .setPid(DiscoveryConstants.DISCOVERY_PID))
                        .add(createServiceDependency()
                            .setService(LogService.class)
                            .setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

}