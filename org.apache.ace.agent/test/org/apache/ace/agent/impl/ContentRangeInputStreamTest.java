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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.RetryAfterException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Test cases for {@link ContentRangeInputStream}.
 */
public class ContentRangeInputStreamTest {
    static enum Failure {
        CONTENT_NOT_FOUND, SERVER_UNAVAILABLE, PARTIAL_NO_CONTENT_RANGE, PARTIAL_NON_BYTE_RANGE, PARTIAL_COMPLETE_BODY, PARTIAL_CHANGING_CONTENT_LENGTH, PARTIAL_UNKNOWN_CHUNK_SIZE;
    }

    /**
     * Stub implementation of an {@link HttpURLConnection} that returns all content in one big chunk.
     */
    private static class CompleteContentConnection extends TestHttpURLConnection {
        private final boolean m_includeLength;
        private InputStream m_stream;

        public CompleteContentConnection(String content, boolean includeLength) {
            super(content);
            m_includeLength = includeLength;
        }

        @Override
        public void disconnect() {
            m_stream = null;
        }

        @Override
        public int getContentLength() {
            return m_includeLength ? m_length : -1;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (m_stream == null) {
                m_stream = new ByteArrayInputStream(m_content.getBytes());
            }
            return m_stream;
        }

        @Override
        public int getResponseCode() throws IOException {
            return 200;
        }
    }

    /**
     * Stub implementation of an {@link HttpURLConnection} that yields various failures.
     */
    private static class FailingContentConnection extends TestHttpURLConnection {
        private final Failure m_failure;
        private InputStream m_stream;

        public FailingContentConnection(String content, Failure failure) {
            super(content);
            m_failure = failure;
        }

        @Override
        public void disconnect() {
            m_stream = null;
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public String getHeaderField(String name) {
            int rc = getResponseCode();
            if (rc == 206) {
                if (m_failure == Failure.PARTIAL_NON_BYTE_RANGE) {
                    return String.format("octets %d-%d/%d", 48, 96, m_length);
                }
                else if (m_failure == Failure.PARTIAL_CHANGING_CONTENT_LENGTH) {
                    int cl = (getRequestProperty("Range") != null) ? 1024 : m_length;
                    return String.format("bytes %d-%d/%d", 48, 96, cl);
                }
                else if (m_failure == Failure.PARTIAL_UNKNOWN_CHUNK_SIZE) {
                    return String.format("bytes %d-/*", 48);
                }
                else if (m_failure != Failure.PARTIAL_NO_CONTENT_RANGE) {
                    return String.format("bytes %d-%d/%d", 48, 96, m_length);
                }
            }
            return super.getHeaderField(name);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (m_stream == null) {
                if (m_failure == Failure.PARTIAL_UNKNOWN_CHUNK_SIZE) {
                    m_stream = new ByteArrayInputStream(m_content.substring(48).getBytes());
                } else {
                    m_stream = new ByteArrayInputStream(m_content.substring(48, 96).getBytes());
                }
            }
            return m_stream;
        }

        @Override
        public int getResponseCode() {
            if (m_failure == Failure.PARTIAL_NO_CONTENT_RANGE || m_failure == Failure.PARTIAL_NON_BYTE_RANGE || m_failure == Failure.PARTIAL_CHANGING_CONTENT_LENGTH || m_failure == Failure.PARTIAL_UNKNOWN_CHUNK_SIZE) {
                return 206;
            }
            else if (m_failure == Failure.PARTIAL_COMPLETE_BODY) {
                if (getRequestProperty("Range") != null) {
                    return 200;
                }
                return 206;
            }
            else if (m_failure == Failure.CONTENT_NOT_FOUND) {
                return 404;
            }
            else if (m_failure == Failure.SERVER_UNAVAILABLE) {
                return 503;
            }
            return 200;
        }
    }

    /**
     * Stub implementation of an {@link HttpURLConnection} that returns all content in one big chunk.
     */
    private static class PartialContentConnection extends TestHttpURLConnection {
        private final int m_chunkSize;
        private final boolean m_deferSendingTotalLength;

        private int[] m_connInfo;
        private InputStream m_partialContentStream;

        public PartialContentConnection(String content, boolean deferSendingTotalLength) {
            super(content);
            // use an odd divisor to ensure that not all chunks are the same...
            m_chunkSize = (m_length / 7);
            m_deferSendingTotalLength = deferSendingTotalLength;
        }

        @Override
        public void disconnect() {
            m_connInfo = null;
            m_partialContentStream = null;
        }

        @Override
        public int getContentLength() {
            int[] connInfo = determineNextContent();
            return (connInfo != null) ? connInfo[1] - connInfo[0] : -1;
        }

