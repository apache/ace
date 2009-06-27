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
package org.apache.ace.server.log.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.log.LogSync;
import org.apache.ace.repository.SortedRangeSet;
import org.apache.ace.server.log.store.LogStore;
import org.osgi.service.log.LogService;

public class LogSyncTask implements Runnable, LogSync {

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String COMMAND_RECEIVE = "receive";

    private static final String GWID_KEY = "gwid";
    private static final String FILTER_KEY = "filter";
    private static final String LOGID_KEY = "logid";
    private static final String RANGE_KEY = "range";

    // injected by dependencymanager
    private volatile Discovery m_discovery;
    private volatile LogService m_log;
    private volatile LogStore m_logStore;
    private final String m_endpoint;
    private final String m_name;

    public LogSyncTask(String endpoint, String name) {
        m_endpoint = endpoint;
        m_name = name;
    }

    public void run() {
        try {
            push();
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log (name=" + m_name + ") with remote");
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log (name=" + m_name + ") with remote", e);
        }
    }

    public boolean pull() throws IOException {
        return synchronize(false, true);
    }

    public boolean push() throws IOException {
        return synchronize(true, false);
    }

    public boolean pushpull() throws IOException {
        return synchronize(true, true);
    }

    /**
     * Synchronizes the local store with the discovered remote one.
     * @throws IOException
     */
    private boolean synchronize(boolean push, boolean pull) throws IOException {
        URL host = m_discovery.discover();

        Connection queryConnection = new Connection(new URL(host, m_endpoint + "/" + COMMAND_QUERY));
        InputStream queryInput = queryConnection.getInputStream();

        List<LogDescriptor> localRanges = m_logStore.getDescriptors();
        List<LogDescriptor> remoteRanges = getRanges(queryInput);

        boolean result = false;
        if (push) {
            result |= doPush(host, localRanges, remoteRanges);
        }
        if (pull) {
            result |= doPull(host, localRanges, remoteRanges);
        }
        return result;
    }

    protected boolean doPush(URL host, List<LogDescriptor> localRanges, List<LogDescriptor> remoteRanges) {
        boolean result = false;
        OutputStream sendOutput = null;
        try {
            Connection sendConnection = new Connection(new URL(host, m_endpoint + "/" + COMMAND_SEND));
            sendOutput = sendConnection.getOutputStream();

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendOutput));
            List<LogDescriptor> delta = calculateDelta(localRanges, remoteRanges);
            result = !delta.isEmpty();
            writeDelta(delta, writer);

