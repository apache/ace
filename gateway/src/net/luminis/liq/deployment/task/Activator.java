package net.luminis.liq.deployment.task;

import java.util.Dictionary;
import java.util.Properties;

import net.luminis.liq.deployment.Deployment;
import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.identification.Identification;
import net.luminis.liq.scheduler.constants.SchedulerConstants;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Dictionary updateProperties = new Properties();
        updateProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes the artifacts (bundles, resources) installed on this gateway with the server.");
        updateProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentUpdateTask.class.getName());
        updateProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");

        Dictionary checkProperties = new Properties();
        checkProperties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that checks for updates for gateway on the server.");
        checkProperties.put(SchedulerConstants.SCHEDULER_NAME_KEY, DeploymentCheckTask.class.getName());
        checkProperties.put(SchedulerConstants.SCHEDULER_RECIPE, "5000");

        manager.add(createService()
            .setInterface(Runnable.class.getName(), updateProperties)
            .setImplementation(DeploymentUpdateTask.class)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
             .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));

        manager.add(createService()
            .setInterface(Runnable.class.getName(), checkProperties)
            .setImplementation(DeploymentCheckTask.class)
            .add(createServiceDependency().setService(Deployment.class).setRequired(true))
            .add(createServiceDependency().setService(Identification.class).setRequired(true))
            .add(createServiceDependency().setService(Discovery.class).setRequired(true))
             .add(createServiceDependency().setService(EventAdmin.class).setRequired(false))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}