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
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.LoggingHandler;
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

    // This is the number of files the maxFileSize of the is split over
    private static final int NUMBER_OF_FILES = 10;
    private static final String DIRECTORY_NAME = "feedback";

    private final AgentContext m_agentContext;
    private final String m_name;
    private final File m_baseDir;
    private final int m_maxFileSize;

    private FeedbackStore m_currentStore;
    private long m_highest;

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
        this(agentContext, name, 1024 * 1024);
    }

    /**
     * Create and initialize a store
     * 
     * @param agentContext
     *            the agentcontext
     * @param name
     *            the name of the feedbackstore
     * @param maxFileSize
     *            the maximum size for this feedbackstore in kB
     */
    public FeedbackStoreManager(AgentContext agentContext, String name, int maxFileSize) throws IOException {
        m_agentContext = agentContext;
        m_name = name;
        m_maxFileSize = maxFileSize;
        m_baseDir = new File(m_agentContext.getWorkDir(), DIRECTORY_NAME);
        if (!m_baseDir.isDirectory() && !m_baseDir.mkdirs()) {
            throw new IllegalArgumentException("Need valid dir");
        }

        // Identify and initialize the latest store
        SortedSet<Long> storeIDs = getAllFeedbackStoreIDs();
        if (storeIDs.isEmpty()) {
            m_currentStore = newFeedbackStore();
        }
        else {
            m_currentStore = getLastStore(storeIDs.last());
            try {
                m_currentStore.init();
            }
            catch (IOException ex) {
                handleException(m_currentStore, ex);
            }
        }
    }

    /**
     * Close the current active store to make sure it's nice and consistent on disk
     * 
     * @throws IOException
     *             if something goed wrong
     */
    public void close() throws IOException {
        if (m_currentStore != null) {
            m_currentStore.close();
        }
        m_currentStore = null;
    }

    /**
     * Return a sorted set of all the feedback stores
     * 
     * @return a ordered set of all storeIds, oldest first
     */
    public SortedSet<Long> getAllFeedbackStoreIDs() throws IOException {
        File[] files = getStoreFiles();
        SortedSet<Long> storeIDs = new TreeSet<Long>();
        for (int i = 0; i < files.length; i++) {
            storeIDs.add(getStoreId(files[i]));
        }
        return storeIDs;
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
        try {
            // check if we exceed the maximum allowed store size, if we do cleanup an old file
            // check if the current store file maximum filesize is reached, if it is the current store should be rotated
            if (isCurrentStoreMaximumFileSizeReached()) {
                if (isCleanupRequired()) {
                    cleanup();
                }
                m_currentStore.close();
                m_currentStore = createStore(m_currentStore.getId(), getLastLogfileNumber(m_currentStore.getId()) + 1);
            }
            // convert the map of properties to a dictionary
            Dictionary<String, String> dictionary = new Hashtable<String, String>();
            for (Entry<String, String> entry : properties.entrySet()) {
                dictionary.put(entry.getKey(), entry.getValue());
            }
            // log the event
            long nextEventId = (m_highest = getHighestEventID(m_currentStore.getId()) + 1);
            Event result = new Event(null, m_currentStore.getId(), nextEventId, System.currentTimeMillis(), type, dictionary);
            m_currentStore.append(result.getID(), result.toRepresentation().getBytes());
        }
        catch (IOException ex) {
            handleException(m_currentStore, ex);
        }
    }

    public void forceCreateNewStore() throws IOException {
        m_currentStore = newFeedbackStore();
    }

    /**
     * Give the highest eventId that is is present is the specified store
     * 
     * @param the
     *            storeId
     * @return the highest event present in the store
     */
    public long getHighestEventID(long storeID) throws IOException {
        FeedbackStore store = getLastStore(storeID);
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
            closeIfNeeded(new FeedbackStore[] { store });
        }
        return -1;
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
        FeedbackStore[] stores = getAllStores(storeID);
        List<Event> result = new ArrayList<Event>();
        try {
            for (FeedbackStore store : stores) {
                try {

                    if (store.getCurrent() > fromEventID) {
                        store.reset();
                    }
                    while (store.hasNext()) {
                        long eventID = store.readCurrentID();
                        if ((eventID >= fromEventID) && (eventID <= toEventID)) {
                            result.add(new Event(new String(store.read())));
                        }
                        else {
                            store.skip();
                        }
                    }
                }
                catch (Exception ex) {
                    handleException(store, ex);
                }
            }
        }
        finally {
            closeIfNeeded(stores);
        }
        return result;
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
        if (store == m_currentStore) {
            m_currentStore = newFeedbackStore();
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
    private boolean isCurrentStoreMaximumFileSizeReached() throws IOException {
        return (m_currentStore.getFileSize()) >= (m_maxFileSize / NUMBER_OF_FILES);
    }

    /**
     * Check if the maximum fileSize for all the logfiles together is reached
     * 
     * @return is the cleanup required
     */
    private boolean isCleanupRequired() throws IOException {
        return getFileSize(getStoreFiles()) >= (m_maxFileSize);
    }

    /**
     * Removes old logfiles starting from the oldest file. It stops when there is 10% free space.
     */
    private void cleanup() throws IOException {
        File[] files = getStoreFiles();
        while (getFileSize(files) > ((m_maxFileSize) / (NUMBER_OF_FILES - 1))) {
            File oldestFile = files[0];
            if (oldestFile != null) {
                oldestFile.delete();
            }
            files = getStoreFiles();
        }
    }

    /**
     * Return the filesize of the given files in Kb
     * 
     * @param files
     *            a list of files
     */
    private long getFileSize(File[] files) {
        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
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
        if (m_currentStore != null && m_currentStore.getId() == storeID) {
            return m_currentStore;
        }

        return createStore(storeID);
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
                int result = (int) (getStoreId(f1) - getStoreId(f2));
                if (result == 0) {
                    int f1Number = getLogfileNumber(f1.getName(), getStoreName(getStoreId(f1)));
                    int f2Number = getLogfileNumber(f2.getName(), getStoreName(getStoreId(f2)));
                    result = f1Number - f2Number;
                }
                return result;
            }

        });
        return files;
    }

    /**
     * Create a new empty feedbackstore with a new storeId.
     * 
     * @return A new feedbackstore with a new storeID
     */
    private FeedbackStore newFeedbackStore() throws IOException {
        long storeId = System.currentTimeMillis();
        while (!(new File(m_baseDir, getStoreName(storeId) + ".1")).createNewFile()) {
            storeId++;
        }
        return new FeedbackStore(new File(m_baseDir, getStoreName(storeId) + ".1"), storeId);
    }

    /**
     * Return all feedbackstores for a single storeId.
     * 
     * @param storeId
     *            the storeId
     * @return a list of all feedbackstores for this storeId
     */
    private FeedbackStore[] getAllStores(long storeId) throws IOException {
        List<FeedbackStore> stores = new ArrayList<FeedbackStore>();
        File[] files = getStoreFiles();
        for (File file : files) {
            if (storeId == getStoreId(file)) {
                stores.add(new FeedbackStore(file, storeId));
            }
        }

        // replace the last reference to the current store to make sure there are no multiple FeedbackStores for one
        // file
        if (stores.size() >= 1 && m_currentStore.getId() == storeId) {
            stores.set(stores.size() - 1, m_currentStore);
        }
        return stores.toArray(new FeedbackStore[stores.size()]);
    }

    /**
     * Create the feedbackstore for the specified storeId. If this storeId is already backed by files on disk then the
     * last file will be used to create this feedbackstore.
     * 
     * @param storeId
     *            the storeId
     * @return the newest feedbackstore for this storeID
     */
    private FeedbackStore createStore(long storeId) throws IOException {
        return createStore(storeId, getLastLogfileNumber(storeId));
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
        if (isCleanupRequired()) {
            cleanup();
        }
        return new FeedbackStore(new File(m_baseDir, getStoreName(storeId) + "." + fileNumber), storeId);
    }

    /**
     * Returns the last file for the specified storeId
     * 
     * @param storeId
     *            the storeID
     * @return the latest (newest) file backing the specified storeID
     */
    private int getLastLogfileNumber(long storeId) throws IOException {
        File[] storeFiles = getStoreFiles();
        String storeName = getStoreName(storeId);

        int lastNumber = 1;
        for (File file : storeFiles) {
            String fileName = file.getName();
            if (fileName.contains(storeName)) {
                lastNumber = getLogfileNumber(fileName, storeName);
            }
        }
        return lastNumber;
    }

    /**
     * Get the name of the store for a storeId
     * 
     * @param storeId
     *            the storeId
     * @return the basename of the file
     */
    private String getStoreName(long storeId) {
        return m_name + "-" + storeId;
    }

    private int getLogfileNumber(String logfileName, String storeName) {
        String extension = logfileName.replace(storeName + ".", "");
        return Integer.parseInt(extension);

    }

    /**
     * Parse the storeId from the specified fileName
     * 
     * @param storeFile
     *            a store file
     * @return the storeId
     */
    private long getStoreId(File storeFile) {
        // remove the extension from the filename
        String storeName = storeFile.getName().replaceFirst("[.][^.]+$", "");
        return Long.parseLong(storeName.replace(m_name + "-", ""));
    }

    /**
     * Close all the feedbackstores if necessary
     * 
     * @param stores
     *            a list of stores
     */
    private void closeIfNeeded(FeedbackStore[] stores) {
        for (FeedbackStore store : stores) {
            if (store != m_currentStore) {
                try {
                    store.close();
                }
                catch (IOException ex) {
                    // Not much we can do
                }
            }
        }
    }

    private void logError(String msg, Exception cause, Object... args) {
        m_agentContext.getHandler(LoggingHandler.class).logError("feedbackChannel(" + m_name + ")", msg, cause, args);
    }

}
