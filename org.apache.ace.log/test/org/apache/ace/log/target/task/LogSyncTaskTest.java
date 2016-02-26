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
package org.apache.ace.log.target.task;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.apache.ace.discovery.Discovery;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.identification.Identification;
import org.apache.ace.log.target.store.LogStore;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LogSyncTaskTest {

    private static final String TARGET_ID = "gwID";
    private LogSyncTask m_task;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_task = new LogSyncTask("testlog");
        TestUtils.configureObject(m_task, LogService.class);
        TestUtils.configureObject(m_task, Identification.class, new Identification() {
            public String getID() {
                return TARGET_ID;
            }
        });
        TestUtils.configureObject(m_task, Discovery.class);
        TestUtils.configureObject(m_task, LogStore.class);
    }

    @Test()
    public synchronized void getRange() throws Exception {
        final Descriptor range = new Descriptor(TARGET_ID, 1, new SortedRangeSet("1-10"));
        m_task.getDescriptor(new InputStream() {
            int m_count = 0;
            byte[] m_bytes = (range.toRepresentation() + "\n").getBytes();

            @Override
            public int read() throws IOException {
                if (m_count < m_bytes.length) {
                    byte b = m_bytes[m_count];
                    m_count++;
                    return b;
                }
                else {
                    return -1;
                }
            }
        });
    }

    @Test()
    public synchronized void synchronizeLog() throws Exception {
        final Descriptor range = new Descriptor(TARGET_ID, 1, new SortedRangeSet(new long[] { 0 }));
        final Event event = new Event(TARGET_ID, 1, 1, 1, 1);
        final List<Event> events = new ArrayList<>();
        events.add(event);

        InputStream input = new InputStream() {
            byte[] bytes = range.toRepresentation().getBytes();
            int count = 0;

            @Override
            public int read() throws IOException {
                if (count < bytes.length) {
                    byte b = bytes[count];
                    count++;
                    return b;
                }
                else {
                    return -1;
                }
            }
        };
        TestUtils.configureObject(m_task, LogStore.class, new LogStore() {
            public List<?> get(long logID, long from, long to) throws IOException {
                return events;
            }

            public long getHighestID(long logID) throws IOException {
                return event.getID();
            }

            public List<?> get(long logID) throws IOException {
                return null;
            }

            public long[] getLogIDs() throws IOException {
                return null;
            }

            public Event put(int type, Dictionary props) throws IOException {
                return null;
            }
        });
        MockConnection connection = new MockConnection(new URL("http://mock"));

        m_task.synchronizeLog(1, input, connection);
        String expectedString = event.toRepresentation() + "\n";
        String actualString = connection.getString();

        assert actualString.equals(expectedString) : "We expected " + expectedString + " but received " + actualString;
    }

    private class MockConnection extends HttpURLConnection {

        private MockOutputStream m_output;

        public MockConnection(URL url) throws IOException {
            super(url);
            m_output = new MockOutputStream();
        }

        public String getString() {
            return m_output.getString();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return m_output;
        }

        @Override
        public void disconnect() {
            // Nop
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() throws IOException {
            // Nop
        }
    }

    private class MockOutputStream extends OutputStream {
        byte[] bytes = new byte[8 * 1024];
        int count = 0;

        @Override
        public void write(int arg0) throws IOException {
            bytes[count] = (byte) arg0;
            count++;
        }

        public String getString() {
            return new String(bytes, 0, count);
        }
    }

}