        @Override
        public String getHeaderField(String name) {
            int[] connInfo = determineNextContent();

            if ("Content-Range".equals(name)) {
                String contentLength;
                if (m_deferSendingTotalLength) {
                    contentLength = "*";
                    if ((connInfo[1] - connInfo[0]) < m_chunkSize) {
                        contentLength = Integer.toString(m_length);
                    }
                }
                else {
                    contentLength = Integer.toString(m_length);
                }
                return String.format("bytes %d-%d/%s", m_connInfo[0], m_connInfo[1], contentLength);
            }
            return super.getHeaderField(name);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            determineNextContent();
            return m_partialContentStream;
        }

        @Override
        public int getResponseCode() throws IOException {
            int[] connInfo = determineNextContent();
            if (connInfo == null) {
                return 500;
            }
            int len = connInfo[1] - connInfo[0];
            return len > 0 ? 206 : 416;
        }

        private int[] determineNextContent() {
            if (m_connInfo == null) {
                int start = 0;
                int end = m_chunkSize;

                String range = getRequestProperty("Range");
                if (range != null && range.startsWith("bytes=")) {
                    String[] parts = range.substring(6).split("-");

                    start = Integer.parseInt(parts[0]);
                    if (parts.length > 1) {
                        end = Integer.parseInt(parts[1]);
                    }
                    else {
                        end = start + m_chunkSize;
                    }
                }

                m_connInfo = new int[] { Math.min(m_length, start), Math.min(m_length, end) };
                m_partialContentStream = new ByteArrayInputStream(m_content.substring(m_connInfo[0], m_connInfo[1]).getBytes());
            }
            return m_connInfo;
        }
    }

    /** Stub implementation that simply opens all given URLs. */
    private static class TestConnectionHandler implements ConnectionHandler {
        private final URLConnection m_conn;

        public TestConnectionHandler(URLConnection conn) {
            m_conn = conn;
        }

        @Override
        public URLConnection getConnection(URL url) throws IOException {
            return m_conn;
        }
    }

    private static abstract class TestHttpURLConnection extends HttpURLConnection {
        protected final String m_content;
        protected final int m_length;

        protected TestHttpURLConnection(String content) {
            super(null /* url, not used. */);
            m_content = content;
            m_length = content.length();
        }

        @Override
        public void connect() throws IOException {
            // Nop
        }

        @Override
        public boolean usingProxy() {
            return false;
        }
    }

    private static URL m_testURL;
    private static String m_content;

