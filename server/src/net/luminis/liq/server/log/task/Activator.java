package net.luminis.liq.server.log.task;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.log.LogSync;
import net.luminis.liq.server.log.store.LogStore;

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
    private final Map<String, Service> m_instances = new HashMap<String, Service>();
    private volatile LogService m_log;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "net.luminis.liq.server.log.task.factory");
        manager.add(createService()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public void deleted(String pid) {
        Service service = m_instances.remove(pid);
        if (service != null) {
            m_manager.remove(service);
        }
    }

    public String getName() {
        return "Log Sync Task Factory";
    }

    @SuppressWarnings("unchecked")
    public synchronized void updated(String pid, Dictionary dict) throws ConfigurationException {
        String name = (String) dict.get(LOG_NAME);
        if ((name == null) || "".equals(name)) {
            throw new ConfigurationException(LOG_NAME, "Log name has to be specified.");
        }

        Service service = m_instances.get(pid);
        if (service == null) {
            Properties props = new Properties();
            props.put(LOG_NAME, name);
            props.put("taskName", LogSyncTask.class.getName());
            props.put("description", "Syncs log (name=" + name + ") with a server.");
            service = m_manager.createService()
                .setInterface(new String[] { Runnable.class.getName(), LogSync.class.getName() }, props)
                .setImplementation(new LogSyncTask(name, name))
                .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=" + name + "))").setRequired(true))
                .add(createServiceDependency().setService(Discovery.class).setRequired(true))
                .add(createServiceDependency().setService(LogService.class).setRequired(false));
            m_instances.put(pid, service);
            m_manager.add(service);
        } else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }
}
