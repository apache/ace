/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.log.target.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import org.amdatu.scheduling.Job;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.osgi.service.log.LogService;

// TODO there are two versions of this class around, the other ohne being the server.LogSyncTask,
// and both are fairly similar
public class LogSyncTask implements Job {

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String PARAMETER_TARGETID = "tid";
    private static final String PARAMETER_LOGID = "logid";

    // injected by dependencymanager
    private volatile Discovery m_discovery;
    private volatile Identification m_identification;
    private volatile LogService m_log;
    private volatile LogStore m_LogStore;
    private volatile ConnectionFactory m_connectionFactory;

    private final String m_endpoint;

    public LogSyncTask(String endpoint) {
        m_endpoint = endpoint;
    }

    /**
     * Synchronize the log events available remote with the events available locally.
     */
    @Override
    public void execute() {
        URL host = m_discovery.discover();

        if (host == null) {
            // expected if there's no discovered
            // ps or relay server
            m_log.log(LogService.LOG_WARNING, "Unable to synchronize log with remote (endpoint=" + m_endpoint + ") - none available");
            return;
        }

        if ("file".equals(host.getProtocol())) {
            // if the discovery URL is a file, we cannot sync, so we silently return here
            return;
        }

        String targetId = m_identification.getID();
        URLConnection sendConnection = null;
        try {
            sendConnection = m_connectionFactory.createConnection(new URL(host, m_endpoint + "/" + COMMAND_SEND));
            sendConnection.setDoOutput(true);
            if (sendConnection instanceof HttpURLConnection) {
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into
                // memory prior to sending it to the server...
                ((HttpURLConnection) sendConnection).setChunkedStreamingMode(8192);
            }

            long[] logIDs = m_LogStore.getLogIDs();
            for (int i = 0; i < logIDs.length; i++) {
                URL url = new URL(host, m_endpoint + "/" + COMMAND_QUERY + "?" + PARAMETER_TARGETID + "=" + targetId + "&" + PARAMETER_LOGID + "=" + logIDs[i]);

                URLConnection queryConnection = m_connectionFactory.createConnection(url);
                // TODO: make sure no actual call is made using sendConnection
                // when there's nothing to sync
                synchronizeLog(logIDs[i], queryConnection.getInputStream(), sendConnection);
            }

            // Make sure to send the actual POST request...
            sendConnection.getContent();
        }
        catch (ConnectException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to connect to remote (endpoint=" + m_endpoint + ")");
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log with remote (endpoint=" + m_endpoint + ")", e);
        }
        finally {
            if (sendConnection instanceof HttpURLConnection) {
                ((HttpURLConnection) sendConnection).disconnect();
            }
        }
    }

    /**
     * Synchronizes a single log (there can be multiple log/logid's per target).
     * 
     * @param logID
     *            ID of the log to synchronize.
     * @param queryInput
     *            Stream pointing to a query result for the events available remotely for this log id
     * @param sendConnection
     *            .getOutputStream() Stream to write the events to that are missing on the remote side.
     * @throws java.io.IOException
     *             If synchronization could not be completed due to an I/O failure.
     */
    protected void synchronizeLog(long logID, InputStream queryInput, URLConnection sendConnection) throws IOException {
        long highestLocal = m_LogStore.getHighestID(logID);
        if (highestLocal == 0) {
            // No events, no need to synchronize
            return;
        }

        SortedRangeSet localRange = new SortedRangeSet("1-" + highestLocal);
        SortedRangeSet remoteRange = getDescriptor(queryInput).getRangeSet();
        SortedRangeSet delta = remoteRange.diffDest(localRange);
        RangeIterator rangeIterator = delta.iterator();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendConnection.getOutputStream()));

        if (rangeIterator.hasNext()) {
            long lowest = rangeIterator.next();
            long highest = delta.getHigh();
            if (lowest <= highest) {
                List<Event> events = m_LogStore.get(logID, lowest, highestLocal > highest ? highest : highestLocal);
                for (Event current : events) {
                    while ((current.getID() > lowest) && rangeIterator.hasNext()) {
                        lowest = rangeIterator.next();
                    }
                    if (current.getID() == lowest) {
                        // before we send the LogEvent to the other side, we fill out the
                        // appropriate identification
                        Event event = new Event(m_identification.getID(), current);
                        writer.write(event.toRepresentation() + "\n");
                    }
                }
            }
        }

        writer.flush();
    }

    /**
     * Retrieves a LogDescriptor object from the specified stream.
     * 
     * @param queryInput
     *            Stream containing a LogDescriptor object.
     * @return LogDescriptor object reflecting the range contained in the stream.
     * @throws java.io.IOException
     *             If no range could be determined due to an I/O failure.
     */
    protected Descriptor getDescriptor(InputStream queryInput) throws IOException {
        BufferedReader queryReader = null;
        try {
            queryReader = new BufferedReader(new InputStreamReader(queryInput));
            String rangeString = queryReader.readLine();
            if (rangeString != null) {
                try {
                    return new Descriptor(rangeString);
                }
                catch (IllegalArgumentException iae) {
                    throw new IOException("Could not determine highest remote event id, received malformed event range (" + rangeString + ")");
                }
            }
            else {
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
