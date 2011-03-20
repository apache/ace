package org.apache.ace.nodelauncher;

import java.util.Properties;

/**
 * A TargetLauncher starts, stops and interrogates named nodes. These nodes
 * represent running JVMs in some sense; they can be provided by some
 * cloud-provider, or running JVMs on a single machine.<br>
 * <br>
 * It is up to the provider to decide what to run on the given Node. This can be
 * either a single Management Agent, which can be identified by the <code>id</code>,
 * or a Node Manager.
 */
public interface NodeLauncher {
    /**
     * Starts a new node with the given ID. Does not check whether this ID is already in use.
     * @param id A textual ID for the node.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    void start(String id) throws Exception;

    /**
     * Destroys the node with the given ID. Does not check whether this ID actually exists.
     * @param id A textual ID for the node.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    void stop(String id) throws Exception;
    
    /**
     * Retrieves properties from the node. These will include, at least
     * <ul>
     * <li><em>ip</em> The public IP address of the node.</li>
     * </ul>
     * @param id The textual ID for the node.
     * @return the properties of the node, or <code>null</code> if this node cannot be found.
     * @throws Exception Be aware that the implementation may pass through implementation-specific exceptions.
     */
    Properties getProperties(String id) throws Exception;
}
