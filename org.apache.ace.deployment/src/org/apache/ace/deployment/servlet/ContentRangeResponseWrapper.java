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
package org.apache.ace.deployment.servlet;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Wraps a HttpServletResponse to add byte range support allowing client to request partial content.
 * 
 * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
 */
public class ContentRangeResponseWrapper extends HttpServletResponseWrapper {

    /**
     * Internal helper that Wraps a ServletOutputStream to add byte range support.
     */
    private static class ContentRangeOutputStreamWrapper extends ServletOutputStream {
        private static final int BUFFER_SIZE = 32 * 1024; // kB

        private final HttpServletResponse m_response;
        private final boolean m_streamAll;
        private final long m_requestFirstBytePos;
        private final long m_requestLastBytePos;
        private final FileOutputStream m_os;
        private final File m_file;

        private final AtomicLong m_instanceLen = new AtomicLong(0);

        public ContentRangeOutputStreamWrapper(HttpServletResponse response) throws IOException {
            this(response, 0, Long.MAX_VALUE);
        }

        public ContentRangeOutputStreamWrapper(HttpServletResponse response, long firstBytePos, long lastBytePos) throws IOException {
            this(response, firstBytePos, lastBytePos, (firstBytePos == 0 && lastBytePos == Long.MAX_VALUE));
        }

        private ContentRangeOutputStreamWrapper(HttpServletResponse response, long firstBytePos, long lastBytePos, boolean streamAll) throws IOException {
            assert response != null;
            assert firstBytePos >= 0;
            assert lastBytePos > firstBytePos;

            m_response = response;
            m_requestFirstBytePos = firstBytePos;
            m_requestLastBytePos = lastBytePos;
            m_streamAll = streamAll;

            // We use a file to buffer because Deployment Packages can be big and the current common ACE Agent case it a
            // range request for some start position up-to EOF.
            m_file = File.createTempFile("deploymentpackage", ".jar");
            m_os = new FileOutputStream(m_file);
        }

        @Override
        public void write(int b) throws IOException {
            // We only need to buffer the relevant bytes since we keep track of the instance length in the counter.
            long value = m_instanceLen.getAndIncrement();
            if (value >= m_requestFirstBytePos && value <= m_requestLastBytePos) {
                m_os.write(b);
            }
        }

        @Override
        public void close() throws IOException {
            closeQuietly(m_os);

            long instanceLength = m_instanceLen.get();
            long instanceLastBytePos = instanceLength - 1L;
            InputStream is = null;
            ServletOutputStream os = null;

            try {
                if (instanceLastBytePos < m_requestFirstBytePos) {
                    m_response.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    m_response.setHeader("Content-Range", String.format("bytes */%d", instanceLength));
                }
                else {
                    long firstBytePos = m_requestFirstBytePos;
                    long lastBytePos = instanceLastBytePos < m_requestLastBytePos ? instanceLastBytePos : m_requestLastBytePos;
                    long contentLength = lastBytePos - firstBytePos + 1;

                    m_response.setStatus(m_streamAll ? SC_OK : SC_PARTIAL_CONTENT);
                    m_response.setHeader("Content-Length", String.valueOf(contentLength));
                    if (!m_streamAll) {
                        m_response.setHeader("Content-Range", String.format("bytes %d-%d/%d", firstBytePos, lastBytePos, instanceLength));
                    }

                    byte[] buffer = new byte[BUFFER_SIZE];
                    is = new FileInputStream(m_file);
                    os = m_response.getOutputStream();

                    for (int bytesRead = is.read(buffer); bytesRead != -1; bytesRead = is.read(buffer)) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            }
            finally {
                closeQuietly(is);
                closeQuietly(os);
                m_file.delete();
            }
        }

        private static void closeQuietly(Closeable resource) throws IOException {
            if (resource != null) {
                try {
                    resource.close();
                }
                catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private final HttpServletResponse m_response;
    private final ServletOutputStream m_outputStream;

    public ContentRangeResponseWrapper(HttpServletRequest request, HttpServletResponse response) throws IOException {
        super(response);

        assert request != null;
        assert response != null;

        m_response = response;
        m_response.setHeader("Accept-Ranges", "bytes");

        // If a valid Range request is present we install the ContentRangeOutputStreamWrapper. Otherwise we do not touch
        // the response ServletOutputStream until we have to in #getOutputStream().
        ContentRangeOutputStreamWrapper wrapper = null;
        long[] requestRange = getRequestRange(request);
        if (requestRange != null) {
            wrapper = new ContentRangeOutputStreamWrapper(response, requestRange[0], requestRange[1]);
        }
        if (wrapper == null) {
            // Assume a range of "bytes=0-", which simply streams everything. This solves ACE-435...
            wrapper = new ContentRangeOutputStreamWrapper(response);
        }
        m_outputStream = wrapper;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return m_outputStream;
    }

    /**
     * Pattern that matches valid Range request headers. Note that the lastBytePos group is optional. If it is empty
     * this indicates all remaining bytes from startBytePos is requested.
     */
    private static final Pattern RANGE_REQUEST_PATTERN = Pattern.compile("^bytes=(\\d+)-(\\d+)?$");

    /**
     * Extracts and validates the Range header from a request. If the header is found but is syntactically invalid it
     * will be ignored as required by specification.
     * 
     * @param request
     *            the request to use
     * @return a long array with two elements (firstBytePos and lastBytePos), or <code>null</code> if no valid Range
     *         header was found.
     */
    private static long[] getRequestRange(HttpServletRequest request) {
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null) {
            Matcher rangeMatcher = RANGE_REQUEST_PATTERN.matcher(rangeHeader);
            if (rangeMatcher.find()) {
                long firstBytePos = Long.parseLong(rangeMatcher.group(1));
                long lastBytePos = (rangeMatcher.group(2) != null) ? Long.parseLong(rangeMatcher.group(2)) : Long.MAX_VALUE;
                if (lastBytePos >= firstBytePos) {
                    return new long[] { firstBytePos, lastBytePos };
                }
            }
        }
        return null;
    }
}
