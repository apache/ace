package net.luminis.liq.discovery.upnp;

import net.luminis.liq.discovery.Discovery;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.upnp.UPnPDevice;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {


        StringBuffer deviceFilter = new StringBuffer();
        deviceFilter.append("(")
            .append(UPnPDevice.TYPE).append("=")
            .append(UPnPBasedDiscovery.DEVICE_TYPE).append(")");

        manager.add(createService()
            .setInterface(new String[] {Discovery.class.getName()}, null)
            .setImplementation(UPnPBasedDiscovery.class)
                .add(createServiceDependency()
                    .setService(LogService.class)
                    .setRequired(false))
                //not required
                .add(createServiceDependency()
                    .setService(UPnPDevice.class, deviceFilter.toString())
                    .setCallbacks("added", "removed")
                    .setRequired(false))
                    );
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

}