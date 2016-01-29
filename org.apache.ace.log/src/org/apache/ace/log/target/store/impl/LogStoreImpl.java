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
package org.apache.ace.log.target.store.impl;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.feedback.Event;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.osgi.service.log.LogService;

/**
 * This class provides an implementation of the LogStore service. It tries to
 * repair broken log files to make them readable again. However, this might lead
 * to loss of data. Additionally, a new file is used when an error is detected.
 */
public class LogStoreImpl implements LogStore {
    // injected by dependencymanager
    volatile Identification m_identification;
    volatile LogService m_log;

    // The current store
    private Store m_store = null;
    private final File m_baseDir;
    private long m_highest;

    /**
     * Create new instance using the specified directory as root directory.
     * 
     * @param baseDir
     *            Root directory to use for storage.
     */
    public LogStoreImpl(File baseDir) {
        m_baseDir = new File(baseDir, "store");
    }

    /**
     * Init the current store.
     * 
     * @throws java.io.IOException
     */
    protected synchronized void start() throws IOException {
        if (!m_baseDir.isDirectory() && !m_baseDir.mkdirs()) {
            throw new IllegalArgumentException("Need valid dir");
        }
        long current = -1;
        File[] files = (File[]) notNull(m_baseDir.listFiles());
        for (int i = 0; i < files.length; i++) {
            long id = Long.parseLong(files[i].getName());
            current = Math.max(id, current);
        }
        try {
            if (current == -1) {
                m_store = newStore();
            } else {
                m_store = createStore(current);
                try {
                    m_store.init();
                } 
                catch (IOException ex) {
                    handleException(m_store, ex);
                }
            }
        } 
        catch (IOException ex) {
            // We should be able to recover from the error.
            m_log.log(LogService.LOG_ERROR, "Exception during log store init",
                    ex);
        }
    }

    /**
     * Close the current store.
     * 
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    protected synchronized void stop() throws IOException {
        m_store.close();
        m_store = null;
    }

    /**
     * Create a store object for a new log.
     * 
     * @return a store object for a new log.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    protected Store newStore() throws IOException {
        long id = System.currentTimeMillis();

        while (!(new File(m_baseDir, String.valueOf(id))).createNewFile()) {
            id++;
        }

        return new Store(new File(m_baseDir, String.valueOf(id)), id);
    }

    /**
     * Create a store object for the given log. This should not be used to
     * create a new log.
     * 
     * @param id
     *            the id of the log.
     * @return a new store object for the given log.
     * @throws java.io.IOException
     *             in case of an IO error.
     */
    protected Store createStore(long id) throws IOException {
        return new Store(new File(m_baseDir, String.valueOf(id)), id);
    }

