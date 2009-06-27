package net.luminis.liq.client.repository.helper.user.impl;

import java.util.Properties;

import net.luminis.liq.client.repository.helper.ArtifactHelper;
import net.luminis.liq.client.repository.helper.ArtifactRecognizer;
import net.luminis.liq.client.repository.helper.user.UserAdminHelper;
import net.luminis.liq.client.repository.object.ArtifactObject;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;

/**
 * Activator class for the UserAdmin ArtifactHelper.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(ArtifactObject.KEY_MIMETYPE, UserAdminHelper.MIMETYPE);
        UserHelperImpl helperImpl = new UserHelperImpl();
        manager.add(createService()
            .setInterface(ArtifactHelper.class.getName(), props)
            .setImplementation(helperImpl));
        props = new Properties();
        props.put(Constants.SERVICE_RANKING, 10);
        manager.add(createService()
            .setInterface(ArtifactRecognizer.class.getName(), props)
            .setImplementation(helperImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do
    }

}
