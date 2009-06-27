package net.luminis.liq.test.repository;

import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the integration test.
 */
public class Activator extends TestActivatorBase {

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createService()
            .setImplementation(RepositoryTest.class)
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(PreferencesService.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return new Class[] { RepositoryTest.class };
    }

}

