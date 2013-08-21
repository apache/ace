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
import java.net.ConnectException;
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
import org.apache.ace.agent.FeedbackChannel;
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

    private static final String DIRECTORY_NAME = "feedback";
    private static final String COMMAND_QUERY = "query";
    private static final String COMMAND_SEND = "send";
    private static final String PARAMETER_TARGETID = "tid";
    private static final String PARAMETER_LOGID = "logid";

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
        if (!m_baseDir.isDirectory() && !m_baseDir.mkdirs())
            throw new IllegalArgumentException("Need valid dir");
        initStore();
    }

    @Override
    public synchronized void sendFeedback() throws RetryAfterException, IOException {
        String identification = getIdentification();
        URL serverURL = getServerURL();
        if (identification == null || serverURL == null)
            return;
        URLConnection sendConnection = null;
        Writer writer = null;
        try {
            URL sendURL = new URL(serverURL, m_name + "/" + COMMAND_SEND);
            sendConnection = getConnectionHandler().getConnection(sendURL);
            sendConnection.setDoOutput(true);
            if (sendConnection instanceof HttpURLConnection)
                ((HttpURLConnection) sendConnection).setChunkedStreamingMode(8192);
            writer = new BufferedWriter(new OutputStreamWriter(sendConnection.getOutputStream()));
            SortedSet<Long> storeIDs = getStoreIDs();
            for (Long storeID : storeIDs) {
                URL queryURL = new URL(serverURL, m_name + "/" + COMMAND_QUERY + "?" + PARAMETER_TARGETID + "=" + identification + "&" + PARAMETER_LOGID + "=" + storeID);
                URLConnection queryConnection = getConnectionHandler().getConnection(queryURL);
                synchronizeStore(storeID, queryConnection.getInputStream(), writer);
            }
            writer.flush();
            sendConnection.getContent();
        }
        catch (ConnectException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (writer != null)
                writer.close();
            if (sendConnection instanceof HttpURLConnection)
                ((HttpURLConnection) sendConnection).disconnect();
        }
    }

    @Override
    public synchronized void write(int type, Map<String, String> properties) throws IOException {
        try {
            LogEvent result = new LogEvent(null, m_store.getId(), getNextEventID(), System.currentTimeMillis(), type, mapToDictionary(properties));
            m_store.append(result.getID(), result.toRepresentation().getBytes());
        }
        catch (IOException ex) {
            handleException(m_store, ex);
        }
    }

    // TODO Is this called?
    public synchronized void closeStore() throws IOException {
        m_store.close();
        m_store = null;
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

    private void synchronizeStore(long storeID, InputStream queryInput, Writer sendWriter) throws IOException {
        long highestLocal = getHighestEventID(storeID);
        if (highestLocal == 0)
            return;
        SortedRangeSet localRange = new SortedRangeSet("1-" + highestLocal);
        SortedRangeSet remoteRange = getQueryDescriptor(queryInput).getRangeSet();
        SortedRangeSet delta = remoteRange.diffDest(localRange);
        RangeIterator rangeIterator = delta.iterator();
        if (!rangeIterator.hasNext())
            return;
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

    private Store newFeedbackStore() throws IOException {
        long storeId = System.currentTimeMillis();
        while (!(new File(m_baseDir, getStoreName(storeId))).createNewFile()) {
            storeId++;
        }
        return new Store(new File(m_baseDir, getStoreName(storeId)), storeId);
    }

    private Store createStore(long storeId) throws IOException {
        return new Store(new File(m_baseDir, getStoreName(storeId)), storeId);
    }

    private String getStoreName(long storeId) {
        return m_name + "-" + storeId;
    }

    private long getStoreId(String storeName) {
        return Long.parseLong(storeName.replace(m_name + "-", ""));
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

    private void handleException(Store store, Exception exception) throws IOException {
        // System.err.println(LogService.LOG_WARNING, "Exception accessing the log: "
        // + store.getId(), exception);
        if (store == m_store)
            m_store = newFeedbackStore();

        try {
            store.truncate();
        }
        catch (IOException ex) {
            // m_log.log(LogService.LOG_WARNING, "Exception during truncate: "
            // + store.getId(), ex);
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
        throw new IOException("Unable to read log entry: "
            + exception.getMessage());
    }

    private File[] getStoreFiles() throws IOException {
        File[] files = (File[]) m_baseDir.listFiles(m_fileFilter);
        if (files == null)
            throw new IOException("Unable to list store files in " + m_baseDir.getAbsolutePath());
        return files;
    }

    private SortedSet<Long> getStoreIDs() throws IOException {
        File[] files = getStoreFiles();
        SortedSet<Long> storeIDs = new TreeSet<Long>();
        for (int i = 0; i < files.length; i++)
            storeIDs.add(getStoreId(files[i].getName()));
        return storeIDs;
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

    private Store getStore(long storeID) throws IOException {
        if (m_store.getId() == storeID) {
            return m_store;
        }
        return createStore(storeID);
    }

    private long getNextEventID() throws IOException {
        return (m_highest = getHighestEventID(m_store.m_id) + 1);
    }

    private ConnectionHandler getConnectionHandler() {
        return m_agentContext.getConnectionHandler();
    }

    private String getIdentification() {
        return m_agentContext.getIdentificationHandler().getAgentId();
    }

    private URL getServerURL() {
        return m_agentContext.getDiscoveryHandler().getServerUrl();
    }

    // bridging to log api
    private static Dictionary<String, String> mapToDictionary(Map<String, String> map) {
        Dictionary<String, String> dictionary = new Hashtable<String, String>();
        for (Entry<String, String> entry : map.entrySet()) {
            dictionary.put(entry.getKey(), entry.getValue());
        }
        return dictionary;
    }

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
         * Determine whether there are any records left based on the current postion.
         * 
         * @return <code>true</code> if there are still records to be read.
         * @throws java.io.IOException
         *             in case of an IO error.
         */
        public boolean hasNext() throws IOException {
            return m_store.getFilePointer() < m_store.length();
        }

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

        /**
         * Skip the next record if there is any.
         * 
         * @throws java.io.IOException
         *             in case of any IO error or if there is no record left.
         */
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
                long current = m_store.getFilePointer();
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
         * Try to truncate the store at the current record.
         * 
         * @throws java.io.IOException
         *             in case of any IO error.
         */
        public void truncate() throws IOException {
            m_store.setLength(m_store.getFilePointer());
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
}
