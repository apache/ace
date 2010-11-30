package org.apache.ace.managementagent;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Properties;

import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class Activator extends DependencyActivatorBase {
    private BundleActivator[] m_activators = new BundleActivator[] {
        new org.apache.ace.deployment.deploymentadmin.Activator(),
        new org.apache.ace.deployment.task.Activator(),
        new org.apache.ace.discovery.property.Activator(),
        new org.apache.ace.gateway.log.Activator(),
        new org.apache.ace.gateway.log.store.impl.Activator(),
        new org.apache.ace.identification.property.Activator(),
        new org.apache.ace.log.listener.Activator(),
        new org.apache.ace.scheduler.Activator(),
        new org.apache.felix.cm.impl.ConfigurationManager(),
        new org.apache.felix.deploymentadmin.Activator(),
        new org.apache.felix.eventadmin.impl.Activator()
    };
    
    private volatile ConfigurationAdmin m_config;
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        for (int i = 0; i < m_activators.length; i++) {
            BundleActivator a = m_activators[i];
            a.start(context);
//            if (a instanceof DependencyActivatorBase) {
//                DependencyActivatorBase dab = (DependencyActivatorBase) a;
//                dab.init(context, manager);
//            }
//            else {
//                a.start(context);
//            }
        }
        
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(ConfigurationAdmin.class)
                .setRequired(true)));
    }
    
    public void start() {
        try {
            configure("org.apache.ace.discovery.property", "serverURL", System.getProperty("discovery", "http://localhost:8080"));
            configure("org.apache.ace.identification.property", "gatewayID", System.getProperty("identification", "configuredGatewayID"));
            configure("org.apache.ace.scheduler", "auditlog", System.getProperty("syncinterval", "2000"), "org.apache.ace.deployment.task.DeploymentUpdateTask", System.getProperty("syncinterval", "2000"));
            configureFactory("org.apache.ace.gateway.log.factory", "name", "auditlog");
            configureFactory("org.apache.ace.gateway.log.store.factory", "name", "auditlog");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configure(String pid, String... params) throws IOException {
        Configuration conf = m_config.getConfiguration(pid, null);
        Dictionary properties = conf.getProperties();
        if (properties == null) {
            properties = new Properties();
        }
        System.out.println("props=" + properties);
        boolean changed = false;
        for (int i = 0; i < params.length; i += 2) {
            System.out.println("key=" + params[i] + " value=" + params[i + 1]);
            if (!params[i + 1].equals(properties.get(params[i]))) {
                properties.put(params[i], params[i + 1]);
                changed = true;
            }
        }
        if (changed) {
            System.out.println("Updating " + pid);
            conf.update(properties);
        }
    }

    private void configureFactory(String pid, String... params) throws IOException {
        Configuration conf = m_config.createFactoryConfiguration(pid, null);
        Dictionary properties = conf.getProperties();
        if (properties == null) {
            properties = new Properties();
        }
        boolean changed = false;
        for (int i = 0; i < params.length; i += 2) {
            if (!params[i + 1].equals(properties.get(params[i]))) {
                properties.put(params[i], params[i + 1]);
                changed = true;
            }
        }
        if (changed) {
            System.out.println("Updating factory " + pid);
            conf.update(properties);
        }
    }
    
    public void stop() {
        
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        for (int i = 0; i < m_activators.length; i++) {
            BundleActivator a = m_activators[i];
            a.stop(context);
//            if (a instanceof DependencyActivatorBase) {
//                DependencyActivatorBase dab = (DependencyActivatorBase) a;
//                dab.destroy(context, manager);
//            }
//            else {
//                a.stop(context);
//            }
        }
    }
}
