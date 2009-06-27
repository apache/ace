package net.luminis.liq.scheduler;

import java.util.Properties;

import net.luminis.liq.scheduler.constants.SchedulerConstants;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Activator for the scheduler service. This activator will monitor <code>Runnable</code>s coming available,
 * and if they are intended to be scheduled, gets the necessary information and passes that to
 * the scheduler.
 */
public class Activator extends DependencyActivatorBase {

    private Scheduler m_scheduler;

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_scheduler = new Scheduler();
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, SchedulerConstants.SCHEDULER_PID);
        manager.add(createService()
            .setInterface(ManagedService.class.getName(), props)
            .setImplementation(m_scheduler)
            .add(createServiceDependency()
                .setService(LogService.class).setRequired(false))
            .add(createServiceDependency()
                .setService(Runnable.class).setRequired(false)
                .setAutoConfig(false)
                .setCallbacks(this, "addRunnable", "addRunnable", "removeRunnable")));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }

    /**
     * Handler for both adding and updating runnable service registrations.
     * @throws ConfigurationException Is thrown when the <code>SCHEDULER_RECIPE</code> contained in <code>ref</code>'s
     * service dictionary cannot be parsed by the scheduler.
     */
    public void addRunnable(ServiceReference ref, Runnable task) throws ConfigurationException {
        String name = (String) ref.getProperty(SchedulerConstants.SCHEDULER_NAME_KEY);
        if (name != null) {
            String description = (String) ref.getProperty(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY);
            Object recipe = ref.getProperty(SchedulerConstants.SCHEDULER_RECIPE);
            boolean recipeOverride = Boolean.valueOf((String) ref.getProperty(SchedulerConstants.SCHEDULER_RECIPE_OVERRIDE)).booleanValue();
            m_scheduler.addRunnable(name, task, description, recipe, recipeOverride);
        }
    }

    public synchronized void removeRunnable(ServiceReference ref, Runnable task) {
        String name = (String) ref.getProperty(SchedulerConstants.SCHEDULER_NAME_KEY);
        if (name != null) {
            m_scheduler.removeRunnable(name);
        }
    }

}