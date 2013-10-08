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
package org.apache.ace.agent.impl;

import static org.apache.ace.agent.impl.ConnectionUtil.close;
import static org.apache.ace.agent.impl.ConnectionUtil.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.agent.RetryAfterException;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;

/**
 * FeedbackChannel implementation
 */
// TODO: decouple from range/log API?
public class FeedbackChannelImpl implements FeedbackChannel {

    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String PARAMETER_TARGETID = "tid";

    private static final String PARAMETER_LOGID = "logid";

    private final AgentContext m_agentContext;
    private final String m_name;
    private final FeedbackStoreManager m_storeManager;

    public FeedbackChannelImpl(AgentContext agentContext, String name) throws IOException {
        m_agentContext = agentContext;
        m_name = name;
        m_storeManager = new FeedbackStoreManager(agentContext, name);
    }

    public void stop() throws IOException {
        m_storeManager.close();
    }

    @Override
    public void sendFeedback() throws RetryAfterException, IOException {
        String identification = getIdentification();
        URL serverURL = getServerURL();

        if (identification == null || serverURL == null) {
            logWarning("No identification or server URL present, cannot send feedback!");
            return;
        }

        ConnectionHandler connectionHandler = getConnectionHandler();
        URLConnection sendConnection = null;
        Writer writer = null;

        try {
            URL sendURL = new URL(serverURL, m_name + "/" + COMMAND_SEND);

            sendConnection = connectionHandler.getConnection(sendURL);
            sendConnection.setDoOutput(true);
            if (sendConnection instanceof HttpURLConnection) {
                ((HttpURLConnection) sendConnection).setChunkedStreamingMode(8192);
            }
            writer = new BufferedWriter(new OutputStreamWriter(sendConnection.getOutputStream()));

            SortedSet<Long> storeIDs = m_storeManager.getAllFeedbackStoreIDs();
            for (Long storeID : storeIDs) {
                URL queryURL = new URL(serverURL, m_name + "/" + COMMAND_QUERY + "?" + PARAMETER_TARGETID + "=" + identification + "&" + PARAMETER_LOGID + "=" + storeID);
                URLConnection queryConnection = connectionHandler.getConnection(queryURL);
                try {
                    synchronizeStore(storeID, queryConnection.getInputStream(), writer);
                }
                catch (IOException e) {
                    handleIOException(queryConnection);
                }
                finally {
                    close(queryConnection);
                }
            }
            writer.flush();

            checkConnectionResponse(sendConnection);
        }
        finally {
            closeSilently(writer);
            close(sendConnection);
        }
    }

    @Override
    public void write(int type, Map<String, String> properties) throws IOException {
        m_storeManager.write(type, properties);
    }

    private ConnectionHandler getConnectionHandler() {
        return m_agentContext.getHandler(ConnectionHandler.class);
    }

    private String getIdentification() {
        return m_agentContext.getHandler(IdentificationHandler.class).getAgentId();
    }

    private LoggingHandler getLoggingHandler() {
        return m_agentContext.getHandler(LoggingHandler.class);
    }

    private URL getServerURL() {
        return m_agentContext.getHandler(DiscoveryHandler.class).getServerUrl();
    }

    private void logWarning(String msg, Object... args) {
        getLoggingHandler().logWarning("feedbackChannel(" + m_name + ")", msg, null, args);
    }

    private Descriptor getQueryDescriptor(InputStream queryInput) throws IOException {
        BufferedReader queryReader = null;
        try {
            queryReader = new BufferedReader(new InputStreamReader(queryInput));
            String rangeString = queryReader.readLine();
            if (rangeString == null) {
                throw new IOException("Could not construct LogDescriptor from stream because stream is empty");
            }
            try {
                return new Descriptor(rangeString);
            }
            catch (IllegalArgumentException e) {
                throw new IOException("Could not determine highest remote event id, received malformed event range (" + rangeString + ")");
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

    private void synchronizeStore(long storeID, InputStream queryInput, Writer sendWriter) throws IOException {
        long highestLocal = m_storeManager.getHighestEventID(storeID);
        if (highestLocal <= 0) {
            // manager is closed...
            return;
        }

        SortedRangeSet localRange = new SortedRangeSet("1-" + highestLocal);
        SortedRangeSet remoteRange = getQueryDescriptor(queryInput).getRangeSet();
        SortedRangeSet delta = remoteRange.diffDest(localRange);
        RangeIterator rangeIterator = delta.iterator();
        if (!rangeIterator.hasNext()) {
            // nothing to sync...
            return;
        }
        long lowest = rangeIterator.next();
        long highest = delta.getHigh();
        if (lowest > highest) {
            // nothing to sync...
            return;
        }

        List<Event> events = m_storeManager.getEvents(storeID, lowest, highestLocal > highest ? highest : highestLocal);
        if (events == null) {
            // manager is closed...
            return;
        }

        String identification = getIdentification();
        for (Event current : events) {
            while ((current.getID() > lowest) && rangeIterator.hasNext()) {
                lowest = rangeIterator.next();
            }
            if (current.getID() == lowest) {
                Event event = new Event(identification, current);
                sendWriter.write(event.toRepresentation());
                sendWriter.write("\n");
            }
        }
    }
}
