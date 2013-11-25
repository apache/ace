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

import static org.apache.ace.agent.impl.ConnectionUtil.DEFAULT_RETRY_TIME;
import static org.apache.ace.agent.impl.ConnectionUtil.HTTP_RETRY_AFTER;
import static org.apache.ace.agent.impl.ConnectionUtil.handleIOException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.RetryAfterException;

/**
 * Abstraction for {@link HttpURLConnection}s that might use content range headers, or partial responses, to return the
 * contents of an URL.
 * <p>
 * This implementation is capable of handling partial content requests, using the HTTP 216 response code, and tries to
 * follow the recommendations as specified in RFC 2616 as closely as possible. This implementation deviates from the RFC
 * by allowing the Content-Range header to be lacking both the last-byte-pos and a resource-length, as used in
 * {@link ContentRangeResponseWrapper}. This is an optimization that is used for ACE as we do not want to, nor can, know
 * the exact length of some of the streams we're sending.
 * </p>
 * 
 * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.16
 */
class ContentRangeInputStream extends InputStream {
    private static final String HDR_CONTENT_RANGE = "Content-Range";
    private static final String HDR_RANGE = "Range";

    private static final String BYTES = "bytes";
    private static final String BYTES_ = BYTES.concat(" ");

    private static final int SC_OK = 200;
    private static final int SC_PARTIAL_CONTENT = 206;
    private static final int SC_RANGE_NOT_SATISFIABLE = 416;
    private static final int SC_SERVICE_UNAVAILABLE = 503;

    private static final int ST_EOF = -1;
    private static final int ST_INITIAL = 0;
    private static final int ST_OPEN = 1;
    private static final int ST_CLOSED = 2;

    private final ConnectionHandler m_handler;
    private final URL m_url;
    private final int m_chunkSize;

    // see ST_* constants...
    private volatile int m_state;
    private volatile URLConnection m_conn;
    // administration...
    private volatile long m_readTotal;
    private volatile long m_readChunk;
    private volatile long[] m_contentInfo;

    /**
     * Creates a new {@link ContentRangeInputStream} instance.
     * 
     * @param handler
     *            the connection handler to use, cannot be <code>null</code>;
     * @param url
     *            the URL to connect to, cannot be <code>null</code>.
     */
    public ContentRangeInputStream(ConnectionHandler handler, URL url) {
        this(handler, url, 0L);
    }

    /**
     * Creates a new {@link ContentRangeInputStream} instance.
     * 
     * @param handler
     *            the connection handler to use, cannot be <code>null</code>;
     * @param url
     *            the URL to connect to, cannot be <code>null</code>;
     * @param startOffset
     *            the starting offset to start reading from, >= 0.
     */
    public ContentRangeInputStream(ConnectionHandler handler, URL url, long startOffset) {
        this(handler, url, startOffset, -1);
    }