            sendOutput.flush();
            sendOutput.close();
            sendConnection.close();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log with remote", e);
        }
        finally {
            if (sendOutput != null) {
                try {
                    sendOutput.close();
                }
                catch (Exception ex) {
                    // not much we can do
                }
            }
        }
        return result;
    }

    /**
     * Writes the difference between local and remote to a writer.
     * @param descriptors A list of LogDescriptors that identifies all local log entries that need to be written.
     * @param writer A writer to write to.
     * @throws IOException
     */
    protected void writeDelta(List<LogDescriptor> descriptors, Writer writer) throws IOException {
        for (LogDescriptor l : descriptors) {
            writeLogDescriptor(l, writer);
        }
    }

    /**
     * Writes the LogEvents described by the descriptor to the writer.
     * @param descriptor A LogDescriptor that identifies the events to be written.
     * @param writer A writer to write the events to.
     * @throws IOException Thrown when either the writer goes wrong, or there is a problem
     * communicating with the local log store.
     */
    protected void writeLogDescriptor(LogDescriptor descriptor, Writer writer) throws IOException {
        List<LogEvent> events = m_logStore.get(descriptor);
        for (LogEvent event : events) {
            writer.write(event.toRepresentation() + "\n");
        }
        writer.flush();
    }

    protected boolean doPull(URL host, List<LogDescriptor> localRanges, List<LogDescriptor> remoteRanges) {
        boolean result = false;
        List<LogDescriptor> delta = calculateDelta(remoteRanges, localRanges);
        result = !delta.isEmpty();
        for (LogDescriptor l : delta) {
            Connection receiveConnection;
            try {
                /*
                 * The request currently contains a range. This is not yet supported by the servlet, but it will
                 * simply be ignored.
                 */
                receiveConnection = new Connection(new URL(host, m_endpoint + "/" + COMMAND_RECEIVE + "?" + GWID_KEY +
                    "=" + l.getGatewayID() + "&" + LOGID_KEY + "=" + l.getLogID() + "&" + RANGE_KEY + "=" + l.getRangeSet().toRepresentation()));
                InputStream receiveInput = receiveConnection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(receiveInput));
                readLogs(reader);
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Unable to connect to retrieve log events.", e);
            }
        }
        return result;
    }

    protected void readLogs(BufferedReader reader) {
        try {
            List<LogEvent> events = new ArrayList<LogEvent>();

            String eventString = null;
            while ((eventString = reader.readLine()) != null) {
                try {
                    LogEvent event = new LogEvent(eventString);
                    events.add(event);
                }
                catch (IllegalArgumentException e) {
                    // Just skip this one.
                }
            }
            m_logStore.put(events);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_DEBUG, "Error reading line from reader", e);
        }

    }

    /**
     * Calculates the difference between two lists of <code>LogDescriptor</code>. The result will contain whatever is
     * not in <code>destination</code>, but is in <code>source</code>.
     */
    protected List<LogDescriptor> calculateDelta(List<LogDescriptor> source, List<LogDescriptor> destination) {
        /*
         * For each local descriptor, we try to find a matching remote one. If so, we will synchronize all events
         * that the remote does not have. If we do not find a matching one at all, we send the complete local
         * log.
         */
        List<LogDescriptor> result = new ArrayList<LogDescriptor>();
        for (LogDescriptor s : source) {
            LogDescriptor diffs = s;
            for (LogDescriptor d : destination) {
                if ((s.getLogID() == d.getLogID()) && (s.getGatewayID().equals(d.getGatewayID()))) {
                    SortedRangeSet rangeDiff = d.getRangeSet().diffDest(s.getRangeSet());
                    if (!isEmptyRangeSet(rangeDiff)) {
                        diffs = new LogDescriptor(s.getGatewayID(), s.getLogID(), rangeDiff);
                    }
                    else {
                        diffs = null;
                    }
                }
            }
            if (diffs != null) {
                result.add(diffs);
            }
        }
        return result;
    }

    private boolean isEmptyRangeSet(SortedRangeSet set) {
        return !set.iterator().hasNext();
    }

    protected List<LogDescriptor> getRanges(InputStream stream) throws IOException {
        List<LogDescriptor> result = new ArrayList<LogDescriptor>();
        BufferedReader queryReader = null;
        try {
            queryReader = new BufferedReader(new InputStreamReader(stream));

            for (String line = queryReader.readLine(); line != null; line = queryReader.readLine()) {
                try {
                    result.add(new LogDescriptor(line));
                }
                catch (IllegalArgumentException iae) {
                    throw new IOException("Could not determine highest remote event id, received malformed event range: " + line);
                }
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
        return result;

    }

    // helper class that abstracts handling of a URLConnection somewhat.
    private class Connection {
        private URLConnection m_connection;

        public Connection(URL url) throws IOException {
            m_connection = url.openConnection();
        }

        /**
         * Enables the retrieving of input using this connection and returns an inputstream
         * to the connection.
         *
         * @return Inputstream to the connection.
         * @throws IOException If I/O problems occur.
         */
        public InputStream getInputStream() throws IOException {
            m_connection.setDoInput(true);
            return m_connection.getInputStream();
        }

        /**
         * Enables the sending of output using this connection and returns an outputstream
         * to the connection.
         *
         * @return Outputstream to the connection.
         * @throws IOException If I/O problems occur.
         */
        public OutputStream getOutputStream() throws IOException {
            m_connection.setDoOutput(true);
            return m_connection.getOutputStream();
        }

        /**
         * Should be called when a <code>Connection</code> is used to do a POST (write to it's outputstream)
         * without reading it's inputstream (the response). Calling this will make sure the POST request is sent.
         *
         * @throws IOException If I/O problems occur dealing with the connection.
         */
        public void close() throws IOException {
            m_connection.getContent();
        }

    }

    public String getName() {
        return m_name;
    }
}
