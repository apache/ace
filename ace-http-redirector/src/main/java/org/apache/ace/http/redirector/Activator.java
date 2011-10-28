package org.apache.ace.http.redirector;

import java.util.Dictionary;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.Servlet;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase implements ManagedServiceFactory {
    private static final String PID = "org.apache.ace.http.redirector.factory";
    private final ConcurrentHashMap<String, Component> m_servlets = new ConcurrentHashMap<String, Component>();
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setInterface(ManagedServiceFactory.class.getName(), new Properties() {{ put(Constants.SERVICE_PID, PID); }})
            .setImplementation(this)
        );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
    
    public String getName() {
        return "Http Redirector";
    }

    public void updated(String pid, Dictionary properties) throws ConfigurationException {
        Component component = m_servlets.get(pid);
        if (component == null) {
            component = getDependencyManager().createComponent()
                .setInterface(Servlet.class.getName(), properties)
                .setImplementation(new RedirectServlet(properties))
                ;
            m_servlets.put(pid, component);
            getDependencyManager().add(component);
        }
        else {
            RedirectServlet servlet = (RedirectServlet) component.getService();
            if (servlet != null) {
                servlet.update(properties);
            }
        }
    }

    public void deleted(String pid) {
        Component component = m_servlets.remove(pid);
        if (component != null) {
            getDependencyManager().remove(component);
        }
    }
}
