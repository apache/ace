package net.luminis.liq.gateway.log;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.gateway.log.store.LogStore;
import net.luminis.liq.gateway.log.task.LogSyncTask;
import net.luminis.liq.identification.Identification;
import net.luminis.liq.log.Log;
import net.luminis.liq.scheduler.constants.SchedulerConstants;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.apache.felix.dependencymanager.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String LOG_NAME = "name";

    private DependencyManager m_manager;
    private final Map m_logInstances = new HashMap(); // String -> Service
    private final Map m_syncInstances = new HashMap(); // String -> Service
    private volatile LogService m_log;

    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "net.luminis.liq.gateway.log.factory");
        manager.add(createService()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public synchronized void deleted(String pid) {
        Service log = (Service) m_logInstances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
        }
        Service sync = (Service) m_syncInstances.remove(pid);
        if (sync != null) {
            m_manager.remove(sync);
        }
    }

    public String getName() {
        return "Log Factory";
    }

    public synchronized void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Service service = (Service) m_logInstances.get(pid);
        if (service == null) {
            // publish log service
            Properties props = new Properties();
            props.put(LOG_NAME, name);
            Service log = m_manager.createService()
                .setInterface(Log.class.getName(), props)
                .setImplementation(LogImpl.class)
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

            // publish log sync task service
            Dictionary properties = new Properties();
            properties.put(SchedulerConstants.SCHEDULER_DESCRIPTION_KEY, "Task that synchronizes log store with id=" + name + " on the gateway and server");
            properties.put(SchedulerConstants.SCHEDULER_NAME_KEY, name);
            properties.put(SchedulerConstants.SCHEDULER_RECIPE, "2000");
            Service sync = m_manager.createService()
                .setInterface(Runnable.class.getName(), properties)
                .setImplementation(new LogSyncTask(name))
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true))
                .add(createServiceDependency().setService(Discovery.class).setRequired(true))
                .add(createServiceDependency().setService(Identification.class).setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));

            m_logInstances.put(pid, log);
            m_syncInstances.put(pid, sync);
            m_manager.add(log);
            m_manager.add(sync);
        } else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was lready configured: " + name);
        }
    }

}
