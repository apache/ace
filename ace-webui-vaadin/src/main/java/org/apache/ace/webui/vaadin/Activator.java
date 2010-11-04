package org.apache.ace.webui.vaadin;

import java.util.Properties;

import javax.servlet.http.HttpServlet;

import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;

public class Activator extends DependencyActivatorBase {
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createComponent()
            .setImplementation(VaadinResourceHandler.class)
            .add(createServiceDependency()
                .setService(HttpService.class)
                .setRequired(true)
            )
        );
        // register the main application for the ACE UI client
        Properties props = new Properties();
        props.put(HttpConstants.ENDPOINT, "/ace");
        manager.add(createComponent()
            .setInterface(HttpServlet.class.getName(), props)
            .setImplementation(VaadinServlet.class)
        );
    }
    
    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
