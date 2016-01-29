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
package org.apache.ace.log.server.store.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.range.RangeIterator;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.event.EventAdmin;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link LogStoreImpl}.
 */
public class LogStoreImplConcurrencyTest {
    private static final String TARGET_ID = "targetId";
    private static final long STORE_ID = 12345;

    private static class Reader implements Runnable {
        private final String m_name;
        private final CountDownLatch m_start;
        private final CountDownLatch m_stop;
        private final LogStoreImpl m_store;
        private final ConcurrentMap<Long, Boolean> m_seen = new ConcurrentHashMap<>();
        private final int m_count;

        public Reader(LogStoreImpl store, CountDownLatch start, CountDownLatch stop, int count) {
            this(store, start, stop, count, 0);
        }

        public Reader(LogStoreImpl store, CountDownLatch start, CountDownLatch stop, int count, int initial) {
            m_name = "Reader-" + initial;
            m_store = store;
            m_start = start;
            m_stop = stop;
            m_count = count;
        }

        @Override
        public void run() {
            Random rnd = new Random();

            try {
                m_start.await();

                System.out.printf("Reader (%s) starting to read %d records...%n", m_name, m_count);

                while (m_seen.size() < m_count) {
                    try {
                        if (rnd.nextInt(1000) >= 995) {
                            // perform a random cleanup...
                            m_store.clean();
                        }
                        List<Descriptor> descriptors = m_store.getDescriptors(TARGET_ID);
                        for (Descriptor desc : descriptors) {
                            SortedRangeSet rangeSet = desc.getRangeSet();
                            RangeIterator rangeIter = rangeSet.iterator();
                            while (rangeIter.hasNext()) {
                                m_seen.putIfAbsent(Long.valueOf(rangeIter.next()), Boolean.TRUE);
                            }
                        }
                    }
                    catch (IOException e) {
                        System.out.printf("I/O exception (%s) caught: %s in %s.%n", e.getClass().getSimpleName(), e.getMessage(), getCaller(e));
                    }
                }

                System.out.printf("Reader (%s) finished with %d records read...%n", m_name, m_seen.size());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                m_stop.countDown();

                System.out.println("Ending reader (" + m_name + ")");
            }
        }
    }

    private static class Writer implements Runnable {
        private final String m_name;
        private final CountDownLatch m_start;
        private final CountDownLatch m_stop;
        private final LogStoreImpl m_store;
        private final ConcurrentMap<Long, Event> m_written = new ConcurrentHashMap<>();
        private final int m_count;
        private final int m_initValue;
        private final int m_stepSize;

        public Writer(LogStoreImpl store, CountDownLatch start, CountDownLatch stop, int count) {
            this(store, start, stop, count, 0, 1);
        }

        public Writer(LogStoreImpl store, CountDownLatch start, CountDownLatch stop, int count, int initial, int stepSize) {
            m_name = "Writer-" + initial;
            m_store = store;
            m_start = start;
            m_stop = stop;
            m_count = count;
            m_initValue = initial;
            m_stepSize = stepSize;
        }

        @Override
        public void run() {
            Random rnd = new Random();

            try {
                m_start.await();

                System.out.printf("Writer (%s) starts writing %d records...%n", m_name, m_count);

                for (int i = m_initValue; i < m_count; i += m_stepSize) {
                    long id = i;
                    Event event = new Event(TARGET_ID, STORE_ID, id, id, rnd.nextInt(10));

                    try {
                        m_store.put(Arrays.asList(event));
                        m_written.putIfAbsent(Long.valueOf(id), event);
                    }
                    catch (Exception exception) {
                        // Ignore...
                    }
                }

                System.out.printf("Writer (%s) finished with %d records written...%n", m_name, m_written.size());
            }
            catch (InterruptedException e) {
                // ok, stop...
            }
            finally {
                m_stop.countDown();

                System.out.println("Ending writer (" + m_name + ")");
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

    private File m_baseDir;
    private ExecutorService m_executor;
    private CompletionService<Boolean> m_completionService;

    /**
     * Tests that concurrent use of a {@link LogStoreImpl} with multiple readers and multiple writers works as expected.
     */
    @Test(enabled = false)
    public void testConcurrentUseMultipleReaderAndMultipleWriters() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 10000;
        final int readerCount = 3; // Runtime.getRuntime().availableProcessors() + 1;
        final int writerCount = 3; // Runtime.getRuntime().availableProcessors() + 1;

        final LogStoreImpl store = createLogStore();

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
            m_completionService.submit(readers[i], Boolean.TRUE);
        }
        for (int i = 0; i < writers.length; i++) {
            m_completionService.submit(writers[i], Boolean.TRUE);
        }

        // 3, 2, 1... GO...
        start.countDown();

        // waiting for all threads to finish...
        for (int i = 0, r = 0; r < 10 && i < writerCount + readerCount; i++) {
            Future<Boolean> future = m_completionService.poll(1, TimeUnit.MINUTES);
            if (future == null) {
                r++;
            }
        }
        assertTrue(stop.await(5, TimeUnit.SECONDS));

        int readCount = 0;
        for (int i = 0; i < readers.length; i++) {
            readCount += readers[i].m_seen.size();
        }
        int writtenCount = 0;
        for (int i = 0; i < writers.length; i++) {
            writtenCount += writers[i].m_written.size();
        }

        assertEquals(recordCount, writtenCount, "Not all records were written?");
        // All readers read the exact same data, so we've got N copies of it...
        assertEquals(readCount, readerCount * writtenCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writers);
    }

