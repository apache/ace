package net.luminis.liq.test.bundlestop;


import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * This bundle stops the systembundle whenever the deploymentadmin is done deploying.
 */
public class Activator extends DependencyActivatorBase {
    @SuppressWarnings("unchecked")
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        String[] topics = new String[] {EventConstants.EVENT_TOPIC, "org/osgi/service/deployment/COMPLETE"};
        Dictionary properties = new Hashtable();
        properties.put(EventConstants.EVENT_TOPIC, topics);

        SystemBundleStopper stopper = new SystemBundleStopper();
        context.addBundleListener(stopper);
        manager.add(createService()
            .setInterface(EventHandler.class.getName(), properties)
            .setImplementation(stopper)
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}