    /**
     * Creates a new {@link ContentRangeInputStream} instance.
     * 
     * @param handler
     *            the connection handler to use, cannot be <code>null</code>;
     * @param url
     *            the URL to connect to, cannot be <code>null</code>;
     * @param startOffset
     *            the starting offset to start reading from, >= 0;
     * @param chunkSize
     *            the number of bytes to request in each chunk, use <tt>-1</tt> to read as many bytes as possible in
     *            each chunk.
     */
    public ContentRangeInputStream(ConnectionHandler handler, URL url, long startOffset, int chunkSize) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null!");
        }
        if (url == null) {
            throw new IllegalArgumentException("URL cannot be null!");
        }
        m_handler = handler;
        m_url = url;

        m_state = ST_INITIAL;
        m_readTotal = startOffset;
        m_chunkSize = chunkSize;
    }

    @Override
    public int available() throws IOException {
        assertOpen();

        if (!prepareNextChunk()) {
            return 0;
        }

        long chunkSize = m_contentInfo[0];
        if (chunkSize < 0) {
            // size not available (yet)...
            return 0;
        }
        return (int) ((chunkSize - m_readChunk) & 0xFFFFFFFFL);
    }

    @Override
    public void close() throws IOException {
        if (m_state != ST_CLOSED) {
            m_state = ST_CLOSED;

            closeChunk();
        }
    }

    @Override
    public int read() throws IOException {
        assertOpen();

        if (!prepareNextChunk()) {
            return -1;
        }

        InputStream is = getInputStream();

        int result = is.read();
        if (result > 0) {
            m_readChunk++;
            m_readTotal++;
        }
        // End of chunk?!
        if ((m_contentInfo[0] > 0) && (m_readChunk >= m_contentInfo[0])) {
            closeChunk();
        }

        return result;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden for efficiency reasons.
     * </p>
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        assertOpen();

        if (!prepareNextChunk()) {
            return -1;
        }

        InputStream is = getInputStream();

        int read = is.read(b, off, len);
        if (read >= 0) {
            m_readChunk += read;
            m_readTotal += read;
        }
        // End of chunk?!
        if ((m_contentInfo[0] > 0) && (m_readChunk >= m_contentInfo[0])) {
            closeChunk();
        }
        return read;
    }

    /**
     * Adds the HTTP-Range header to a given URL connection.
     * 
     * @param conn
     *            the URL connection to add the HTTP-Range header to, cannot be <code>null</code>.
     */
    private void applyRangeHeader(URLConnection conn) {
        if (m_readTotal > 0L || m_chunkSize > 0) {
            if (conn instanceof HttpURLConnection) {
                String rangeHeader;
                if (m_chunkSize > 0) {
                    rangeHeader = String.format("%s=%d-%d", BYTES, m_readTotal, (m_readTotal + m_chunkSize));
                }
                else {
                    rangeHeader = String.format("%s=%d-", BYTES, m_readTotal);
                }
                conn.setRequestProperty(HDR_RANGE, rangeHeader);
            }
            else {
                // Non-HTTP connection, skip the first few bytes when calling this method for the first time...
                if (m_contentInfo == null) {
                    long skip = m_readTotal;
                    ConnectionUtil.skip(conn, skip);
                }
            }
        }
    }

    /**
     * Verifies that this stream (and the underlying URL connection) is open.
     * 
     * @throws IOException
     *             in case this stream is already closed.
     */
    private void assertOpen() throws IOException {
        if (m_state == ST_CLOSED) {
            throw new IOException("Trying to read from closed stream!");
        }
        else if (m_state != ST_EOF) {
            m_state = ST_OPEN;
        }
    }

    /**
     * Closes the current chunk-connection, if necessary.
     */
    private void closeChunk() {
        if (m_conn != null) {
            m_conn = ConnectionUtil.close(m_conn);
            m_readChunk = 0;
        }
    }

    /**
     * @return <code>true</code> if there is content remaining to be read, <code>false</code> otherwise.
     */
    private boolean contentRemaining() {
        int state = m_state;
        if (state == ST_EOF) {
            return false;
        }
        else if (m_contentInfo == null) {
            // no information yet about the content, so we must read it first...
            return true;
        }
        long totalSize = m_contentInfo[1];
        if ((totalSize > 0L) && (m_readTotal >= totalSize)) {
            m_state = ST_EOF;
            return false;
        }
        return true;
    }

    /**
     * @param conn
     *            the URL connection to get the range information from, cannot be <code>null</code>.
     * @return an array of two elements containing: current chunk size & total size (in bytes).
     * @throws IOException
     *             in case of I/O problems or unexpected content.
     */
    private long[] getContentRangeInfo(URLConnection conn) throws IOException {
        if (conn instanceof HttpURLConnection) {
            return getHttpContentRangeInfo((HttpURLConnection) conn);
        }

        long totalBytes = conn.getContentLength();
        return new long[] { totalBytes, totalBytes };
    }

    private long[] getHttpContentRangeInfo(HttpURLConnection conn) throws IOException {
        int rc;
        try {
            rc = conn.getResponseCode();
        }
        catch (IOException exception) {
            rc = handleIOException(conn);
        }

        if (rc == SC_OK) {
            // Non-chunked response...
            if (m_readTotal > 0) {
                // this is "bad", as we've read some parts and we cannot tell the consumer of this stream that this is
                // happening...
                throw new IOException("Server returned complete content instead of (requested) partial.");
            }

            long totalBytes = conn.getContentLength();

            return new long[] { totalBytes, totalBytes };
        }
        else if (rc == SC_PARTIAL_CONTENT) {
            // Chunked response, see how many bytes we've got to read for it...
            String contentRange = conn.getHeaderField(HDR_CONTENT_RANGE);
            if (contentRange == null) {
                throw new IOException("Server returned no Content-Range for partial content");
            }

            return parseContentRangeHeader(contentRange);
        }
        else if (rc == SC_RANGE_NOT_SATISFIABLE) {
            // Range not satisfiable, we might already have completed the download?
            String contentRange = conn.getHeaderField(HDR_CONTENT_RANGE);
            if (contentRange != null) {
                return parseContentRangeHeader(contentRange);
            }

            // fall through, we cannot handle this...
        }
        else if (rc == SC_SERVICE_UNAVAILABLE) {
            // Service is unavailable, throw an exception to try it again later...
            int retry = ((HttpURLConnection) conn).getHeaderFieldInt(HTTP_RETRY_AFTER, DEFAULT_RETRY_TIME);

            throw new RetryAfterException(retry);
        }

        throw new IOException("Unknown/unexpected status code: " + rc);
    }

    /**
     * @return the current input stream, never <code>null</code>.
     * @throws IOException
     *             in case of I/O problems accessing the input stream.
     */
    private InputStream getInputStream() throws IOException {
        try {
            return m_conn.getInputStream();
        }
        catch (IOException exception) {
            handleIOException(m_conn);
            closeChunk();
            throw exception;
        }
    }

    /**
     * Parses a Content-Range header, which should be a byte-range specification of the content to expect.
     * 
     * @param value
     *            the Content-Range header value to parse, cannot be <code>null</code>.
     * @return an array with two elements, the first indicating the length of the current chunk, and the second the
     *         total length of the content.
     * @throws IOException
     *             in case a non-byte Content-Range header value was given.
     */
    private long[] parseContentRangeHeader(String value) throws IOException {
        if (!value.startsWith(BYTES_)) {
            throw new IOException("Server returned non-byte Content-Range " + value);
        }

        String[] parts = value.substring(6).split("/");

        long chunkSize = 0L;
        if (!"*".equals(parts[0])) {
            String[] rangeDef = parts[0].split("-");

            try {
                long start = Long.parseLong(rangeDef[0]);
                long end = Long.parseLong(rangeDef[1]);

                chunkSize = end - start;
            }
            catch (Exception exception) {
                // Ack; invalid range specified, cannot determine chunk size!
                chunkSize = -1L;
            }
        }

        long totalBytes;
        if ("*".equals(parts[1])) {
            totalBytes = -1L;
        }
        else {
            totalBytes = Long.parseLong(parts[1]);
        }

        return new long[] { chunkSize, totalBytes };
    }

    /**
     * Prepares the connection for the next chunk, if needed.
     * 
     * @return <code>true</code> if the prepare was successful (there was a next chunk to be read), <code>false</code>
     *         otherwise.
     * @throws IOException
     *             in case of I/O exception.
     */
    private boolean prepareNextChunk() throws IOException {
        if ((m_conn == null) && contentRemaining()) {
            m_conn = m_handler.getConnection(m_url);

            applyRangeHeader(m_conn);

            long[] contentInfo = getContentRangeInfo(m_conn);

            // No, not yet, update our local administration...
            if (m_contentInfo != null) {
                // verify the total size (paranoia sanity check)...
                if (m_contentInfo[1] >= 0 && contentInfo[1] >= 0 && m_contentInfo[1] != contentInfo[1]) {
                    throw new IOException("Stream size mismatch between different chunks!");
                }
            }

            m_contentInfo = contentInfo;
        }
        // Make sure there's still content remaining to be read...
        return (m_conn != null) && contentRemaining();
    }
}
