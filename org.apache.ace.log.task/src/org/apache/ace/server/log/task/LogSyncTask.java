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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.log.LogSync;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.server.log.store.LogStore;
import org.osgi.service.log.LogService;

public class LogSyncTask implements Runnable, LogSync {

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String COMMAND_RECEIVE = "receive";

    private static final String TARGETID_KEY = "tid";
    private static final String FILTER_KEY = "filter";
    private static final String LOGID_KEY = "logid";
    private static final String RANGE_KEY = "range";

    // injected by dependencymanager
    private volatile Discovery m_discovery;
    private volatile LogService m_log;
    private volatile LogStore m_logStore;
    private volatile ConnectionFactory m_connectionFactory;
    
    private final String m_endpoint;
    private final String m_name;
	private final Mode m_mode;

    public static enum Mode { PUSH, PULL, PUSHPULL };
    
    public LogSyncTask(String endpoint, String name, Mode mode) {
        m_endpoint = endpoint;
        m_name = name;
		m_mode = mode;
    }

    public void run() {
        try {
        	switch (m_mode) {
	        	case PULL:
	        		pull();
	        		break;
	        	case PUSH:
	        		push();
	        		break;
	        	case PUSHPULL:
	        		pushpull();
	        		break;
        	}
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_mode.toString() + ") synchronize log (name=" + m_name + ") with remote");
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_mode.toString() + ") synchronize log (name=" + m_name + ") with remote", e);
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
     * @throws java.io.IOException
     */
    private boolean synchronize(boolean push, boolean pull) throws IOException {
        URL host = m_discovery.discover();

        URLConnection queryConnection = m_connectionFactory.createConnection(new URL(host, m_endpoint + "/" + COMMAND_QUERY));
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
            URLConnection sendConnection = m_connectionFactory.createConnection(new URL(host, m_endpoint + "/" + COMMAND_SEND));
            
            if (sendConnection instanceof HttpURLConnection) {
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into 
                // memory prior to sending it to the server...
                ((HttpURLConnection) sendConnection).setChunkedStreamingMode(8192);
            }
            sendConnection.setDoOutput(true);

            sendOutput = sendConnection.getOutputStream();

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendOutput));
            List<LogDescriptor> delta = calculateDelta(localRanges, remoteRanges);
            result = !delta.isEmpty();
            writeDelta(delta, writer);

            sendOutput.flush();
            sendOutput.close();

            if (sendConnection instanceof HttpURLConnection) {
                HttpURLConnection conn = (HttpURLConnection) sendConnection;
                conn.getContent();
                conn.disconnect();
            }
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
     * @throws java.io.IOException
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
     * @throws java.io.IOException Thrown when either the writer goes wrong, or there is a problem
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
            try {
                /*
                 * The request currently contains a range. This is not yet supported by the servlet, but it will
                 * simply be ignored.
                 */
                URL url = new URL(host, m_endpoint + "/" + COMMAND_RECEIVE + "?" + TARGETID_KEY + "=" + l.getTargetID() + "&" + LOGID_KEY + "=" + l.getLogID() + "&" + RANGE_KEY + "=" + l.getRangeSet().toRepresentation());
                
                URLConnection receiveConnection = m_connectionFactory.createConnection(url);
                InputStream receiveInput = receiveConnection.getInputStream();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(receiveInput));
                readLogs(reader);
                
                if (receiveConnection instanceof HttpURLConnection) {
                    HttpURLConnection conn = (HttpURLConnection) receiveConnection;
                    conn.getContent();
                    conn.disconnect();
                }
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
                if ((s.getLogID() == d.getLogID()) && (s.getTargetID().equals(d.getTargetID()))) {
                    SortedRangeSet rangeDiff = d.getRangeSet().diffDest(s.getRangeSet());
                    if (!isEmptyRangeSet(rangeDiff)) {
                        diffs = new LogDescriptor(s.getTargetID(), s.getLogID(), rangeDiff);
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

    public String getName() {
        return m_name;
    }
}