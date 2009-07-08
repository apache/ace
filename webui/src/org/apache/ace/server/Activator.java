package org.apache.ace.server;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {
    private static volatile BundleContext m_context;

    static BundleContext getContext() {
        return m_context;
    }

    @Override
    public void init(BundleContext context, DependencyManager manager)
    throws Exception {
        m_context = context;
    }
    @Override
    public void destroy(BundleContext context, DependencyManager manager)
    throws Exception {
    }

}
