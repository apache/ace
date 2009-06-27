package net.luminis.liq.server.action.popupmessage;

import java.util.Properties;

import net.luminis.liq.server.action.Action;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;

public class Activator extends DependencyActivatorBase {

    @Override
    public void init(BundleContext context, DependencyManager manager) {
        Properties props = new Properties();
        props.put(Action.ACTION_NAME, PopupMessageAction.NAME);
        manager.add(createService()
            .setInterface(Action.class.getName(), props)
            .setImplementation(PopupMessageAction.class)
            );
    }

    @Override
    public void destroy(BundleContext context, DependencyManager manager) {
        // do nothing
    }
}
