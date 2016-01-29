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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.agent.impl.FeedbackStore.Record;
import org.apache.ace.feedback.Event;

/**
 * This class acts as a factory for retrieving/creating stores and it also is an adapter for the feedbackstore that is
 * currently active.
 * 
 * The filenames backing the feedbackstore are : storename-timestamp.sequencenumber, e.g. feedback-1378716629402.1 When
 * the maximum allowed filesize for a logfile is reached a new file is created : feedback-1378716629402.2
 * 
 * This managerclass takes care of the splitting of logs over multiple files, cleanup of old files, etc.
 */
public class FeedbackStoreManager {
    private static final String DIRECTORY_NAME = "feedback";
    private static final int DEFAULT_STORE_SIZE = 1024 * 1024; // 1 MB
    private static final int DEFAULT_FILE_SIZE = DEFAULT_STORE_SIZE / 10;

    private final AgentContext m_agentContext;
    private final String m_name;
    private final File m_baseDir;
    /** the maximum size of all store files together. */
    private final int m_maxStoreSize;
    /** the maximum size of a single store file. */
    private final int m_maxFileSize;

    private final AtomicBoolean m_closed;
    private final AtomicReference<FeedbackStore> m_currentStoreRef;
    private final SortedMap<Long, SortedSet<Integer>> m_storeFileIdx;

