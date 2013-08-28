package org.apache.ace.agent;

import java.util.Map;

/**
 * Listener interface for consumers that wish to be notified of agent events. This includes events deployment events as
 * defined by the OSGi DeploymentAdmin specification.F
 */
public interface EventListener {

    /**
     * Event callback.
     * 
     * @param topic The topic string
     * @param payload An unmodifiable map
     */
    void handle(String topic, Map<String, String> payload);
}
