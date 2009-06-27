package net.luminis.liq.gateway.log.store;

import java.io.IOException;
import java.util.Dictionary;
import java.util.List;

import net.luminis.liq.log.LogEvent;

/**
 * Server log store interface for the gateways. Implementations of this service interface provide a persisted storage for
 * log data.
 */
public interface LogStore {

    /**
     * Create a new event out of the given type and properties. Write it to the store and return it.
     *
     * @param type the type of the event.
     * @param props the properties of the event.
     * @return the created event that has been persisted.
     * @throws IOException in case of any IO error.
     */
    public LogEvent put(int type, Dictionary props) throws IOException;

    /**
     * Get all events in the given log.
     *
     * @param logID the id of the log.
     * @return a list of LogEvent's that are currently in the log of the given logID.
     * @throws IOException in case of any IO error.
     */
    public List/*<LogEvent>*/get(long logID) throws IOException;

    /**
     * Get the events in the given log that are in the range of the given lower and upper bound.
     *
     * @param logID the id of the log.
     * @param from the lower bound.
     * @param to the upper bound.
     * @return a list of LogEvent's that are currently in the log of the given logID and have an id in the range of the given
     *         bounds.
     * @throws IOException in case of any IO error.
     */
    public List/*<LogEvent>*/get(long logID, long from, long to) throws IOException;

    /**
     * Get the the highest id of any LogEvent entry in the given log.
     *
     * @param logID the id of the log.
     * @return the id of the highest LogEvent entry in the given log.
     * @throws IOException in case of any IO error.
     */
    public long getHighestID(long logID) throws IOException;

    /**
     * Get the ids of all available logs in this store.
     *
     * @return an array of the ids of all available logs in this store.
     * @throws IOException in case of any IO error.
     */
    public long[] getLogIDs() throws IOException;
}
