package net.luminis.liq.test.mockautoconf;

import java.util.Properties;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;

public class Activator extends DependencyActivatorBase {

    private static final String PID = "org.osgi.deployment.rp.autoconf";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        MockAutoConf impl = new MockAutoConf();
        Properties props = new Properties();
        props.put(Constants.SERVICE_PID, PID);

        manager.add(createService().setInterface(ResourceProcessor.class.getName(), props)
                .setImplementation(impl));
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // do nothing
    }
}
