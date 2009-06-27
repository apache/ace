package net.luminis.sample.managedservice;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ManagedService;

public class Activator extends DependencyActivatorBase {

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary properties = new Hashtable();
        properties.put(Constants.SERVICE_PID, "net.luminis.sample.managedservice");
        manager.add(createService()
                        .setInterface(ManagedService.class.getName(), properties)
                        .setImplementation(Impl.class));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

}