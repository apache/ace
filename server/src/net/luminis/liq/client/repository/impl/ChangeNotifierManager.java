package net.luminis.liq.client.repository.impl;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * ChangeNotifierManager handles a number of ChangeNotifiers, so there is only
 * one dependency on EventAdmin; this manager directs all calls from the ChangeNotifiers
 * to the 'real' EventAdmin.
 */
public class ChangeNotifierManager implements EventAdmin {

    private volatile EventAdmin m_eventAdmin; /* Will be injected by dependency manager */

    /**
     * Creates and configures a ChangeNotifier for use with the given topics.
     * @param privateTopicRoot The root of all private topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param publicTopicRoot The root of all public topics; see TopicRoot in the description of {@link ChangeNotifierImpl}.
     * @param entityRoot A class-specific root for the class which will use this ChangeNotifierImpl.
     * @return The newly configured ChangeNotifier.
     */
    public ChangeNotifier getConfiguredNotifier(String privateTopicRoot, String publicTopicRoot, String entityRoot) {
        return new ChangeNotifierImpl(this, privateTopicRoot, publicTopicRoot, entityRoot);
    }

    public void postEvent(Event event) {
        m_eventAdmin.postEvent(event);
    }

    public void sendEvent(Event event) {
        m_eventAdmin.sendEvent(event);
    }

}
