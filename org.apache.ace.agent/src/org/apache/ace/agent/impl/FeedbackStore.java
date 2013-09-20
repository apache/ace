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
import java.util.concurrent.atomic.AtomicLong;

/**
 * The general idea is to provide easy access to a file of records. It supports iterating over records both by skipping
 * and by reading. Furthermore, files can be truncated. Most methods will make an effort to reset to the last good
 * record in case of an error -- hence, a call to truncate after an IOException might make the store readable again.
 */
public class FeedbackStore {
    private final RandomAccessFile m_store;
    private final long m_id;
    private final AtomicLong m_current;

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
        m_store = new RandomAccessFile(store, "rwd");
        m_id = id;
        m_current = new AtomicLong(0);
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
            m_store.seek(m_current.get());
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
        m_current.set(0);
    }

    /**
     * Determine whether there are any records left based on the current postion.
     * 
     * @return <code>true</code> if there are still records to be read.
     * @throws IOException
     *             in case of an IO error.
     */
    public boolean hasNext() throws IOException {
        return m_store.getFilePointer() < m_store.length();
    }

    /**
     * Read a single logevent from this file
     * 
     * @return the bytes for a single logevent
     * @throws IOException
     *             in case of an IO error.
     */
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
                setCurrent(current);
                return entry;
            }
        }
        catch (IOException ex) {
            handle(pos, ex);
        }
        return null;
    }

    /**
     * Return the id for the logevent at the current position in the file
     * 
     * @return the event id
     * @throws IOException
     *             in case of an IO error.
     */
    public long readCurrentID() throws IOException {
        long pos = m_store.getFilePointer();
        try {
            if (pos < m_store.length()) {
                long id = m_store.readLong();
                return id;
            }
        }
        catch (IOException ex) {
            handle(pos, ex);
        }
        finally {
            m_store.seek(pos);
        }
        return -1;
    }

    /**
     * Make sure the store is readable. As a result, the store is at the end of the records.
     * 
     * @throws IOException
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
     * @throws IOException
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
            m_store.skipBytes(next);
            setCurrent(pos);
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
     * @throws IOException
     *             in case of any IO error.
     */
    public void append(long id, byte[] entry) throws IOException {
        long pos = m_store.getFilePointer();
        long length = m_store.length();
        try {
            m_store.seek(length);
            long current = m_store.getFilePointer();
            m_store.writeLong(id);
            m_store.writeInt(entry.length);
            m_store.write(entry);
            m_store.seek(pos);
            setCurrent(current);
        }
        catch (IOException ex) {
            handle(pos, ex);
        }
    }

    /**
     * Try to truncate the store at the current record.
     * 
     * @throws IOException
     *             in case of any IO error.
     */
    public void truncate() throws IOException {
        m_store.setLength(m_store.getFilePointer());
    }

    /**
     * Release any resources.
     * 
     * @throws IOException
     *             in case of any IO error.
     */
    public void close() throws IOException {
        m_store.close();
    }

    /**
     * Return the filesize for this file
     * 
     * @return the size in bytes
     * @throws IOException
     *             in case of any IO error.
     */
    public long getFileSize() throws IOException {
        return m_store.length();
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

    private void setCurrent(long pos) {
        long old;
        do {
            old = m_current.get();
        }
        while (!m_current.compareAndSet(old, pos));
    }
}
