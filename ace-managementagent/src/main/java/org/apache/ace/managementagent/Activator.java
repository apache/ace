package org.apache.ace.managementagent;

import java.io.IOException;
import java.net.URL;
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
        new org.apache.ace.deployment.service.impl.Activator(),
        new org.apache.ace.deployment.task.Activator(),
        new org.apache.ace.discovery.property.Activator(),
        new org.apache.ace.target.log.Activator(),
        new org.apache.ace.target.log.store.impl.Activator(),
        new org.apache.ace.identification.property.Activator(),
        new org.apache.ace.log.listener.Activator(),
        new org.apache.ace.scheduler.Activator(),
        new org.apache.felix.cm.impl.ConfigurationManager(),
        new org.apache.felix.deploymentadmin.Activator(),
        new org.apache.felix.eventadmin.impl.Activator()
    };
    
    private volatile ConfigurationAdmin m_config;
    
    private boolean m_quiet = Boolean.parseBoolean(System.getProperty("quiet", "false"));
    
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        for (int i = 0; i < m_activators.length; i++) {
            BundleActivator a = m_activators[i];
            // start the bundle unless there is a system property with the same package name as
            // the package that the bundle activator is part of that has the value 'disabled'
            // example use case: turn off the embedded scheduler
            String packageName = a.getClass().getPackage().getName();
            if (!"disabled".equals(System.getProperty(packageName))) {
                a.start(context);
            }
            else if (!m_quiet) {
                System.out.println("Not starting activator " + packageName + ".");
            }
        }
        
        manager.add(createComponent()
            .setImplementation(this)
            .add(createServiceDependency()
                .setService(ConfigurationAdmin.class)
                .setRequired(true)));
    }
    
    public void start() {
        try {
            String syncInterval = System.getProperty("syncinterval", "2000");
            configureFactory("org.apache.ace.target.log.factory", "name", "auditlog");
            configureFactory("org.apache.ace.target.log.store.factory", "name", "auditlog");
            configure("org.apache.ace.scheduler", "org.apache.ace.deployment.task.DeploymentUpdateTask", syncInterval);
            String stopUnaffectedBundles = System.getProperty("org.apache.felix.deploymentadmin.stopunaffectedbundle", "false");
            System.setProperty("org.apache.felix.deploymentadmin.stopunaffectedbundle", stopUnaffectedBundles);
            String agents = System.getProperty("agents");
            if (agents != null) {
                // format: a,b,c;d,e,f
                // a=name, b=id, c=url
                String[] definitions = agents.split(";");
                StringBuffer instances = new StringBuffer();
                
                for (String definition : definitions) {
                    String[] args = definition.split(",");
                    if (args.length != 3) {
                        System.err.println("Each agent definition needs to consist of 3 parts: name, identification and discovery, and not: " + definition);
                        System.exit(20);
                    }
                    String ma = args[0];
                    String id = args[1];
                    String url = args[2];
                    
                    boolean isFileUrl = "file".equals((new URL(url)).getProtocol());
                    
                    configureFactory("org.apache.ace.identification.property.factory", "ma", ma, "targetID", id);
                    configureFactory("org.apache.ace.discovery.property.factory", "ma", ma, "serverURL", url);
                    // if discovery points to the local filesystem, it's no use trying to sync the audit log
                    // to a server (we are keeping the local log in case someone wants to retrieve it)
                    if (!isFileUrl) {
                        configureFactory("org.apache.ace.target.log.sync.factory", "ma", ma, "name", "auditlog");
                    }
                    configureFactory("org.apache.ace.deployment.task.base.factory", "ma", ma);
                    configureFactory("org.apache.ace.deployment.task.default.factory", "ma", ma);
                    configure("org.apache.ace.scheduler", "ma=" + ma + ";name=auditlog", syncInterval);
                    instances.append(
                        "  Instance     : " + ma + "\n" +
                        "    Target ID  : " + id + "\n" +
                        "    Server     : " + url + "\n");
                }

                if (!m_quiet) {
                    System.out.println("Started management agent instances.\n" +
                        instances.toString() +
                        "  Sync interval: " + syncInterval + " ms\n" +
                        "  Unaffected bundles will " + ("false".equals(stopUnaffectedBundles) ? "not " : "") + "be stopped during deployment.");
                }
            }
            else {
                String server = System.getProperty("discovery", "http://localhost:8080");
                configure("org.apache.ace.discovery.property", "serverURL", server);
                boolean isFileUrl = "file".equals((new URL(server)).getProtocol());
                String targetId = System.getProperty("identification", "defaultTargetID");
                configure("org.apache.ace.identification.property", "targetID", targetId);
                if (!isFileUrl) {
                    configureFactory("org.apache.ace.target.log.sync.factory", "name", "auditlog");
                }
                configure("org.apache.ace.scheduler", "auditlog", syncInterval);
                if (!m_quiet) {
                    System.out.println("Started management agent.\n"
                        + "  Target ID    : " + targetId + "\n"
                        + "  Server       : " + server + "\n"
                        + "  Sync interval: " + syncInterval + " ms\n"
                        + "  Unaffected bundles will " + ("false".equals(stopUnaffectedBundles) ? "not " : "")
                        + "be stopped during deployment.");
                }
            }

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
        boolean changed = false;
        for (int i = 0; i < params.length; i += 2) {
            if (!params[i + 1].equals(properties.get(params[i]))) {
                properties.put(params[i], params[i + 1]);
                changed = true;
            }
        }
        if (changed) {
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
            conf.update(properties);
        }
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        for (int i = 0; i < m_activators.length; i++) {
            BundleActivator a = m_activators[i];
            a.stop(context);
        }
    }
}
