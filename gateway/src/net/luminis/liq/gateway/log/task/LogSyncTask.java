package net.luminis.liq.gateway.log.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.List;

import net.luminis.liq.discovery.Discovery;
import net.luminis.liq.gateway.log.store.LogStore;
import net.luminis.liq.identification.Identification;
import net.luminis.liq.log.LogDescriptor;
import net.luminis.liq.log.LogEvent;
import net.luminis.liq.repository.RangeIterator;
import net.luminis.liq.repository.SortedRangeSet;

import org.osgi.service.log.LogService;

public class LogSyncTask implements Runnable {

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String PARAMETER_GATEWAYID = "gwid";
    private static final String PARAMETER_LOGID = "logid";

    // injected by dependencymanager
    private volatile Discovery m_discovery;
    private volatile Identification m_identification;
    private volatile LogService m_log;
    private volatile LogStore m_LogStore;

    private final String m_endpoint;

    public LogSyncTask(String endpoint) {
        m_endpoint  = endpoint;
    }

    /**
     * Synchronize the log events available remote with the events available locally.
     */
    public void run() {
        String gatewayID = m_identification.getID();
        URL host = m_discovery.discover();

        if (host == null) {
            //expected if there's no discovered
            //ps or relay server
            m_log.log(LogService.LOG_WARNING, "Unable to synchronize log with remote (endpoint=" + m_endpoint + ") - none available");
            return;
        }

        Connection sendConnection = null;
        try {
            sendConnection = new Connection(new URL(host, m_endpoint + "/" + COMMAND_SEND));
            long[] logIDs = m_LogStore.getLogIDs();
            for (int i = 0; i < logIDs.length; i++) {
                Connection queryConnection = new Connection(new URL(host, m_endpoint + "/" + COMMAND_QUERY + "?" + PARAMETER_GATEWAYID + "=" + gatewayID  + "&" + PARAMETER_LOGID + "=" + logIDs[i]));
                // TODO: make sure no actual call is made using sendConnection when there's nothing to sync
                synchronizeLog(logIDs[i], queryConnection.getInputStream(), sendConnection);
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log with remote (endpoint=" + m_endpoint + ")", e);
        }
        finally {
            if (sendConnection != null) {
                sendConnection.close();
            }
        }
    }

    /**
     * Synchronizes a single log (there can be multiple log/logid's per gateway).
     *
     * @param logID ID of the log to synchronize.
     * @param queryInput Stream pointing to a query result for the events available remotely for this log id
     * @param sendConnection.getOutputStream() Stream to write the events to that are missing on the remote side.
     * @throws IOException If synchronization could not be completed due to an I/O failure.
     */
    protected void synchronizeLog(long logID, InputStream queryInput, Connection sendConnection) throws IOException {
        long highestLocal = m_LogStore.getHighestID(logID);
        if (highestLocal == 0) {
            // No events, no need to synchronize
            return;
        }
        SortedRangeSet localRange = new SortedRangeSet("1-" + highestLocal);
        SortedRangeSet remoteRange = getDescriptor(queryInput).getRangeSet();
        SortedRangeSet delta = remoteRange.diffDest(localRange);
        RangeIterator rangeIterator = delta.iterator();
        List events = m_LogStore.get(logID, 1, highestLocal);
        BufferedWriter writer = null;
        writer = new BufferedWriter(new OutputStreamWriter(sendConnection.getOutputStream()));

        while (rangeIterator.hasNext()) {
            // Note the -1: Events are 1-based, but the list is 0-based.
            LogEvent event = (LogEvent) events.get((int) rangeIterator.next() - 1);
            writer.write(event.toRepresentation() + "\n");
        }
        writer.flush();
    }

    /**
     * Retrieves a LogDescriptor object from the specified stream.
     *
     * @param queryInput Stream containing a LogDescriptor object.
     * @return LogDescriptor object reflecting the range contained in the stream.
     * @throws IOException If no range could be determined due to an I/O failure.
     */
    protected LogDescriptor getDescriptor(InputStream queryInput) throws IOException {
        BufferedReader queryReader = null;
        try {
            queryReader = new BufferedReader(new InputStreamReader(queryInput));
            String rangeString = queryReader.readLine();
            if (rangeString != null) {
                try {
                    return new LogDescriptor(rangeString);
                }
                catch (IllegalArgumentException iae) {
                    throw new IOException("Could not determine highest remote event id, received malformed event range (" + rangeString + ")");
                }
            } else {
                throw new IOException("Could not construct LogDescriptor from stream because stream is empty");
            }
        }
        finally {
            if (queryReader != null) {
                try {
                    queryReader.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
    }

}
