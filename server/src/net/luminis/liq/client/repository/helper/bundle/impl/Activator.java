package net.luminis.liq.client.repository.helper.bundle.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.client.repository.helper.ArtifactHelper;
import net.luminis.liq.client.repository.helper.ArtifactRecognizer;
import net.luminis.liq.client.repository.helper.bundle.BundleHelper;
import net.luminis.liq.client.repository.object.ArtifactObject;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;

/**
 * Activator class for the Bundle ArtifactHelper.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(ArtifactObject.KEY_MIMETYPE, BundleHelper.MIMETYPE);
        BundleHelperImpl helperImpl = new BundleHelperImpl();
        manager.add(createService()
            .setInterface(ArtifactHelper.class.getName(), props)
            .setImplementation(helperImpl));
        manager.add(createService()
            .setInterface(ArtifactRecognizer.class.getName(), null)
            .setImplementation(helperImpl));
        manager.add(createService()
            .setInterface(BundleHelper.class.getName(), null)
            .setImplementation(helperImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do
    }

}
