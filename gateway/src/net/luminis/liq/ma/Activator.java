package net.luminis.liq.ma;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

// TODO work in progress
public class Activator implements BundleActivator {
    private net.luminis.liq.identification.property.Activator m_identification;
    private net.luminis.liq.discovery.property.Activator m_discovery;
    private net.luminis.liq.deployment.deploymentadmin.Activator m_deployment;
    private net.luminis.liq.deployment.task.Activator m_task;
    private net.luminis.liq.scheduler.Activator m_scheduler;
    private net.luminis.liq.configurator.Activator m_configurator;
    private net.luminis.liq.gateway.log.store.impl.Activator m_store;
    private net.luminis.liq.gateway.log.Activator m_log;
    private net.luminis.liq.log.listener.Activator m_logListener;

    public void start(BundleContext context) throws Exception {
        m_identification = new net.luminis.liq.identification.property.Activator();
        m_discovery = new net.luminis.liq.discovery.property.Activator();
        m_deployment = new net.luminis.liq.deployment.deploymentadmin.Activator();
        m_task = new net.luminis.liq.deployment.task.Activator();
        m_scheduler = new net.luminis.liq.scheduler.Activator();
        m_configurator = new net.luminis.liq.configurator.Activator();
        m_store = new net.luminis.liq.gateway.log.store.impl.Activator();
        m_log = new net.luminis.liq.gateway.log.Activator();
        m_logListener = new net.luminis.liq.log.listener.Activator();

        m_identification.start(context);
        m_discovery.start(context);
        m_deployment.start(context);
        m_task.start(context);
        m_scheduler.start(context);
        m_configurator.start(context);
        m_store.start(context);
        m_log.start(context);
        m_logListener.start(context);
    }

    public void stop(BundleContext context) throws Exception {
        m_identification.stop(context);
        m_discovery.stop(context);
        m_deployment.stop(context);
        m_task.stop(context);
        m_scheduler.stop(context);
        m_configurator.stop(context);
        m_store.stop(context);
        m_log.stop(context);
        m_logListener.stop(context);
    }
}
