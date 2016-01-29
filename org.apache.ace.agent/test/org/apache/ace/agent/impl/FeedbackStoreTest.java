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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ace.agent.impl.FeedbackStore.Record;
import org.testng.annotations.Test;

/**
 * Test cases for {@link FeedbackStore}.
 */
public class FeedbackStoreTest {
    private static class Reader extends Thread {
        private final CountDownLatch m_start;
        private final CountDownLatch m_stop;
        private final FeedbackStore m_store;
        private final ConcurrentMap<Long, Boolean> m_seen = new ConcurrentHashMap<>();
        private final int m_count;

        public Reader(FeedbackStore store, CountDownLatch start, CountDownLatch stop, int count) {
            this(store, start, stop, count, 0);
        }

        public Reader(FeedbackStore store, CountDownLatch start, CountDownLatch stop, int count, int initial) {
            setName("Reader-" + initial);
            m_store = store;
            m_start = start;
            m_stop = stop;
            m_count = count;
        }

        @Override
        public void run() {
            try {
                m_start.await();

                System.out.printf("Reader (%s) starting to read %d records...%n", getName(), m_count);

                Random rnd = new Random();

                long oldID = 0;
                while (m_seen.size() < m_count) {
                    try {
                        // generate data records with different sizes...
                        if (rnd.nextInt(10) >= 5) {
                            // reset all...
                            m_store.init();
                        }
                        long id = m_store.getLastEventID();
                        if (id >= m_count) {
                            throw new IOException(String.format("Invalid record ID: %1$d (0x%1$x)!%n", id));
                        }
                        for (long j = oldID; j <= id; j++) {
                            m_seen.putIfAbsent(Long.valueOf(j), Boolean.TRUE);
                        }
                        oldID = id;
                    }
                    catch (IOException e) {
                        System.out.printf("I/O exception (%s) caught: %s in %s.%n", e.getClass().getSimpleName(), e.getMessage(), getCaller(e));
                    }
                }

                System.out.printf("Reader (%s) finished with %d records read...%n", getName(), m_seen.size());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                m_stop.countDown();
            }
        }
    }

    private static class Writer extends Thread {
        private final CountDownLatch m_start;
        private final CountDownLatch m_stop;
        private final FeedbackStore m_store;
        private final ConcurrentMap<Long, byte[]> m_written = new ConcurrentHashMap<>();
        private final int m_count;
        private final int m_initValue;
        private final int m_stepSize;

        public Writer(FeedbackStore store, CountDownLatch start, CountDownLatch stop, int count) {
            this(store, start, stop, count, 0, 1);
        }

        public Writer(FeedbackStore store, CountDownLatch start, CountDownLatch stop, int count, int initial, int stepSize) {
            setName("Writer-" + initial);
            m_store = store;
            m_start = start;
            m_stop = stop;
            m_count = count;
            m_initValue = initial;
            m_stepSize = stepSize;
        }

        @Override
        public void run() {
            try {
                m_start.await();

                System.out.printf("Writer (%s) starts writing %d records...%n", getName(), m_count);

                for (int i = m_initValue; i < m_count; i += m_stepSize) {
                    long id = i;
                    byte[] entry = String.format("record-data-%d", i).getBytes();

                    m_store.append(id, entry);
                    m_written.putIfAbsent(Long.valueOf(id), entry);
                }

                System.out.printf("Writer (%s) finished with %d records written...%n", getName(), m_written.size());
            }
            catch (InterruptedException e) {
                // ok, stop...
            }
            catch (IOException exception) {
                exception.printStackTrace();
            }
            finally {
                m_stop.countDown();
            }
        }
    }

