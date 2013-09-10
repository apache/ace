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

import static org.apache.ace.agent.impl.ConnectionUtil.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.agent.RetryAfterException;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;

/**
 * FeedbackChannel implementation
 * 
 */
// TODO: rotate/truncate<br/>
// TODO: test(coverage)<br/>
// TODO: decouple from range/log API?
public class FeedbackChannelImpl implements FeedbackChannel {
    /**
     * The general idea is to provide easy access to a file of records. It supports iterating over records both by
     * skipping and by reading. Furthermore, files can be truncated. Most methods will make an effort to reset to the
     * last good record in case of an error -- hence, a call to truncate after an IOException might make the store
     * readable again.
     */
    static class Store {
        private final RandomAccessFile m_store;
        private final long m_id;
        private long m_current;

        /**
         * Create a new File based Store.
         * 
         * @param store
         *            the file to use as backend.
         * @param id
         *            the log id of the store
         * @throws java.io.IOException
         *             in case the file is not rw.
         */
        Store(File store, long id) throws IOException {
            m_store = new RandomAccessFile(store, "rwd");
            m_id = id;
        }

        /**
         * Store the given record data as the next record.
         * 
         * @param entry
         *            the data of the record to store.
         * @throws java.io.IOException
         *             in case of any IO error.
         */
        public void append(long id, byte[] entry) throws IOException {
            long pos = m_store.getFilePointer();
            try {
                m_store.seek(m_store.length());
                m_store.writeLong(id);
                m_store.writeInt(entry.length);
                m_store.write(entry);
                m_store.seek(pos);
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
        }

        /**
         * Release any resources.
         * 
         * @throws java.io.IOException
         *             in case of any IO error.
         */
        public void close() throws IOException {
            m_store.close();
        }

        /**
         * Get the id of the current record.
         * 
         * @return the idea of the current record.
         */
        public long getCurrent() throws IOException {
            long pos = m_store.getFilePointer();
            if (m_store.length() == 0) {
                return 0;
            }
            long result = 0;
            try {
                m_store.seek(m_current);
                result = readCurrentID();
                m_store.seek(pos);
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
            return result;
        }

        /**
         * Get the log id of this store.
         * 
         * @return the log id of this store.
         */
        public long getId() {
            return m_id;
        }

        /**
         * Determine whether there are any records left based on the current postion.
         * 
         * @return <code>true</code> if there are still records to be read.
         * @throws java.io.IOException
         *             in case of an IO error.
         */
        public boolean hasNext() throws IOException {
            return m_store.getFilePointer() < m_store.length();
        }

        /**
         * Make sure the store is readable. As a result, the store is at the end of the records.
         * 
         * @throws java.io.IOException
         *             in case of any IO error.
         */
        public void init() throws IOException {
            reset();
            try {
                while (true) {
                    skip();
                }
            }
            catch (EOFException ex) {
                // done
            }
        }

        @SuppressWarnings("unused")
        public byte[] read() throws IOException {
            long pos = m_store.getFilePointer();
            try {
                if (pos < m_store.length()) {
                    long current = m_store.getFilePointer();
                    long id = m_store.readLong();
                    int next = m_store.readInt();
                    byte[] entry = new byte[next];
                    m_store.readFully(entry);
                    m_current = current;
                    return entry;
                }
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
            return null;
        }

        public long readCurrentID() throws IOException {
            long pos = m_store.getFilePointer();
            try {
                if (pos < m_store.length()) {
                    long id = m_store.readLong();
                    m_store.seek(pos);
                    return id;
                }
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
            return -1;
        }

        /**
         * Reset the store to the beginning of the records
         * 
         * @throws java.io.IOException
         *             in case of an IO error.
         */
        public void reset() throws IOException {
            m_store.seek(0);
            m_current = 0;
        }

        /**
         * Skip the next record if there is any.
         * 
         * @throws java.io.IOException
         *             in case of any IO error or if there is no record left.
         */
        @SuppressWarnings("unused")
        public void skip() throws IOException {
            long pos = m_store.getFilePointer();
            try {
                long id = m_store.readLong();
                int next = m_store.readInt();
                if (m_store.length() < next + m_store.getFilePointer()) {
                    throw new IOException("Unexpected end of file");
                }
                m_store.seek(m_store.getFilePointer() + next);
                m_current = pos;
                pos = m_store.getFilePointer();
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
        }

        /**
         * Try to truncate the store at the current record.
         * 
         * @throws java.io.IOException
         *             in case of any IO error.
         */
        public void truncate() throws IOException {
            m_store.setLength(m_store.getFilePointer());
        }

        private void handle(long pos, IOException exception) throws IOException {
            try {
                m_store.seek(pos);
            }
            catch (IOException ex) {
                // m_log.log(LogService.LOG_WARNING, "Exception during seek!", ex);
            }
            throw exception;
        }
    }

    private static final String DIRECTORY_NAME = "feedback";
    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String PARAMETER_TARGETID = "tid";

    private static final String PARAMETER_LOGID = "logid";

    // bridging to log api
    private static Dictionary<String, String> mapToDictionary(Map<String, String> map) {
        Dictionary<String, String> dictionary = new Hashtable<String, String>();
        for (Entry<String, String> entry : map.entrySet()) {
            dictionary.put(entry.getKey(), entry.getValue());
        }
        return dictionary;
    }

    private final AgentContext m_agentContext;
    private final String m_name;
    private final File m_baseDir;
    private final FileFilter m_fileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.getName().startsWith(m_name);
        }
    };

    private Store m_store = null;

    private long m_highest;

    public FeedbackChannelImpl(AgentContext agentContext, String name) throws IOException {
        m_agentContext = agentContext;
        m_name = name;
        m_baseDir = new File(m_agentContext.getWorkDir(), DIRECTORY_NAME);
        if (!m_baseDir.isDirectory() && !m_baseDir.mkdirs()) {
            throw new IllegalArgumentException("Need valid dir");
        }
        initStore();
    }

    public void closeStore() throws IOException {
        Store store;
        synchronized (m_store) {
            store = m_store;
        }
        store.close();
        m_store = null;
    }

    @Override
    public synchronized void sendFeedback() throws RetryAfterException, IOException {
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
            SortedSet<Long> storeIDs = getStoreIDs();
            for (Long storeID : storeIDs) {
                URL queryURL = new URL(serverURL, m_name + "/" + COMMAND_QUERY + "?" + PARAMETER_TARGETID + "=" + identification + "&" + PARAMETER_LOGID + "=" + storeID);
                URLConnection queryConnection = connectionHandler.getConnection(queryURL);
                try {
                    synchronizeStore(storeID, queryConnection.getInputStream(), writer);
                }
                finally {
                    close(queryConnection);
                }
            }
            writer.flush();

            ConnectionUtil.checkConnectionResponse(sendConnection);
            sendConnection.getContent();
        }
        finally {
            closeSilently(writer);
            close(sendConnection);
        }
    }

    @Override
    public void write(int type, Map<String, String> properties) throws IOException {
        synchronized (m_store) {
            try {
                LogEvent result = new LogEvent(null, m_store.getId(), getNextEventID(), System.currentTimeMillis(), type, mapToDictionary(properties));
                m_store.append(result.getID(), result.toRepresentation().getBytes());
            }
            catch (IOException ex) {
                handleException(m_store, ex);
            }
        }
    }

    private void closeIfNeeded(Store store) {
        if (store != m_store) {
            try {
                store.close();
            }
            catch (IOException ex) {
                // Not much we can do;
            }
        }
    }

    private Store createStore(long storeId) throws IOException {
        return new Store(new File(m_baseDir, getStoreName(storeId)), storeId);
    }

    private ConnectionHandler getConnectionHandler() {
        return m_agentContext.getHandler(ConnectionHandler.class);
    }

    private List<LogEvent> getEvents(long storeID, long fromEventID, long toEventID) throws IOException {
        Store store = getStore(storeID);
        List<LogEvent> result = new ArrayList<LogEvent>();
        try {
            if (store.getCurrent() > fromEventID) {
                store.reset();
            }
            while (store.hasNext()) {
                long eventID = store.readCurrentID();
                if ((eventID >= fromEventID) && (eventID <= toEventID)) {
                    result.add(new LogEvent(new String(store.read())));
                }
                else {
                    store.skip();
                }
            }
        }
        catch (Exception ex) {
            handleException(store, ex);
        }
        finally {
            closeIfNeeded(store);
        }
        return result;
    }

    private long getHighestEventID(long storeID) throws IOException {
        Store store = getStore(storeID);
        try {
            if (m_highest == 0) {
                store.init();
                return (m_highest = store.getCurrent());
            }
            else {
                return m_highest;
            }
        }
        catch (IOException ex) {
            handleException(store, ex);
        }
        finally {
            closeIfNeeded(store);
        }
        return -1;
    }

    private String getIdentification() {
        return m_agentContext.getHandler(IdentificationHandler.class).getAgentId();
    }

    private LoggingHandler getLoggingHandler() {
        return m_agentContext.getHandler(LoggingHandler.class);
    }

    private long getNextEventID() throws IOException {
        return (m_highest = getHighestEventID(m_store.m_id) + 1);
    }

    private LogDescriptor getQueryDescriptor(InputStream queryInput) throws IOException {
        BufferedReader queryReader = null;
        try {
            queryReader = new BufferedReader(new InputStreamReader(queryInput));
            String rangeString = queryReader.readLine();
            if (rangeString == null) {
                throw new IOException("Could not construct LogDescriptor from stream because stream is empty");
            }
            try {
                return new LogDescriptor(rangeString);
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

    private URL getServerURL() {
        return m_agentContext.getHandler(DiscoveryHandler.class).getServerUrl();
    }

    private Store getStore(long storeID) throws IOException {
        if (m_store.getId() == storeID) {
            return m_store;
        }
        return createStore(storeID);
    }

    private File[] getStoreFiles() throws IOException {
        File[] files = (File[]) m_baseDir.listFiles(m_fileFilter);
        if (files == null) {
            throw new IOException("Unable to list store files in " + m_baseDir.getAbsolutePath());
        }
        return files;
    }

    private long getStoreId(String storeName) {
        return Long.parseLong(storeName.replace(m_name + "-", ""));
    }

    private SortedSet<Long> getStoreIDs() throws IOException {
        File[] files = getStoreFiles();
        SortedSet<Long> storeIDs = new TreeSet<Long>();
        for (int i = 0; i < files.length; i++) {
            storeIDs.add(getStoreId(files[i].getName()));
        }
        return storeIDs;
    }

    private String getStoreName(long storeId) {
        return m_name + "-" + storeId;
    }

    private void handleException(Store store, Exception exception) throws IOException {
        logError("Exception caught while accessing feedback channel store #%d", exception, store.getId());

        if (store == m_store) {
            m_store = newFeedbackStore();
        }
        try {
            store.truncate();
        }
        catch (IOException ex) {
            logError("Exception caught while truncating feedback channel store #%d", ex, store.getId());
        }

        try {
            store.close();
        }
        catch (IOException ex) {
            // Not much we can do
        }

        if (exception instanceof IOException) {
            throw (IOException) exception;
        }

        throw new IOException("Unable to read log entry: " + exception.getMessage());
    }

    private void initStore() throws IOException {
        SortedSet<Long> storeIDs = getStoreIDs();
        if (storeIDs.isEmpty()) {
            m_store = newFeedbackStore();
        }
        else {
            m_store = createStore(storeIDs.last());
            try {
                m_store.init();
            }
            catch (IOException ex) {
                handleException(m_store, ex);
            }
        }
    }

    private void logError(String msg, Exception cause, Object... args) {
        getLoggingHandler().logError("feedbackChannel(" + m_name + ")", msg, cause, args);
    }

    private void logWarning(String msg, Object... args) {
        getLoggingHandler().logWarning("feedbackChannel(" + m_name + ")", msg, null, args);
    }

    private Store newFeedbackStore() throws IOException {
        long storeId = System.currentTimeMillis();
        // XXX this can fail in case of high concurrent situations!
        while (!(new File(m_baseDir, getStoreName(storeId))).createNewFile()) {
            storeId++;
        }
        return new Store(new File(m_baseDir, getStoreName(storeId)), storeId);
    }

    private void synchronizeStore(long storeID, InputStream queryInput, Writer sendWriter) throws IOException {
        long highestLocal = getHighestEventID(storeID);
        if (highestLocal == 0) {
            return;
        }
        SortedRangeSet localRange = new SortedRangeSet("1-" + highestLocal);
        SortedRangeSet remoteRange = getQueryDescriptor(queryInput).getRangeSet();
        SortedRangeSet delta = remoteRange.diffDest(localRange);
        RangeIterator rangeIterator = delta.iterator();
        if (!rangeIterator.hasNext()) {
            return;
        }
        String identification = getIdentification();
        long lowest = rangeIterator.next();
        long highest = delta.getHigh();
        if (lowest <= highest) {
            List<LogEvent> events = getEvents(storeID, lowest, highestLocal > highest ? highest : highestLocal);
            Iterator<LogEvent> iter = events.iterator();
            while (iter.hasNext()) {
                LogEvent current = (LogEvent) iter.next();
                while ((current.getID() > lowest) && rangeIterator.hasNext()) {
                    lowest = rangeIterator.next();
                }
                if (current.getID() == lowest) {
                    LogEvent event = new LogEvent(identification, current);
                    sendWriter.write(event.toRepresentation());
                    sendWriter.write("\n");
                }
            }
        }
    }
}