    /**
     * Get the entries in the given range from the given log.
     * 
     * @param logID
     *            the id of the log.
     * @param from
     *            the lower bound of the range.
     * @param to
     *            the upper bound of the range.
     * @return a list of entries from the given log in the given range.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    public synchronized List get(long logID, long from, long to)
            throws IOException {
        Store store = getLog(logID);
        List<Event> result = new ArrayList<>();
        try {
            if (store.getCurrent() > from) {
                store.reset();
            }

            while (store.hasNext()) {
                long eventID = store.readCurrentID();
                if ((eventID >= from) && (eventID <= to)) {
                    result.add(new Event(new String(store.read())));
                } else {
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

    /**
     * Try to repair the given store, log the given exception and rethrow it. In
     * case the store is the current log switch to a new one if possible.
     * 
     * @param store
     *            the store to repair/close.
     * @param exception
     *            the exception to log and rethrow.
     * @throws java.io.IOException
     *             the given exception if it is an IOException else the message
     *             of the given exception wrapped in an IOException.
     */
    protected void handleException(Store store, Exception exception)
            throws IOException {
        m_log.log(LogService.LOG_WARNING, "Exception accessing the log: "
                + store.getId(), exception);
        if (store == m_store) {
            m_store = newStore();
        }

        try {
            store.truncate();
        } 
        catch (IOException ex) {
            m_log.log(LogService.LOG_WARNING, "Exception during truncate: "
                    + store.getId(), ex);
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

    /**
     * Get all entries of the given log.
     * 
     * @param logID
     *            the id of the log.
     * @return a list of all entries in this log.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    public List get(long logID) throws IOException {
        return get(logID, 0, Long.MAX_VALUE);
    }

    /**
     * Get the current log ids.
     * 
     * @return the ids of the current logs.
     */
    public long[] getLogIDs() throws IOException {
        File[] files = (File[]) notNull(m_baseDir.listFiles());
        long[] result = new long[files.length];
        for (int i = 0; i < files.length; i++) {
            result[i] = Long.parseLong(files[i].getName());
        }
        return result;
    }

    /**
     * Create and add a LogEvent to the current log.
     * 
     * @param type
     *            the type the event.
     * @param dict
     *            the properties of the event.
     * @return the new event.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    public synchronized Event put(int type, Dictionary dict) throws IOException {
        try {
            Map<String, String> props = new HashMap<>();
            Enumeration<?> keys = dict.keys();
            while (keys.hasMoreElements()) {
                String key = (String) keys.nextElement();
                props.put(key, (String) dict.get(key));
            }
            
            Event result = new Event(null, m_store.getId(), getNextID(), System.currentTimeMillis(), type, props);
            m_store.append(result.getID(), result.toRepresentation().getBytes());
            return result;
        } 
        catch (IOException ex) {
            handleException(m_store, ex);
        }
        return null;
    }

    /**
     * Get the highest entry id of the given log.
     * 
     * @param logID
     *            the id of the log.
     * @return the id of the highest entry.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    public synchronized long getHighestID(long logID) throws IOException {
        Store store = getLog(logID);
        try {
            if (m_highest == 0) {
                store.init();
                return (m_highest = store.getCurrent());
            } else {
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

    /**
     * Close the given store if it is not the current store. IO errors are
     * ignored.
     * 
     * @param store
     *            the store to close.
     */
    protected void closeIfNeeded(Store store) {
        if (store != m_store) {
            try {
                store.close();
            } 
            catch (IOException ex) {
                // Not much we can do;
            }
        }
    }

    /**
     * Get a Store object for the log of the given logid.
     * 
     * @param logID
     *            the id for which to return (and possibly create) a store.
     * @return either a new or the current Store object.
     * @throws java.io.IOException
     *             in case of any IO error.
     */
    protected Store getLog(long logID) throws IOException {
        if (m_store.getId() == logID) {
            return m_store;
        }
        return createStore(logID);
    }

    /**
     * Get the next id for the current store.
     * 
     * @return the next free log id of the current store.
     * @throws java.io.IOException
     */
    protected long getNextID() throws IOException {
        return (m_highest = getHighestID(m_store.m_id) + 1);
    }

    /*
     * throw IOException in case the target is null else return the target.
     */
    private Object notNull(Object target) throws IOException {
        if (target == null) {
            throw new IOException(
                    "Unknown IO error while trying to access the store.");
        }
        return target;
    }

    /**
     * The general idea is to provide easy access to a file of records. It
     * supports iterating over records both by skipping and by reading.
     * Furthermore, files can be truncated. Most methods will make an effort to
     * reset to the last good record in case of an error -- hence, a call to
     * truncate after an IOException might make the store readable again.
     */
    class Store {
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
         * Determine whether there are any records left based on the current
         * postion.
         * 
         * @return <code>true</code> if there are still records to be read.
         * @throws java.io.IOException
         *             in case of an IO error.
         */
        public boolean hasNext() throws IOException {
            return m_store.getFilePointer() < m_store.length();
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
         * Make sure the store is readable. As a result, the store is at the end
         * of the records.
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
                m_log.log(LogService.LOG_WARNING, "Exception during seek!", ex);
            }
            throw exception;
        }
    }
}