    /**
     * Tests that concurrent use of a {@link LogStoreImpl} with a single reader and multiple writers works as expected.
     */
    @Test(enabled = false)
    public void testConcurrentUseSingleReaderAndMultipleWriters() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 10000;
        final int writerCount = 3; // Runtime.getRuntime().availableProcessors() + 1;

        final LogStoreImpl store = createLogStore();

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(writerCount + 1);

        Writer[] writers = new Writer[writerCount];

        for (int i = 0; i < writerCount; i++) {
            writers[i] = new Writer(store, start, stop, recordCount, i, writerCount);
        }

        Reader reader = new Reader(store, start, stop, recordCount);

        // gents, start your engines...
        m_completionService.submit(reader, Boolean.TRUE);
        for (int i = 0; i < writers.length; i++) {
            m_completionService.submit(writers[i], Boolean.TRUE);
        }

        // 3, 2, 1... GO...
        start.countDown();

        // waiting for all threads to finish...
        for (int i = 0, r = 0; r < 10 && i < writerCount + 1; i++) {
            Future<Boolean> future = m_completionService.poll(1, TimeUnit.MINUTES);
            if (future == null) {
                r++;
            }
        }
        assertTrue(stop.await(5, TimeUnit.SECONDS));

        int writtenCount = 0;
        for (int i = 0; i < writers.length; i++) {
            writtenCount += writers[i].m_written.size();
        }

        int readCount = reader.m_seen.size();

        assertEquals(recordCount, writtenCount, "Not all records were written?");
        assertEquals(readCount, writtenCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writers);
    }

    /**
     * Tests that concurrent use of a {@link LogStoreImpl} with a single reader and writer works as expected.
     */
    @Test
    public void testConcurrentUseSingleReaderAndSingleWriter() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 10000;

        final LogStoreImpl store = createLogStore();

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(2);

        Writer writer = new Writer(store, start, stop, recordCount);
        Reader reader = new Reader(store, start, stop, recordCount);

        // gents, start your engines...
        m_completionService.submit(writer, Boolean.TRUE);
        m_completionService.submit(reader, Boolean.TRUE);

        // 3, 2, 1... GO...
        start.countDown();

        // waiting both threads to finish...
        assertTrue(stop.await(120, TimeUnit.SECONDS));

        int writeCount = writer.m_written.size();
        int readCount = reader.m_seen.size();

        assertEquals(recordCount, writeCount, "Not all records were written?");
        assertEquals(readCount, writeCount, "Not all records were seen?");

        verifyStoreContents(store, recordCount, writer);
    }

    @Test
    public void testTimedWrite() throws Exception {
        File storeFile = File.createTempFile("feedback", ".store");
        storeFile.deleteOnExit();

        final int recordCount = 10000;

        final LogStoreImpl store = createLogStore();

        long start = System.nanoTime();
        for (int i = 0; i < recordCount; i++) {
            store.put(Arrays.asList(new Event("1,2,3,4,5")));
        }
        long end = System.nanoTime();
        System.out.printf("Writing %d records took %.3f ms.%n", recordCount, (end - start) / 1.0e6);
    }

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_baseDir = File.createTempFile("logstore", "txt");
        m_baseDir.delete();
        m_baseDir.mkdirs();
        
        m_executor = Executors.newCachedThreadPool();
        m_completionService = new ExecutorCompletionService<>(m_executor);
    }
    
    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws InterruptedException {
        m_executor.shutdownNow();
        m_executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private LogStoreImpl createLogStore() throws Exception {
        LogStoreImpl logStore = new LogStoreImpl(m_baseDir, "log");
        TestUtils.configureObject(logStore, EventAdmin.class);
        logStore.start();
        return logStore;
    }

    private void verifyStoreContents(final LogStoreImpl store, final int count, Writer... writers) throws IOException {
        // Verify the written file...
        List<Descriptor> descriptors = store.getDescriptors();

        long expectedID = 0;
        for (Descriptor desc : descriptors) {
            SortedRangeSet rangeSet = desc.getRangeSet();
            RangeIterator rangeIter = rangeSet.iterator();

            while (rangeIter.hasNext()) {
                long id = rangeIter.next();

                Event expectedEntry = null;
                for (int i = 0; (expectedEntry == null) && i < writers.length; i++) {
                    expectedEntry = writers[i].m_written.remove(id);
                }
                assertNotNull(expectedEntry, "Event ID #" + id + " never written?!");
                // Test continuation of written data...
                assertEquals(expectedEntry.getID(), expectedID++, "Entry ID mismatch?!");
            }
        }
    }
}
