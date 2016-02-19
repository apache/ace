package org.apache.ace.http.context;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;

import java.util.Properties;

import org.apache.ace.http.HttpConstants;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {

    private static final String PID = "org.apache.ace.http.context";
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties props = new Properties();
        props.put(HTTP_WHITEBOARD_CONTEXT_NAME, HttpConstants.ACE_WHITEBOARD_CONTEXT_NAME);
        manager.add(createComponent()
            .setInterface(ServletContextHelper.class.getName(), props)
            .setImplementation(new AceServletContextHelper(context.getBundle()))
            .add(createConfigurationDependency().setPid(PID))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
            );
    }

}
