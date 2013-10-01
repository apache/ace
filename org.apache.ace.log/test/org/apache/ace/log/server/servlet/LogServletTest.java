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
package org.apache.ace.log.server.servlet;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.log.server.servlet.LogServlet;
import org.apache.ace.log.server.store.LogStore;
import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LogServletTest {

    private LogServlet m_logServlet;
    private Descriptor m_range = new Descriptor("tID", 123, new SortedRangeSet("1-3"));
    private Event m_event1 = new Event("tID", 123, 1, 888888, 1, new Properties());
    private Event m_event2 = new Event("tID", 123, 2, 888888, 2, new Properties());
    private MockLogStore m_mockStore;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_logServlet = new LogServlet("test", false /* useAuth */);
        TestUtils.configureObject(m_logServlet, LogService.class);
        m_mockStore = new MockLogStore();
        TestUtils.configureObject(m_logServlet, LogStore.class, m_mockStore);
    }

    @AfterMethod(alwaysRun = true)
    protected void tearDown() throws Exception {
    }

    @Test(groups = { UNIT })
    public void queryLog() throws Exception {
        MockServletOutputStream output = new MockServletOutputStream();
        boolean result = m_logServlet.handleQuery(m_range.getTargetID(), String.valueOf(m_range.getStoreID()), null, output);
        assert result;
        assert m_range.toRepresentation().equals(output.m_text);
        output.m_text = "";
        result = m_logServlet.handleQuery(null, null, null, output);
        assert result;
        assert (m_range.toRepresentation() + "\n").equals(output.m_text);
    }

    @Test(groups = { UNIT })
    public void receiveLog() throws Exception {
        MockServletOutputStream output = new MockServletOutputStream();
        boolean result = m_logServlet.handleReceive(m_range.getTargetID(), String.valueOf(m_range.getStoreID()), "1", null, output);
        assert result;
        String expected = m_event1.toRepresentation() + "\n";
        String actual = output.m_text;
        assert expected.equals(actual) : "We expected '" + expected + "', but received '" + actual + "'";

        output = new MockServletOutputStream();
        result = m_logServlet.handleReceive(m_range.getTargetID(), String.valueOf(m_range.getStoreID()), null , null, output);
        assert result;
        expected = m_event1.toRepresentation() + "\n" + m_event2.toRepresentation() + "\n";
        actual = output.m_text;
        assert expected.equals(actual) : "We expected '" + expected + "', but received '" + actual + "'";;
    }

    @Test(groups = { UNIT })
    public void sendLog() throws Exception {
        MockServletInputStream input = new MockServletInputStream();
        String expected = m_event1.toRepresentation() + "\n" + m_event2.toRepresentation() + "\n";
        input.setBytes(expected.getBytes());
        m_logServlet.handleSend(input);

        String actual = "";
        for (Iterator<Event> i = m_mockStore.m_events.iterator(); i.hasNext();) {
            Event event = i.next();
            actual = actual + event.toRepresentation() + "\n";
        }
        assert expected.equals(actual);
    }

    private class MockLogStore implements LogStore {
        public List<Event> m_events = new ArrayList<Event>();

        public List<Event> get(Descriptor range) {
            List<Event> events = new ArrayList<Event>();
            if (range.getRangeSet().contains(1)) {
                events.add(m_event1);
            }
            if (range.getRangeSet().contains(2)) {
                events.add(m_event2);
            }
            return events;
        }
        public List<Descriptor> getDescriptors(String targetID) {
            return null;
        }
        public List<Descriptor> getDescriptors() {
            List<Descriptor> ranges = new ArrayList<Descriptor>();
            ranges.add(m_range);
            return ranges;
        }
        public Descriptor getDescriptor(String targetID, long logID) throws IOException {
            return m_range;
        }
        public void put(List<Event> events) throws IOException {
            m_events = events;
        }
    }

    private class MockServletOutputStream extends ServletOutputStream {
        public String m_text = "";

        @Override
        public void print(String s) throws IOException {
            m_text = m_text.concat(s);
        }
        @Override
        public void write(int arg0) throws IOException {
        }
    }

    private class MockServletInputStream extends ServletInputStream {
        private int i = 0;
        private byte[] m_bytes;

        @Override
        public int read() throws IOException {
            int result = -1;
            if (i < m_bytes.length) {
                result = m_bytes[i];
                i++;
            }
            return result;
        }

        public void setBytes(byte[] bytes) {
            m_bytes = bytes;
        }
    }
}
