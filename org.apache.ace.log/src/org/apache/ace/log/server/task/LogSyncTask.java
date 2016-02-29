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
package org.apache.ace.log.server.task;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.amdatu.scheduling.Job;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.discovery.Discovery;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.feedback.LowestID;
import org.apache.ace.log.LogSync;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.range.SortedRangeSet;
import org.osgi.service.log.LogService;

public class LogSyncTask implements Job, LogSync {

    public static enum Mode {
        NONE, PUSH, PULL, PUSHPULL
    }

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String COMMAND_SEND_IDS = "sendids";
    private static final String COMMAND_RECEIVE = "receive";
    private static final String COMMAND_RECEIVE_IDS = "receiveids";
    private static final String TARGETID_KEY = "tid";
    @SuppressWarnings("unused")
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
    private final String m_targetID;
    private final Mode m_dataTransferMode;
    private final Mode m_lowestIDMode;

    public LogSyncTask(String endpoint, String name, Mode dataTransferMode, Mode lowestIDMode) {
    	this(endpoint, name, dataTransferMode, lowestIDMode, null);
    }

    public LogSyncTask(String endpoint, String name, Mode dataTransferMode, Mode lowestIDMode, String targetID) {
        m_endpoint = endpoint;
        m_name = name;
        m_dataTransferMode = dataTransferMode;
        m_lowestIDMode = lowestIDMode;
        m_targetID = targetID;
    }

    public String getName() {
        return m_name;
    }

    public boolean pull() throws IOException {
        return synchronize(false /* push */, true /* pull */);
    }

    public boolean push() throws IOException {
        return synchronize(true /* push */, false /* pull */);
    }

    public boolean pushpull() throws IOException {
        return synchronize(true /* push */, true /* pull */);
    }
    
    public boolean pullIDs() throws IOException {
    	return synchronizeLowestIDs(false, true);
    }

    public boolean pushIDs() throws IOException {
    	return synchronizeLowestIDs(true, false);
    }

    public boolean pushpullIDs() throws IOException {
    	return synchronizeLowestIDs(true, true);
    }
    
