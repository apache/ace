package net.luminis.liq.deployment.streamgenerator.impl;

import net.luminis.liq.deployment.provider.DeploymentProvider;
import net.luminis.liq.deployment.streamgenerator.StreamGenerator;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        manager.add(createService()
            .setInterface(StreamGenerator.class.getName(), null)
            .setImplementation(StreamGeneratorImpl.class)
            .add(createServiceDependency()
                .setService(DeploymentProvider.class)
                .setRequired(true)
                )
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
    }
}
