package net.luminis.liq.discovery;

import java.net.URL;

/**
 * The <code>Discovery</code> interface can be used to locate the Provisioning Server.
 */
public interface Discovery {

    /**
     * Discovers the location of the Provisioning Server.
     *
     * @return URL pointing to the base address of the Provisioning Server
     */
    public URL discover();

}
