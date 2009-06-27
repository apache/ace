package net.luminis.liq.configurator.useradmin.task;

import java.util.Properties;

import net.luminis.liq.resourceprocessor.useradmin.UserAdminConfigurator;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;

/**
 * Activator for the UserAdmin updater task.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put("taskName", UpdateUserAdminTask.PID);
        props.put("description", "Synchronizes the UserAdmin with the server.");
        manager.add(createService()
            .setInterface(Runnable.class.getName(), props)
            .setImplementation(UpdateUserAdminTask.class)
            .add(createServiceDependency().setService(UserAdminConfigurator.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            .add(createConfigurationDependency().setPid(UpdateUserAdminTask.PID))
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // Nothing to do, the runnable will be pulled automatically.
    }

}
