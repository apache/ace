package net.luminis.liq.discovery.property;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.discovery.property.constants.DiscoveryConstants;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Simple implementation of the <code>Discovery</code> interface. It 'discovers'
 * the Provisioning Server by implementing the <code>ManagedService</code> and having the
 * location configured by <code>ConfigurationAdmin</code>. If no configuration or a <code>null</code>
 * configuration has been supplied by <code>ConfigurationAdmin</code> the location stored
 * in <code>GatewayConstants.DISCOVERY_DEFAULT_URL</code> will be used.
 */
public class PropertyBasedDiscovery implements Discovery, ManagedService {

    volatile public LogService m_log; /* will be injected by dependencymanager */
    private URL m_serverURL; /* managed by configadmin */

    public synchronized void updated(Dictionary dictionary) throws ConfigurationException {
        try {
            if(dictionary != null) {
                m_serverURL = new URL((String) dictionary.get(DiscoveryConstants.DISCOVERY_URL_KEY));
            }
        }
        catch (MalformedURLException e) {
            throw new ConfigurationException(DiscoveryConstants.DISCOVERY_URL_KEY, "Malformed URL", e);
        }
    }

    public synchronized URL discover() {
        return m_serverURL;
    }

}