    @Override
    public void execute() {
        try {
            switch (m_lowestIDMode) {
	            case NONE:
	            	break;
                case PULL:
                    pullIDs();
                    break;
                case PUSH:
                    pushIDs();
                    break;
                case PUSHPULL:
                    pushpullIDs();
                    break;
            }
        }
        catch (MalformedURLException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_lowestIDMode + ") synchronize IDs (name=" + m_name + ") with remote (malformed URL, incorrect configuration?)", e);
        }
        catch (ConnectException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to (" + m_lowestIDMode + ") synchronize IDs (name=" + m_name + ") with remote (connection refused, remote not up?)", e);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_lowestIDMode + ") synchronize IDs (name=" + m_name + ") with remote", e);
        }

        try {
            switch (m_dataTransferMode) {
	            case NONE:
	            	break;
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
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_dataTransferMode + ") synchronize log (name=" + m_name + ") with remote (malformed URL, incorrect configuration?)", e);
        }
        catch (ConnectException e) {
            m_log.log(LogService.LOG_WARNING, "Unable to (" + m_dataTransferMode + ") synchronize log (name=" + m_name + ") with remote (connection refused, remote not up?)", e);
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (" + m_dataTransferMode + ") synchronize log (name=" + m_name + ") with remote", e);
        }
    }

    /**
     * Calculates the difference between two lists of <code>Descriptor</code>. The result will contain whatever is not
     * in <code>destination</code>, but is in <code>source</code>.
     */
    protected List<Descriptor> calculateDelta(List<Descriptor> source, List<Descriptor> destination) {
        /*
         * For each local descriptor, we try to find a matching remote one. If so, we will synchronize all events that
         * the remote does not have. If we do not find a matching one at all, we send the complete local log.
         */
        List<Descriptor> result = new ArrayList<>();
        for (Descriptor s : source) {
            Descriptor diffs = s;
            for (Descriptor d : destination) {
                if ((s.getStoreID() == d.getStoreID()) && (s.getTargetID().equals(d.getTargetID()))) {
                    SortedRangeSet rangeDiff = d.getRangeSet().diffDest(s.getRangeSet());
                    if (!isEmptyRangeSet(rangeDiff)) {
                        diffs = new Descriptor(s.getTargetID(), s.getStoreID(), rangeDiff);
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

    protected boolean doPull(URL host, List<Descriptor> localRanges, List<Descriptor> remoteRanges) {
        List<Descriptor> delta = calculateDelta(remoteRanges, localRanges);

        boolean result = !delta.isEmpty();
        if (result) {
            for (Descriptor descriptor : delta) {
                InputStream receiveInput = null;
                HttpURLConnection receiveConnection = null;
                try {
                    /*
                     * The request currently contains a range. This is not yet supported by the servlet, but it will
                     * simply be ignored.
                     */
                    URL url = createReceiveURL(host, descriptor);

                    receiveConnection = createConnection(url);
                    receiveInput = receiveConnection.getInputStream();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(receiveInput));
                    try {
                        readLogs(reader);
                    }
                    finally {
                        reader.close();
                    }

                    int rc = receiveConnection.getResponseCode();
                    result = (rc == HttpServletResponse.SC_OK);

                    if (!result) {
                        String msg = receiveConnection.getResponseMessage();
                        m_log.log(LogService.LOG_WARNING, String.format("Could not pull log '%s'. Server response: %s (%d)", m_name, msg, rc));
                    }
                }
                catch (IOException e) {
                    m_log.log(LogService.LOG_ERROR, "Unable to connect to retrieve log events.", e);
                }
                finally {
                    closeSilently(receiveInput);
                    closeSilently(receiveConnection);
                }
            }
        }

        if (result) {
            m_log.log(LogService.LOG_DEBUG, "Pulled log (" + m_name + ") successfully from remote...");
        }

        return result;
    }

    protected boolean doPush(URL host, List<Descriptor> localRanges, List<Descriptor> remoteRanges) {
        List<Descriptor> delta = calculateDelta(localRanges, remoteRanges);
        boolean result = !delta.isEmpty();

        OutputStream sendOutput = null;
        HttpURLConnection sendConnection = null;

        if (result) {
            try {
                sendConnection = createConnection(createURL(host, COMMAND_SEND));
                // ACE-294: enable streaming mode causing only small amounts of memory to be
                // used for this commit. Otherwise, the entire input stream is cached into
                // memory prior to sending it to the server...
                sendConnection.setChunkedStreamingMode(8192);
                sendConnection.setDoOutput(true);

                sendOutput = sendConnection.getOutputStream();

                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendOutput));
                try {
                    writeDelta(delta, writer);
                }
                finally {
                    writer.close();
                }

                // Will cause a flush and reads the response from the server...
                int rc = sendConnection.getResponseCode();
                result = (rc == HttpServletResponse.SC_OK);

                if (!result) {
                    String msg = sendConnection.getResponseMessage();
                    m_log.log(LogService.LOG_WARNING, String.format("Could not push log '%s'. Server response: %s (%d)", m_name, msg, rc));
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_ERROR, "Unable to (fully) synchronize log with remote", e);
            }
            finally {
                closeSilently(sendOutput);
                closeSilently(sendConnection);
            }
        }

        if (result) {
            m_log.log(LogService.LOG_DEBUG, "Pushed log (" + m_name + ") successfully to remote...");
        }

        return result;
    }

    protected List<Descriptor> getRanges(URL host) throws IOException {
        List<Descriptor> result = new ArrayList<>();

        URLConnection queryConnection = null;
        InputStream queryInput = null;
        try {
            queryConnection = createConnection(createURL(host, COMMAND_QUERY));
            queryInput = queryConnection.getInputStream();

            BufferedReader queryReader = new BufferedReader(new InputStreamReader(queryInput));

            for (String line = queryReader.readLine(); line != null; line = queryReader.readLine()) {
                try {
                    result.add(new Descriptor(line));
                }
                catch (IllegalArgumentException iae) {
                    queryReader.close();

                    throw new IOException("Could not determine highest remote event id, received malformed event range: " + line);
                }
            }
        }
        finally {
            closeSilently(queryInput);
            closeSilently(queryConnection);
        }
        return result;

    }

    protected void readLogs(BufferedReader reader) {
        try {
            List<Event> events = new ArrayList<>();

            String eventString = null;
            while ((eventString = reader.readLine()) != null) {
                try {
                    events.add(new Event(eventString));
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
     * Writes the difference between local and remote to a writer.
     * 
     * @param descriptors
     *            A list of Descriptors that identifies all local log entries that need to be written.
     * @param writer
     *            A writer to write to.
     * @throws java.io.IOException
     */
    protected void writeDelta(List<Descriptor> descriptors, Writer writer) throws IOException {
        for (Descriptor l : descriptors) {
            writeDescriptor(l, writer);
        }
        writer.flush();
    }

    /**
     * Writes the Events described by the descriptor to the writer.
     * 
     * @param descriptor
     *            A Descriptor that identifies the events to be written.
     * @param writer
     *            A writer to write the events to.
     * @throws java.io.IOException
     *             Thrown when either the writer goes wrong, or there is a problem communicating with the local log
     *             store.
     */
    protected void writeDescriptor(Descriptor descriptor, Writer writer) throws IOException {
        List<Event> events = m_logStore.get(descriptor);
        for (Event event : events) {
            writer.write(event.toRepresentation() + "\n");
        }
    }

    private void closeSilently(Closeable resource) {
        if (resource != null) {
            try {
                resource.close();
            }
            catch (IOException exception) {
                // Ignore, not much we can do...
            }
        }
    }

    private void closeSilently(URLConnection resource) {
        if (resource instanceof HttpURLConnection) {
            ((HttpURLConnection) resource).disconnect();
        }
    }

    private HttpURLConnection createConnection(URL url) throws IOException {
        return (HttpURLConnection) m_connectionFactory.createConnection(url);
    }

    private URL createReceiveURL(URL host, Descriptor l) throws MalformedURLException {
        return new URL(host, String.format("%s/%s?%s=%s&%s=%s&%s=%s", m_endpoint, COMMAND_RECEIVE, TARGETID_KEY, l.getTargetID(), LOGID_KEY, l.getStoreID(), RANGE_KEY, l.getRangeSet().toRepresentation()));
    }

    private URL createURL(URL host, String command) throws MalformedURLException {
    	if (m_targetID == null) {
    		return new URL(host, m_endpoint + "/" + command);
    	}
    	else {
    		return new URL(host, m_endpoint + "/" + command + "?" + TARGETID_KEY + "=" + m_targetID);
    	}
    }

    private boolean isEmptyRangeSet(SortedRangeSet set) {
        return !set.iterator().hasNext();
    }

    /**
     * Synchronizes the local store with the discovered remote one.
     * 
     * @throws java.io.IOException
     */
    private boolean synchronize(boolean push, boolean pull) throws IOException {
        URL host = m_discovery.discover();

        List<Descriptor> localRanges = m_logStore.getDescriptors();
        List<Descriptor> remoteRanges = getRanges(host);

        boolean result = false;
        if (push) {
            result |= doPush(host, localRanges, remoteRanges);
        }
        if (pull) {
            result |= doPull(host, localRanges, remoteRanges);
        }

        return result;
    }
    
    private boolean synchronizeLowestIDs(boolean push, boolean pull) throws IOException {
        URL host = m_discovery.discover();
    	
        boolean result = false;
        if (push) {
            result |= doPushLowestIDs(host);
        }
        if (pull) {
            result |= doPullLowestIDs(host);
        }

        return result;
    }
    
    protected boolean doPushLowestIDs(URL host) {
    	boolean result = false;
        OutputStream sendOutput = null;
        HttpURLConnection sendConnection = null;

        try {
            sendConnection = createConnection(createURL(host, COMMAND_SEND_IDS));
            // ACE-294: enable streaming mode causing only small amounts of memory to be
            // used for this commit. Otherwise, the entire input stream is cached into
            // memory prior to sending it to the server...
            sendConnection.setChunkedStreamingMode(8192);
            sendConnection.setDoOutput(true);
            sendOutput = sendConnection.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(sendOutput));
            try {
            	for (Descriptor d : (m_targetID == null ? m_logStore.getDescriptors() : m_logStore.getDescriptors(m_targetID))) {
            		long lowestID = m_logStore.getLowestID(d.getTargetID(), d.getStoreID());
            		if (lowestID > 0) {
            			LowestID lid = new LowestID(d.getTargetID(), d.getStoreID(), lowestID);
            			writer.write(lid.toRepresentation() + "\n");
            		}
            	}
            }
            finally {
                writer.close();
            }

            // Will cause a flush and reads the response from the server...
            int rc = sendConnection.getResponseCode();
            result = (rc == HttpServletResponse.SC_OK);

            if (!result) {
                String msg = sendConnection.getResponseMessage();
                m_log.log(LogService.LOG_WARNING, String.format("Could not push lowest IDs '%s'. Server response: %s (%d)", m_name, msg, rc));
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to (fully) push lowest IDs with remote", e);
        }
        finally {
            closeSilently(sendOutput);
            closeSilently(sendConnection);
        }
        if (result) {
            m_log.log(LogService.LOG_DEBUG, "Pushed lowest IDs (" + m_name + ") successfully to remote...");
        }
        return result;
    }
    
    protected boolean doPullLowestIDs(URL host) {
        boolean result = false;
        InputStream receiveInput = null;
        HttpURLConnection receiveConnection = null;
        try {
            URL url = createURL(host, COMMAND_RECEIVE_IDS);

            receiveConnection = createConnection(url);
            receiveInput = receiveConnection.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(receiveInput));
            try {
            	String line;
                while ((line = reader.readLine()) != null) {
                    try {
                    	LowestID lid = new LowestID(line);
                		m_logStore.setLowestID(lid.getTargetID(), lid.getStoreID(), lid.getLowestID());
                    }
                    catch (IllegalArgumentException e) {
                        // Just skip this one.
                    	m_log.log(LogService.LOG_WARNING, "Could not parse incoming line: " + line + " because: " + e.getMessage(), e);
                    }
                }
            }
            catch (IOException e) {
                m_log.log(LogService.LOG_DEBUG, "Error reading line from reader", e);
            }
            finally {
                reader.close();
            }

            int rc = receiveConnection.getResponseCode();
            result = (rc == HttpServletResponse.SC_OK);

            if (!result) {
                String msg = receiveConnection.getResponseMessage();
                m_log.log(LogService.LOG_WARNING, String.format("Could not receive lowest IDs '%s'. Server response: %s (%d)", m_name, msg, rc));
            }
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Unable to connect to receive lowest IDs.", e);
        }
        finally {
            closeSilently(receiveInput);
            closeSilently(receiveConnection);
        }
        if (result) {
            m_log.log(LogService.LOG_DEBUG, "Pulled lowest IDs (" + m_name + ") successfully from remote...");
        }
        return result;
    }
}