    private final FileFilter m_fileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.getName().startsWith(m_name);
        }
    };

    /**
     * Create and initialize a store based on a default maxFileSize of 1024 kB (=1 MB)
     * 
     * @param agentContext
     *            the agentcontext
     * @param name
     *            the name of the feedbackstore
     */
    public FeedbackStoreManager(AgentContext agentContext, String name) throws IOException {
        this(agentContext, name, DEFAULT_STORE_SIZE, DEFAULT_FILE_SIZE);
    }

    /**
     * Create and initialize a store
     * 
     * @param agentContext
     *            the agent context
     * @param name
     *            the name of the store
     * @param maxStoreSize
     *            the maximum size for this store, in bytes;
     * @param maxFileSize
     *            the maximum size for one file, in bytes.
     */
    public FeedbackStoreManager(AgentContext agentContext, String name, int maxStoreSize, int maxFileSize) throws IOException {
        m_agentContext = agentContext;
        m_name = name;
        m_maxStoreSize = maxStoreSize;
        m_maxFileSize = maxFileSize;

        if (m_maxFileSize > m_maxStoreSize) {
            throw new IllegalArgumentException("Maximum file size cannot exceed maximum store size!");
        }

        m_closed = new AtomicBoolean(false);

        m_baseDir = new File(m_agentContext.getWorkDir(), DIRECTORY_NAME);
        if (!m_baseDir.isDirectory() && !m_baseDir.mkdirs()) {
            throw new IllegalArgumentException("Need valid dir");
        }

        m_currentStoreRef = new AtomicReference<>();
        m_storeFileIdx = new TreeMap<>();

        Pattern p = Pattern.compile(m_name + "-(\\d+).(\\d+)");
        File[] allFiles = m_baseDir.listFiles(m_fileFilter);
        for (File file : allFiles) {
            Matcher m = p.matcher(file.getName());
            if (m.matches()) {
                long storeId = Long.valueOf(m.group(1));
                int fileNumber = Integer.valueOf(m.group(2));

                SortedSet<Integer> storeFileNos = m_storeFileIdx.get(storeId);
                if (storeFileNos == null) {
                    storeFileNos = new TreeSet<>();
                    m_storeFileIdx.put(storeId, storeFileNos);
                }
                storeFileNos.add(fileNumber);
            }
        }

        // Identify and initialize the latest store...
        FeedbackStore store = null;
        try {
            if (m_storeFileIdx.isEmpty()) {
                store = newFeedbackStore();
            }
            else {
                Long lastStoreId = m_storeFileIdx.lastKey();
                Integer fileNo = m_storeFileIdx.get(lastStoreId).last();

                store = createStore(lastStoreId, fileNo);
            }
            setStore(store);
        }
        catch (IOException ex) {
            handleException(store, ex);
        }
    }

    /**
     * Close the current active store to make sure it's nice and consistent on disk
     * 
     * @throws IOException
     *             if something goed wrong
     */
    public void close() throws IOException {
        if (m_closed.compareAndSet(false, true)) {
            setStore(null); // will close automatically the previously set store...
        }
    }

    /**
     * Return a sorted set of all the feedback stores
     * 
     * @return a ordered set of all storeIds, oldest first
     */
    public SortedSet<Long> getAllFeedbackStoreIDs() throws IOException {
        return new TreeSet<>(m_storeFileIdx.keySet());
    }

    /**
     * Return all events in the store in the given range. This list might contains gaps for entries that are not
     * present. From/to are inclusive.
     * 
     * @param storeId
     *            the storeId
     * @param eventId
     *            the start of the range of events
     * @param toEventId
     *            the end of the range of events
     */
    public List<Event> getEvents(long storeID, long fromEventID, long toEventID) throws IOException {
        if (m_closed.get()) {
            return Collections.emptyList();
        }

        FeedbackStore[] stores = getAllStores(storeID);
        try {
            List<Record> records = new ArrayList<>();
            for (FeedbackStore store : stores) {
                try {
                    if (store.getFirstEventID() <= toEventID && store.getLastEventID() >= fromEventID) {
                        records.addAll(store.getRecords(fromEventID, toEventID));
                    }
                }
                catch (Exception ex) {
                    handleException(store, ex);
                }
            }

            // Sort the records by their event ID...
            Collections.sort(records);
            // Unmarshal the records into concrete log events...
            List<Event> result = new ArrayList<>();
            for (Record record : records) {
                result.add(new Event(record.m_entry));
            }
            return result;
        }
        finally {
            closeIfNeeded(stores);
        }
    }

    /**
     * Give the highest eventId that is is present is the specified store
     * 
     * @param the
     *            storeId
     * @return the highest event present in the store, >= 0, or <tt>-1</tt> if this manager is already closed.
     * @throws IOException
     *             in case of I/O problems accessing the store(s).
     */
    public long getHighestEventID(long storeID) throws IOException {
        if (m_closed.get()) {
            return -1L;
        }

        FeedbackStore store = getLastStore(storeID);
        try {
            return store.getLastEventID();
        }
        finally {
            closeIfNeeded(store);
        }
    }

    /**
     * Write to the currently active store
     * 
     * @param type
     *            the type of message
     * @param properties
     *            the properties to be logged
     */
    public void write(int type, Map<String, String> properties) throws IOException {
        if (m_closed.get()) {
            // Nothing we can do here...
            return;
        }

        FeedbackStore currentStore = getCurrentStore();

        try {
            long storeID = currentStore.getId();
            // make sure to continue with the last written event in case we're rotating to a new file...
            long nextEventId = currentStore.getLastEventID() + 1;

            // check if the current store file maximum filesize is reached, if it is the current store should be rotated
            if (isMaximumStoreSizeReached(currentStore)) {
                int newFileNo = getLastLogfileNumber(storeID) + 1;
                currentStore = setStore(createStore(storeID, newFileNo));

                // check if we exceed the maximum allowed store size, if so, we do clean up old files...
                cleanupOldStoreFiles();
            }

            // log the event XXX shouldn't the target ID be filled in?
            Event result = new Event(getTargetID(), storeID, nextEventId, System.currentTimeMillis(), type, properties);

            currentStore.append(result.getID(), result.toRepresentation().getBytes());
        }
        catch (IOException ex) {
            handleException(currentStore, ex);
        }
    }

    void forceCreateNewStore() throws IOException {
        setStore(newFeedbackStore());
    }

    /**
     * @throws IOException
     */
    private void cleanupOldStoreFiles() throws IOException {
        int maxFiles = (int) Math.ceil(m_maxStoreSize / m_maxFileSize);

        File[] storeFiles = getStoreFiles();
        if (storeFiles.length > maxFiles) {
            // we've exceeded our total storage limit...
            int deleteTo = storeFiles.length - maxFiles;
            // delete the files...
            for (int i = 0; i < deleteTo; i++) {
                storeFiles[i].delete();
            }
        }
    }

    /**
     * Close all the feedbackstores if necessary
     * 
     * @param stores
     *            a list of stores
     */
    private void closeIfNeeded(FeedbackStore... stores) {
        for (FeedbackStore store : stores) {
            if (store != getCurrentStore()) {
                try {
                    store.close();
                }
                catch (IOException ex) {
                    // Not much we can do
                }
            }
        }
    }

    /**
     * Create a new feedbackstore with the specified storeId and fileNumber.
     * 
     * @param storeId
     *            the storeId
     * @param fileNumber
     *            the new sequence number for this storeID
     * @return a feedbackstore
     */
    private FeedbackStore createStore(long storeId, int fileNumber) throws IOException {
        File storeFile = new File(m_baseDir, getStoreName(storeId, fileNumber));
        m_storeFileIdx.get(storeId).add(Integer.valueOf(fileNumber));
        return new FeedbackStore(storeFile, storeId);
    }

    /**
     * Return all feedbackstores for a single storeId.
     * 
     * @param storeId
     *            the storeId
     * @return a list of all feedbackstores for this storeId
     */
    private FeedbackStore[] getAllStores(long storeId) throws IOException {
        List<FeedbackStore> stores = new ArrayList<>();

        SortedSet<Integer> storeFileNos = m_storeFileIdx.get(storeId);

        FeedbackStore currentStore = getCurrentStore();
        if (currentStore.getId() == storeId) {
            // The last one is the current store...
            storeFileNos = storeFileNos.headSet(storeFileNos.last());
        }
        for (Integer fileNo : storeFileNos) {
            stores.add(createStore(storeId, fileNo));
        }
        if (currentStore.getId() == storeId) {
            stores.add(currentStore);
        }

        return stores.toArray(new FeedbackStore[stores.size()]);
    }

    /**
     * Get the name of the store for a storeId
     * 
     * @param storeId
     *            the storeId
     * @return the basename of the file
     */
    private String getBaseStoreName(long storeId) {
        return String.format("%s-%d.", m_name, storeId);
    }

    private FeedbackStore getCurrentStore() {
        return m_currentStoreRef.get();
    }

    /**
     * Returns the last file for the specified storeId
     * 
     * @param storeId
     *            the storeID
     * @return the latest (newest) file backing the specified storeID
     */
    private int getLastLogfileNumber(long storeId) throws IOException {
        SortedSet<Integer> fileNos = m_storeFileIdx.get(storeId);
        return (fileNos == null) ? 1 : fileNos.last();
    }

    /**
     * Return the feedbackstore for the specified storeId. If there are multiple files for this storeId the last one is
     * returned
     * 
     * @param the
     *            storeId
     * @return the feedbackstore for that storeID
     */
    private FeedbackStore getLastStore(long storeID) throws IOException {
        FeedbackStore currentStore = getCurrentStore();
        if (currentStore != null && currentStore.getId() == storeID) {
            return currentStore;
        }

        int lastFileNo = getLastLogfileNumber(storeID);
        return createStore(storeID, lastFileNo);
    }

    private int getLogfileNumber(String logfileName, long storeId) {
        String extension = logfileName.replace(m_name + "-" + storeId + ".", "");
        return Integer.parseInt(extension);
    }

    /**
     * Return all store files for this store name, sorted by lastModifiedDate
     * 
     * @return a sorted list of files, oldest file first
     */
    private File[] getStoreFiles() throws IOException {
        File[] files = (File[]) m_baseDir.listFiles(m_fileFilter);
        if (files == null) {
            throw new IOException("Unable to list store files in " + m_baseDir.getAbsolutePath());
        }
        // sort files on storeId and fileNumber
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                long storeId1 = getStoreId(f1);
                long storeId2 = getStoreId(f2);

                int result = (int) (storeId1 - storeId2);
                if (result == 0) {
                    int f1Number = getLogfileNumber(f1.getName(), storeId1);
                    int f2Number = getLogfileNumber(f2.getName(), storeId2);
                    result = f1Number - f2Number;
                }
                return result;
            }
        });
        return files;
    }

    /**
     * Parse the storeId from the specified fileName
     * 
     * @param storeFile
     *            a store file
     * @return the storeId
     */
    private long getStoreId(File storeFile) {
        Pattern p = Pattern.compile(m_name + "-(\\d+)");
        Matcher m = p.matcher(storeFile.getName());
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        throw new RuntimeException("Invalid store file name: " + storeFile.getName());
    }

    /**
     * Get the name of the store for a storeId
     * 
     * @param storeId
     *            the storeId
     * @return the basename of the file
     */
    private String getStoreName(long storeId, int fileNo) {
        return String.format("%s%d", getBaseStoreName(storeId), fileNo);
    }

    private String getTargetID() {
        IdentificationHandler idHandler = m_agentContext.getHandler(IdentificationHandler.class);
        return idHandler.getAgentId();
    }

    /**
     * Handle exceptions. This method will truncate the store from the point the error occurred. If the error occurred
     * in the currently active store it will close the current store and create a new one. The original error is
     * rethrowed.
     * 
     * @param store
     *            the store where the exception happened
     * @param exception
     *            the original exception
     */
    private void handleException(FeedbackStore store, Exception exception) throws IOException {
        logError("Exception caught while accessing feedback channel store #%d", exception, store.getId());
        if (store == getCurrentStore()) {
            setStore(newFeedbackStore()); // XXX
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

    /**
     * Check if the maximum allowed size for the current store file is reached
     * 
     * @return is the maximum reached
     */
    private boolean isMaximumStoreSizeReached(FeedbackStore store) throws IOException {
        return store.getFileSize() >= m_maxFileSize;
    }

    private void logError(String msg, Exception cause, Object... args) {
        m_agentContext.getHandler(LoggingHandler.class).logError("feedbackChannel(" + m_name + ")", msg, cause, args);
    }

    /**
     * Create a new empty feedbackstore with a new storeId.
     * 
     * @return A new feedbackstore with a new storeID
     */
    private FeedbackStore newFeedbackStore() throws IOException {
        long storeId = System.currentTimeMillis();

        String storeFilename;
        File storeFile;
        do {
            storeFilename = getStoreName(storeId, 1);
            storeFile = new File(m_baseDir, storeFilename);
            if (storeFile.createNewFile()) {
                break;
            }
            storeId++;
        }
        while (true);

        m_storeFileIdx.put(storeId, new TreeSet<>(Arrays.asList(1)));

        return new FeedbackStore(storeFile, storeId);
    }

    private FeedbackStore setStore(FeedbackStore store) throws IOException {
        FeedbackStore old;
        do {
            old = m_currentStoreRef.get();
        }
        while (!m_currentStoreRef.compareAndSet(old, store));

        if (old != null) {
            old.close();
        }

        return store;
    }
}
