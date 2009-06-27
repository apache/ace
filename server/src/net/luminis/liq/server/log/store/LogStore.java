package net.luminis.liq.server.log.store;


import java.io.IOException;
import java.util.List;

import net.luminis.liq.log.LogEvent;
import net.luminis.liq.log.LogDescriptor;

/**
 * Log store interface. Implementation of this service interface provide a persisted storage for LogEvent logs.
 */
public interface LogStore {
	
    /**
     * Event topic that indicates a new LogEvent that has been added to the store. The name
     * of the log is available as EVENT_PROP_LOGNAME, the original LogEvent as EVENT_PROP_LOG_EVENT.
     */
    public static final String EVENT_TOPIC = LogStore.class.getName().replace('.', '/') + "/LogEvent";
    
    /**
     * Event property key containing the name of the log on which the LogEvent has been added. 
     */
    public static final String EVENT_PROP_LOGNAME = "name";
    
    /**
     * Event property key containing the LogEvent that has been added. 
     */
    public static final String EVENT_PROP_LOG_EVENT = "logEvent";

    /**
     * Return all events in a given range.
     *
     * @param range the range to filter events by.
     * @return a list of all events in this store that are in the given range.
     * @throws IOException in case of any error.
     */
    public List<LogEvent> get(LogDescriptor range) throws IOException;

    /**
     * Get the range for the given id and the given log.
     *
     * @param gatewayID the id for which to return the log range.
     * @param logID the log id for which to return the range.
     * @return the range for the given id and the given log.
     * @throws IOException in case of any error.
     */
    public LogDescriptor getDescriptor(String gatewayID, long logID) throws IOException;

    /**
     * Store the given events. The implementation does not have to be transactional i.e., it might throw an exception and still
     * store part of the events. However, individual events should be either stored or not.
     *
     * @param events a list of events to store.
     * @throws IOException in case of any error. It might be possible that only part of the events get stored.
     */
    public void put(List<LogEvent> events) throws IOException;

    /**
     * Get the ranges of all logs of the given id.
     *
     * @param gatewayID the id for which to return the log ranges.
     * @return a list of the ranges of all logs for the given id.
     * @throws IOException in case of any error.
     */
    public List<LogDescriptor> getDescriptors(String gatewayID) throws IOException;

    /**
     * Get the ranges of all logs of all ids in this store.
     *
     * @return a list of ranges of all logs for all ids in this store.
     * @throws IOException in case of any error.
     */
    public List<LogDescriptor> getDescriptors() throws IOException;

}
