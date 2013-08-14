package org.apache.ace.agent.impl;

import org.apache.ace.agent.IdentificationHandler;

/**
 * Default identification handler that reads the identity from the configuration using key
 * {@link IDENTIFICATION_CONFIG_KEY}.
 * 
 */
public class IdentificationHandlerImpl implements IdentificationHandler {

    /**
     * Configuration key for the default identification handler. The value must be a single file-system and URL safe
     * string.
     */
    // TODO move to and validate in configuration handler?
    public static final String IDENTIFICATION_CONFIG_KEY = "agent.discovery";

    private final AgentContext m_agentContext;

    public IdentificationHandlerImpl(AgentContext agentContext) {
        m_agentContext = agentContext;
    }

    // TODO add a default fallback?
    @Override
    public String getIdentification() {
        String configValue = m_agentContext.getConfigurationHandler().getMap().get(IDENTIFICATION_CONFIG_KEY);
        if (configValue == null)
            return null;
        configValue = configValue.trim();
        if (configValue.equals(""))
            return null;
        return configValue;
    }
}
