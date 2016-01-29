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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The general idea is to provide easy access to a file of records. It supports iterating over records both by skipping
 * and by reading. Furthermore, files can be truncated. Most methods will make an effort to reset to the last good
 * record in case of an error -- hence, a call to truncate after an IOException might make the store readable again.
 */
public class FeedbackStore {
    /**
     * Denotes a single record stored in a FeedbackStore.
     */
    public static class Record implements Comparable<Record> {
        public final long m_id;
        public final byte[] m_entry;

        /**
         * Creates a new {@link Record} instance.
         */
        public Record(long id, byte[] entry) {
            m_id = id;
            m_entry = entry;
        }

        @Override
        public int compareTo(Record other) {
            return (m_id < other.m_id ? -1 : (m_id == other.m_id ? 0 : 1));
        }
    }

    private final File m_storeFile;
    private final RandomAccessFile m_store;
    private final long m_id;

    private long m_lowestEventID;
    private long m_highestEventID;

    private final ReadWriteLock m_rwLock = new ReentrantReadWriteLock();

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
    FeedbackStore(File store, long id) throws IOException {
        m_storeFile = store;
        m_store = new RandomAccessFile(store, "rw");
        m_id = id;

        init();
    }

    /**
     * Store the given record data as the next record.
     * 
     * @param entry
     *            the data of the record to store.
     * @throws IOException
     *             in case of any IO error.
     */
    public void append(long id, byte[] entry) throws IOException {
        Lock writeLock = m_rwLock.writeLock();

        writeLock.lock();
        try {
            long pos = m_store.getFilePointer();
            try {
                long current = m_store.length();
                // Go to end of file...
                m_store.seek(current);

                m_store.writeLong(id);
                m_store.writeInt(entry.length);
                m_store.write(entry);

                // System.out.printf("Appended %d bytes for record #%d at %d (current = %d).%n", entry.length, id,
                // current, m_store.getFilePointer());

                // Go back to start of record...
                m_store.seek(current);

                updateIDs(id);
            }
            catch (IOException ex) {
                handle(pos, ex);
            }
        }
        finally {
            writeLock.unlock();
        }
    }

    /**
     * Release all resources.
     * 
     * @throws IOException
     *             in case of any IO error.
     */
    public void close() throws IOException {
        Lock writeLock = m_rwLock.writeLock();

        writeLock.lock();
        try {
            m_store.close();
        }
        finally {
            writeLock.unlock();
        }
    }

    /**
     * Return the filesize for this file
     * 
     * @return the size in bytes
     * @throws IOException
     *             in case of any IO error.
     */
    public long getFileSize() throws IOException {
        Lock readLock = m_rwLock.readLock();

        readLock.lock();
        try {
            return m_store.length();
        }
        finally {
            readLock.unlock();
        }
    }

    /**
     * Get the ID of the first written event record, which is most of the times the first event record.
     * 
     * @return the ID of the first record, >= 0.
     */
    public long getFirstEventID() {
        Lock readLock = m_rwLock.readLock();

        readLock.lock();
        try {
            return m_lowestEventID;
        }
        finally {
            readLock.unlock();
        }
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
     * Get the ID of the last written event record, which is most of the times the current event record.
     * 
     * @return the ID of the current record, >= 0.
     */
    public long getLastEventID() {
        Lock readLock = m_rwLock.readLock();

        readLock.lock();
        try {
            return m_highestEventID;
        }
        finally {
            readLock.unlock();
        }
    }

    public List<Record> getRecords(long fromId, long toId) throws IOException {
        RandomAccessFile raf = null;

        List<Record> result = new ArrayList<>();

        try {
            // Take a NEW file instance as to ensure we do not
            // disturb any concurrent writes while initializing...
            raf = new RandomAccessFile(m_storeFile, "r");

            // the length is live-updated, so we should be able
            // to get as close as possible to the last written record...
            while (raf.getFilePointer() < raf.length()) {
                int headerSize = 12; // 8 for long, 4 for int...
                waitToRead(raf, headerSize);

                long id = raf.readLong();
                int entrySize = raf.readInt();
                waitToRead(raf, entrySize);

                if ((id >= fromId) && (id <= toId)) {
                    byte[] buffer = new byte[entrySize];
                    raf.readFully(buffer);

                    result.add(new Record(id, buffer));
                }
                else {
                    int actual = 0;
                    do {
                        actual += raf.skipBytes(entrySize - actual);
                    }
                    while (actual < entrySize);
                }
            }
        }
        finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            }
            catch (IOException ignored) {
            }
        }

