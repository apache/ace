package net.luminis.liq.client.repositoryuseradmin.impl;

import net.luminis.liq.client.repositoryuseradmin.RepositoryUserAdmin;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.PreferencesService;

/**
 * Activator for the Repository UserAdmin. Note that this UserAdmin is not intended
 * to be a full implementation of the UserAdmin specification, but rather a
 * value-object model that uses the UserAdmin interface for convenience.
 */
public class Activator extends DependencyActivatorBase {

    RepositoryUserAdminImpl m_impl;

    @Override
    public void init(BundleContext context, DependencyManager manager) {
        m_impl = new RepositoryUserAdminImpl();
        manager.add(createService()
            .setInterface(RepositoryUserAdmin.class.getName(), null)
            .setImplementation(m_impl)
            .add(createServiceDependency()
                 .setService(PreferencesService.class)
                 .setRequired(true))
            .add(createServiceDependency()
                 .setService(LogService.class)
                 .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // At least, save our progress.
        m_impl.logout(true);
    }
}
