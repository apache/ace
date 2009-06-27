package net.luminis.liq.identification.property;

import java.util.Dictionary;

import net.luminis.liq.identification.Identification;
import net.luminis.liq.identification.property.constants.IdentificationConstants;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Simple implementation of the <code>Identification</code> interface. Because
 * a gateway identification should not change during it's lifetime the user of this
 * implementation should set the ID only once.
 */
public class PropertyBasedIdentification implements ManagedService, Identification {
    private volatile LogService m_log;
    private String m_gatewayID;

    public synchronized String getID() {
        return m_gatewayID;
    }

    public void updated(Dictionary dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String id = (String) dictionary.get(IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY);
            if ((id == null) || (id.length() == 0)) {
                // illegal config
                throw new ConfigurationException(IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, "Illegal gateway ID supplied");
            }
            if (m_gatewayID != null) {
                m_log.log(LogService.LOG_WARNING, "Gateway ID is being changed from " + m_gatewayID + " to " + id);
            }
            // legal config, set configuration
            synchronized (this) {
                m_gatewayID = id;
            }
        }
    }
}
