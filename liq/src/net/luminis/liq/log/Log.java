package net.luminis.liq.log;

import java.util.Dictionary;

/**
 * Log interface.
 */
public interface Log {
    /**
     * Logs a new message to the log.
     * 
     * @param type
     * @param properties
     */
    public void log(int type, Dictionary properties);
}
