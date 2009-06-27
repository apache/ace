package net.luminis.liq.resourceprocessor.useradmin.impl;

import java.util.Properties;

import net.luminis.liq.resourceprocessor.useradmin.UserAdminConfigurator;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Activator for the UserAdmin resource processor. The services of this bundle
 * will be published as a UserAdminConfigurator, and a ResourceProcessor for use
 * by the Deployment Admin.
 */
public class Activator extends DependencyActivatorBase {
    private static final String PID = "net.luminis.liq.resourceprocessor.useradmin";

    @Override
    public void init(BundleContext context, DependencyManager manager) {
        UserAdminStore userAdminStore = new UserAdminStore(context);
        Processor processor = new Processor(userAdminStore);

        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID);
        manager.add(createService().setInterface(ResourceProcessor.class.getName(), props)
                .setImplementation(processor)
                .add(createServiceDependency()
                    .setService(UserAdminConfigurator.class)
                    .setRequired(true)) // This UserAdminConfigurator is the same as below,
                                        // and we don't want to add UserAdmins twice.
                .add(createServiceDependency()
                    .setService(LogService.class)
                    .setRequired(false)));


        manager.add(createService().setInterface(UserAdminConfigurator.class.getName(), null)
            .setImplementation(userAdminStore)
            .add(createServiceDependency()
                .setService(UserAdmin.class)
                .setAutoConfig(false)
                .setCallbacks("userAdminAdded", "userAdminRemoved"))
            .add(createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}
