package net.luminis.liq.log.listener;

import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.log.Log;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

/**
 * Activator for the bundle that listens to all life-cycle events, and logs them to the
 * log service. The BundleEvents, FrameworkEvents and the events related to
 * Deployment Packages are relevant for the audit log.
 * <p>
 * Furthermore this bundle takes care of the situation when the real log is not
 * yet available within the framework, by using a cache that temporarily stores the
 * log entries, and flushing those when the real log service comes up.
 * BundleEvents and Framework events are always available, but events related to
 * Deployment Packages will only be available when the EventAdmin is present.
 */
public class Activator implements BundleActivator {

    private static final String LOG_NAME = "auditlog";

    private final static String [] topics = new String[] { "org/osgi/service/deployment/*", "net/luminis/liq/deployment/*" };
    private ServiceTracker m_logTracker;
    private ListenerImpl m_listener;

    public synchronized void start(BundleContext context) throws Exception {
        LogProxy logProxy = new LogProxy();
        m_listener = new ListenerImpl(context, logProxy);
        m_listener.startInternal();
        // listen for bundle and framework events
        context.addBundleListener(m_listener);
        context.addFrameworkListener(m_listener);

        // listen for deployment events
        Dictionary dict = new Hashtable();
        dict.put(EventConstants.EVENT_TOPIC, topics);
        context.registerService(EventHandler.class.getName(), m_listener, dict);

        // keep track of when the real log is available
        ServiceTrackerCustomizer logTrackerCust = new LogTracker(context, logProxy);
        m_logTracker = new ServiceTracker(context, context.createFilter("(&(" + Constants.OBJECTCLASS + "=" + Log.class.getName() + ")(name=" + LOG_NAME + "))"), logTrackerCust);
        m_logTracker.open();
    }

    public synchronized void stop(BundleContext context) throws Exception {
        // cleanup
        m_logTracker.close();
        context.removeFrameworkListener(m_listener);
        context.removeBundleListener(m_listener);
        m_listener.stopInternal();
    }
}