    @BeforeClass
    protected static void setUpSuite() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(String.format("%03d. Hello World of Content-Range Input Stream!\n", i + 1));
        }
        m_content = sb.toString();
        m_testURL = new URL("file://nonExistingFile.txt");
    }

    private static String slurpAsStringByteForByte(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();

        int read = 0;
        do {
            read = is.read();
            if (read > 0) {
                sb.append((char) read);
            }
        }
        while (read > 0);
        return sb.toString();
    }

    private static String slurpAsStringWithBuffer(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();

        byte[] buf = new byte[64];
        int read = 0;
        do {
            read = is.read(buf);
            if (read > 0) {
                sb.append(new String(buf, 0, read));
            }
        }
        while (read > 0);
        return sb.toString();
    }

    /**
     * Tests that we call {@link InputStream#close()} multiple times.
     */
    @Test
    public void testDoubleClosedStreamOk() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(m_content, true));

        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);
        is.close(); // simulate an early close...
        is.close(); // not a problem...
    }

    /**
     * Tests that the "Range" header is correctly set.
     */
    @Test
    public void testRangeHeadersCorrectlySetOk() throws Exception {
        String content = m_content;
        PartialContentConnection conn;
        ContentRangeInputStream is;

        conn = new PartialContentConnection(content, false);
        // no offset causes no Range header to be set (initially)...
        is = new ContentRangeInputStream(new TestConnectionHandler(conn), m_testURL);

        is.read(); // read one byte...
        is.close();

        // Make sure the proper request header is NOT set...
        assertRequestHeader(conn, "Range", null);

        conn = new PartialContentConnection(content, false);
        // start at 48 bytes and return the next complete chunk...
        is = new ContentRangeInputStream(new TestConnectionHandler(conn), m_testURL, 48);

        is.read(); // read one byte...
        is.close();

        // Make sure the proper request header is set...
        assertRequestHeader(conn, "Range", "bytes=48-");

        conn = new PartialContentConnection(content, false);
        // 4752 + 48 = 4800, causing only one chunk to be returned...
        is = new ContentRangeInputStream(new TestConnectionHandler(conn), m_testURL, 48, 4752);

        is.read(); // read one byte...
        is.close();

        // Make sure the proper request header is set...
        assertRequestHeader(conn, "Range", "bytes=48-4800");
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadClosedStreamFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(m_content, true));

        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);
        is.close(); // simulate an early close...

        is.read(); // should fail!
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialContentByteForByteOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(content, true));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringByteForByte(is), content);

        // try several additional reads, which should all return -1 (= EOF)...
        int tries = 5;
        while (--tries > 0) {
            assertEquals(is.read(), -1);
        }
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialContentOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(content, true));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialEmptyContentOk() throws Exception {
        String content = "";

        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(content, true));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialFileContentByteForByteOk() throws Exception {
        File file = File.createTempFile("cris", ".tmp");
        file.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(m_content.getBytes());
        fos.close();

        ConnectionHandler handler = new TestConnectionHandler(file.toURI().toURL().openConnection());
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringByteForByte(is), m_content);

        // try several additional reads, which should all return -1 (= EOF)...
        int tries = 5;
        while (--tries > 0) {
            assertEquals(is.read(), -1);
        }
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialFileContentByteForByteWithOffsetOk() throws Exception {
        File file = File.createTempFile("cris", ".tmp");
        file.deleteOnExit();

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(m_content.getBytes());
        fos.close();

        ConnectionHandler handler = new TestConnectionHandler(file.toURI().toURL().openConnection());
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL, 48);

        assertEquals(slurpAsStringByteForByte(is), m_content.substring(48));

        // try several additional reads, which should all return -1 (= EOF)...
        int tries = 5;
        while (--tries > 0) {
            assertEquals(is.read(), -1);
        }
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadNonPartialWithoutContentLengthOk() throws Exception {
        String content = "";

        ConnectionHandler handler = new TestConnectionHandler(new CompleteContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentByteForByteOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringByteForByte(is), content);
    }

    /**
     * Tests that we cannot read partial content if the content is not available.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadPartialContentNotFoundFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.CONTENT_NOT_FOUND));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentWithUnknownChunkSizeOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(content, Failure.PARTIAL_UNKNOWN_CHUNK_SIZE));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content.substring(48));
    }

    /**
     * Tests that we cannot read partial content if the server is not available.
     */
    @Test(expectedExceptions = RetryAfterException.class)
    public void testReadPartialContentServerUnavailableFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.SERVER_UNAVAILABLE));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we cannot read partial content if the server returns a complete body.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadPartialContentWithChangingContentLengthFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.PARTIAL_CHANGING_CONTENT_LENGTH));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(new byte[1024]); // should succeed.
            is.read(new byte[1024]); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentWithChunkSizeOk() throws Exception {
        String content = m_content;

        PartialContentConnection conn = new PartialContentConnection(content, false);
        ConnectionHandler handler = new TestConnectionHandler(conn);
        // should cause chunks of 1024 bytes to be used, which means 4 complete chunks and one chunk of 704 bytes...
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL, 0, 1024);

        assertEquals(slurpAsStringWithBuffer(is), content);

        assertResponseHeader(conn, "Content-Range", "bytes 4096-4800/4800");
    }

    /**
     * Tests that we cannot read partial content if the server returns a complete body.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadPartialContentWithCompleteBodyFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.PARTIAL_COMPLETE_BODY));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(new byte[1024]); // should succeed.
            is.read(new byte[1024]); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentWithDeferredTotalLengthOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, true));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentWithOffsetAndChunkSizeOk() throws Exception {
        String content = m_content;

        PartialContentConnection conn = new PartialContentConnection(content, false);
        ConnectionHandler handler = new TestConnectionHandler(conn);
        // 4752 + 48 = 4800, causing only one chunk to be returned...
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL, 48, 4752);

        assertEquals(slurpAsStringWithBuffer(is), content.substring(48));
    }

    /**
     * Tests that we can read non-partial content and return the expected contents.
     */
    @Test
    public void testReadPartialContentWithOffsetOk() throws Exception {
        String content = m_content;

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL, 48);

        assertEquals(slurpAsStringWithBuffer(is), content.substring(48));
    }

    /**
     * Tests that we cannot read partial content if given a non-byte range value in the Content-Range header.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadPartialContentWithoutByteRangeValueFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.PARTIAL_NON_BYTE_RANGE));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we cannot read partial content without a Content-Range header.
     */
    @Test(expectedExceptions = IOException.class)
    public void testReadPartialContentWithoutContentRangeHeaderFail() throws Exception {
        ConnectionHandler handler = new TestConnectionHandler(new FailingContentConnection(m_content, Failure.PARTIAL_NO_CONTENT_RANGE));
        ContentRangeInputStream is = null;

        try {
            is = new ContentRangeInputStream(handler, m_testURL);
            is.read(); // should fail!
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialEmptyContentByteForByteOk() throws Exception {
        String content = "";

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringByteForByte(is), content);
    }

    /**
     * Tests that we can read partial content and return the expected contents.
     */
    @Test
    public void testReadPartialEmptyContentOk() throws Exception {
        String content = "";

        ConnectionHandler handler = new TestConnectionHandler(new PartialContentConnection(content, false));
        ContentRangeInputStream is = new ContentRangeInputStream(handler, m_testURL);

        assertEquals(slurpAsStringWithBuffer(is), content);
    }

    private void assertRequestHeader(HttpURLConnection conn, String property, String expected) {
        String value = conn.getRequestProperty(property);
        assertEquals(expected, value);
    }

    private void assertResponseHeader(HttpURLConnection conn, String property, String expected) {
        String value = conn.getHeaderField(property);
        assertEquals(expected, value);
    }
}