    private static String getCaller(Exception e) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] st = e.getStackTrace();
        int n = Math.min(st.length, 1);
        int m = Math.min(st.length, 4);
        for (int i = n; i < m; i++) {
            if (i > n) {
                sb.append(" -> ");
            }
            StackTraceElement ste = st[i];
            sb.append(ste.getClassName()).append(".").append(ste.getMethodName()).append("(").append(ste.getLineNumber()).append(")");
        }
        return sb.toString();
    }
    
    @Test
    public void testTimedWrite() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 1000000;

        final FeedbackStore store = new FeedbackStore(storeFile, 1);

        long start = System.nanoTime();
        for (int i = 0; i < recordCount; i++) {
            store.append(i, "Hello World!".getBytes());
        }
        long end = System.nanoTime();
        System.out.printf("Writing %d records took %.3f ms.%n", recordCount, (end - start) / 1.0e6);
    }

    /**
     * Tests that concurrent use of a {@link FeedbackStore} with a single reader and multiple writers works as expected.
     */
    @Test
    public void testConcurrentUseSingleReaderAndMultipleWriters() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 100000;
        final int writerCount = Runtime.getRuntime().availableProcessors() + 1;

        final FeedbackStore store = new FeedbackStore(storeFile, 1);

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(writerCount + 1);

        Writer[] writers = new Writer[writerCount];

        for (int i = 0; i < writerCount; i++) {
            writers[i] = new Writer(store, start, stop, recordCount, i, writerCount);
        }

        Reader reader = new Reader(store, start, stop, recordCount);

        // gents, start your engines...
        reader.start();
        for (int i = 0; i < writers.length; i++) {
            writers[i].start();
        }

        // 3, 2, 1... GO...
        start.countDown();

        // waiting both threads to finish...
        assertTrue(stop.await(10 * writerCount, TimeUnit.SECONDS));

        int writtenCount = 0;
        for (int i = 0; i < writers.length; i++) {
            writers[i].join();
            writtenCount += writers[i].m_written.size();
        }

        reader.join();

        int readCount = reader.m_seen.size();

        assertEquals(recordCount, writtenCount, "Not all records were written?");
        assertEquals(readCount, writtenCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writers);
    }

    /**
     * Tests that concurrent use of a {@link FeedbackStore} with multiple readers and multiple writers works as
     * expected.
     */
    @Test
    public void testConcurrentUseMultipleReaderAndMultipleWriters() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 100000;
        final int readerCount = Runtime.getRuntime().availableProcessors() + 1;
        final int writerCount = Runtime.getRuntime().availableProcessors() + 1;

        final FeedbackStore store = new FeedbackStore(storeFile, 1);

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(writerCount + readerCount);

        Writer[] writers = new Writer[writerCount];
        for (int i = 0; i < writerCount; i++) {
            writers[i] = new Writer(store, start, stop, recordCount, i, writerCount);
        }

        Reader[] readers = new Reader[readerCount];
        for (int i = 0; i < readerCount; i++) {
            readers[i] = new Reader(store, start, stop, recordCount, i);
        }

        // gents, start your engines...
        for (int i = 0; i < readers.length; i++) {
            readers[i].start();
        }
        for (int i = 0; i < writers.length; i++) {
            writers[i].start();
        }

        // 3, 2, 1... GO...
        start.countDown();

        // waiting both threads to finish...
        assertTrue(stop.await(10 * (writerCount + readerCount), TimeUnit.SECONDS));

        int readCount = 0;
        for (int i = 0; i < readers.length; i++) {
            readers[i].join();
            readCount += readers[i].m_seen.size();
        }
        int writtenCount = 0;
        for (int i = 0; i < writers.length; i++) {
            writers[i].join();
            writtenCount += writers[i].m_written.size();
        }

        assertEquals(recordCount, writtenCount, "Not all records were written?");
        // All readers read the exact same data, so we've got N copies of it...
        assertEquals(readCount, readerCount * writtenCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writers);
    }

    /**
     * Tests that concurrent use of a {@link FeedbackStore} with a single reader and writer works as expected.
     */
    @Test
    public void testConcurrentUseSingleReaderAndSingleWriter() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 10000;

        final FeedbackStore store = new FeedbackStore(storeFile, 1);

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(2);

        Writer writer = new Writer(store, start, stop, recordCount);
        Reader reader = new Reader(store, start, stop, recordCount);

        // gents, start your engines...
        writer.start();
        reader.start();

        // 3, 2, 1... GO...
        start.countDown();

        // waiting both threads to finish...
        assertTrue(stop.await(15, TimeUnit.SECONDS));

        writer.join();
        reader.join();

        int writeCount = writer.m_written.size();
        int readCount = reader.m_seen.size();

        assertEquals(recordCount, writeCount, "Not all records were written?");
        assertEquals(readCount, writeCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writer);
    }

    private void verifyStoreContents(final FeedbackStore store, final int count, Writer... writers) throws IOException {
        store.reset();
        store.init();

        assertEquals(store.getFirstEventID(), 0, "First record ID is different");
        assertEquals(store.getLastEventID(), count - 1, "Last record ID is different");

        // Verify the written file...
        List<Record> records = store.getRecords(0, count - 1);
        Collections.sort(records);

        long expectedID = 0;
        for (Record record : records) {
            long id = record.m_id;

            byte[] expectedEntry = null;
            for (int i = 0; (expectedEntry == null) && i < writers.length; i++) {
                expectedEntry = writers[i].m_written.remove(id);
            }
            assertNotNull(expectedEntry, "Event ID #" + id + " never written?!");
            // Test consistency of written data...
            assertEquals(record.m_entry, expectedEntry, "Entry mismatch?!");
            // Test continuation of written data...
            assertEquals(record.m_id, expectedID++, "Entry ID mismatch?!");
        }
    }
}