        return result;
    }

    /**
     * Make sure the store is readable. As a result, the store is at the end of the records.
     * 
     * @throws IOException
     *             in case of any IO error.
     */
    void init() throws IOException {
        Lock writeLock = m_rwLock.writeLock();
        RandomAccessFile raf = null;

        try {
            // Take a NEW file instance as to ensure we do not
            // disturb any concurrent writes while initializing...
            raf = new RandomAccessFile(m_storeFile, "r");

            long lowest = Long.MAX_VALUE;
            long highest = Long.MIN_VALUE;
            boolean empty = true;

            // the length is live-updated, so we should be able
            // to get as close as possible to the last written record...
            while (raf.getFilePointer() < raf.length()) {
                long id = skip(raf);

                lowest = Math.min(lowest, id);
                highest = Math.max(highest, id);
                empty = false;
            }

            if (empty) {
                lowest = Long.MAX_VALUE;
                highest = 0;
            }

            writeLock.lock();
            try {
                m_lowestEventID = lowest;
                m_highestEventID = highest;
            }
            finally {
                writeLock.unlock();
            }

//            System.out.printf("Init, range = %d..%d.%n", m_lowestEventID, m_highestEventID);
        }
        finally {
            try {
                if (raf != null) {
                    raf.close();
                }
            }
            catch (IOException ignored) {
            }
        }
    }

    /**
     * Reset the store to the beginning of the records
     * 
     * @throws java.io.IOException
     *             in case of an IO error.
     */
    void reset() throws IOException {
        Lock writeLock = m_rwLock.writeLock();

        writeLock.lock();
        try {
            m_store.seek(0);
        }
        finally {
            writeLock.unlock();
        }
    }

    /**
     * Try to truncate the store at the current record.
     * 
     * @throws IOException
     *             in case of any IO error.
     */
    public void truncate() throws IOException {
        Lock writeLock = m_rwLock.writeLock();

        writeLock.lock();
        try {
            m_store.setLength(m_store.getFilePointer());
        }
        finally {
            writeLock.unlock();
        }
    }

    private void handle(long pos, IOException exception) throws IOException {
        try {
            m_store.seek(pos);
        }
        catch (IOException ex) {
            // we don't log this, seeking back to pos is a 'best effort'
            // attempt to keep the file consistent and it would be very
            // strange for it to fail (in which case we have no code to
            // deal with that anyway)
        }
        throw exception;
    }

    /**
     * Skips an entire record for the given {@link RandomAccessFile}, assuming it is placed at the beginning of a
     * record!
     * 
     * @param raf
     *            the {@link RandomAccessFile} to skip a record in, cannot be <code>null</code>.
     * @return the event ID of the skipped record.
     * @throws IOException
     *             in case of I/O errors.
     */
    private long skip(RandomAccessFile raf) throws IOException {
        int headerSize = 12; // 8 for long, 4 for int...
        waitToRead(raf, headerSize);

        long lastId = raf.readLong();
        int entrySize = raf.readInt();

        waitToRead(raf, entrySize);

        int actual = 0;
        do {
            actual += raf.skipBytes(entrySize - actual);
        }
        while (actual < entrySize);

        return lastId;
    }

    private void updateIDs(long eventID) {
        if (eventID < m_lowestEventID) {
            m_lowestEventID = eventID;
        }
        if (eventID > m_highestEventID) {
            m_highestEventID = eventID;
        }
    }

    private void waitToRead(RandomAccessFile raf, int bytesNeeded) throws IOException {
        int tryCount = 2000;
        while (tryCount-- > 0 && (raf.getFilePointer() + bytesNeeded) > raf.length()) {
            // Looking at a file that is changing, so it might well that the size is changing, wait a little and try
            // again (wait 1 usec, 2 msec total waiting time)...
            LockSupport.parkNanos(1000L);
        }
        if (tryCount <= 0) {
            throw new EOFException("Unexpected end of file, expected at least " + bytesNeeded + " additional bytes!");
        }
    }
}
