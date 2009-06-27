package net.luminis.liq.client.repository.stateful;

import java.util.Map;

import net.luminis.liq.client.repository.ObjectRepository;

/**
 * Represents a repository of <ode>StatefulGatewayObject</code>'s.
 */
public interface StatefulGatewayRepository extends ObjectRepository<StatefulGatewayObject> {

    /**
     * Registers a gateway with given attributes. This will result in the creation
     * of a <code>GatewayObject</code> in the <code>GatewayRepository</code>, and
     * the creation of a <code>StatefulGatewayObject</code>, which will also be
     * returned.
     * @param attributes The attributes to create the <code>GatewayObject</code> with.
     * @return The newly registered gateway object.
     */
    public StatefulGatewayObject preregister(Map<String, String> attributes, Map<String, String> tags);

    /**
     * Unregisters a gateway, removing it from the <code>GatewayRepository</code>. Note
     * that a <code>StatefulGatewayObject</code> might stay around if it is backed
     * by audit log entries. If the given ID is not that of an existing <code>GatewayObject</code>,
     * an <code>IllegalArgumentException</code> will be thrown.
     * @param gatewayID A string representing a gateway ID.
     */
    public void unregister(String gatewayID);

    /**
     * Explicitly instruct the <code>StatefulGatewayRepository</code> to update
     * its contents; for instance, after syncing the audit log.
     */
    public void refresh();

}
