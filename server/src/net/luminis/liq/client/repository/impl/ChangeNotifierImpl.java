package net.luminis.liq.client.repository.impl;

import java.util.Properties;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * ChangeNotifierImpl provides a basic implementation of a ChangeNotifier, intended to be used
 * by classes related to the RepositoryAdmin.<br>
 * <br>
 * Topics are built up in the following fashion:
 * <ul>
 * <li><b>...TopicRoot</b> All topics start with a TopicRoot, which is the same for all related classes, and ends with a "/".
 * There can be internal and external topics, hence two TopicRoot parameters in the constructor.</li>
 * <li><b>entityRoot</b> This is followed by a class-specific root, usually consisting of the classname with an added "/".</li>
 * <li>Finally, for each call to <code>notifyChanged</code>, a topic can be specified, which is something like
 * "CHANGED" or "ADDED".</li>
 * </ul>
 */
public class ChangeNotifierImpl implements ChangeNotifier {

    private final EventAdmin m_eventAdmin;
    private final String m_privateTopicRoot;
    private final String m_publicTopicRoot;
    private final String m_entityRoot;

    /**
     * Creates a new ChangeNotifierImpl.
     * @param eventAdmin An EventAdmin to send events to.
     * @param privateTopicRoot The root of all private topics; see TopicRoot in the description of this class.
     * @param publicTopicRoot The root of all public topics; see TopicRoot in the description of this class.
     * @param entityRoot A class-specific root for the class which will use this ChangeNotifierImpl.
     */
    ChangeNotifierImpl(EventAdmin eventAdmin, String privateTopicRoot, String publicTopicRoot, String entityRoot) {
        m_eventAdmin = eventAdmin;
        m_privateTopicRoot = privateTopicRoot;
        m_publicTopicRoot = publicTopicRoot;
        m_entityRoot = entityRoot;
    }

    public void notifyChanged(String topic, Properties props, boolean internalOnly) {
        m_eventAdmin.sendEvent(new Event(m_privateTopicRoot + m_entityRoot + topic, props));
        if (!internalOnly) {
            m_eventAdmin.postEvent(new Event(m_publicTopicRoot + m_entityRoot + topic, props));
        }
    }

    public void notifyChanged(String topic, Properties props) {
        notifyChanged(topic, props, false);
    }

    public String getTopicAll(boolean publicTopic) {
        if (publicTopic) {
            return m_publicTopicRoot + m_entityRoot + "*";
        }
        else {
            return m_privateTopicRoot + m_entityRoot + "*";
        }
    }

}
