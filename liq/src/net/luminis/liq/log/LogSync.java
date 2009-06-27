package net.luminis.liq.log;

import java.io.IOException;

/**
 * Log synchronizing interface. It is intended to give direct access to the synchronizing
 * possibilities of the server side log.
 */
public interface LogSync {

    /**
     * Pushes local changes and updates them in the remote repository.
     * @throws IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean push() throws IOException;
    
    /**
     * Pulls remote changes and updates them in the local repository.
     * @throws IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean pull() throws IOException;
    
    /**
     * Both pushes and pulls changes, and updates them in the both repositories.
     * @throws IOException when there is an error communicating with the server.
     * @return <code>true</code> when changes have been made the local log store,
     * <code>false</code> otherwise.
     */
    public boolean pushpull() throws IOException;
    
    /**
     * Returns the name of the log 'channel' this log sync task is assigned to.
     * 
     * @return The name of the log 'channel' this log sync task is assigned to.
     */
    public String getName();
}
