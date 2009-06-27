package net.luminis.liq.server.log.store.impl;

import java.io.File;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.luminis.liq.server.log.store.LogStore;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.apache.felix.dependencymanager.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {

    private static final String LOG_NAME = "name";
    private DependencyManager m_manager;
    private final Map<String, Service> m_instances = new HashMap<String, Service>();
    private BundleContext m_context;
    private volatile LogService m_log;

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_context = context;
        m_manager = manager;
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, "net.luminis.liq.server.log.store.factory");
        manager.add(createService()
            .setInterface(ManagedServiceFactory.class.getName(), props)
            .setImplementation(this)
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }

    public void deleted(String pid) {
        Service log = m_instances.remove(pid);
        if (log != null) {
            m_manager.remove(log);
            delete(new File(m_context.getDataFile(""), pid));
        }
    }

    private void delete(File root) {
        if (root.isDirectory()) {
            for (File file : root.listFiles()) {
                delete(file);
            }
        }
        root.delete();
    }

    public String getName() {
        return "Log Store Factory";
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
            File baseDir = new File(m_context.getDataFile(""), pid);
            service = m_manager.createService()
                .setInterface(LogStore.class.getName(), props)
                .setImplementation(new LogStoreImpl(baseDir, name))
                .add(createServiceDependency().setService(EventAdmin.class).setRequired(false));
            m_instances.put(pid, service);
            m_manager.add(service);
        } else {
            m_log.log(LogService.LOG_INFO, "Ignoring configuration update because factory instance was already configured: " + name);
        }
    }

}
