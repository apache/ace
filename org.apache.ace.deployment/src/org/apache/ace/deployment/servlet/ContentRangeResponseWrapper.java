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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

        private final HttpServletResponse m_response;
        private final long m_requestFirstBytePos;
        private final long m_requestLastBytePos;

        private final FileOutputStream m_os;
        private final File m_file;

        private long m_instanceLen = 0l;

        public ContentRangeOutputStreamWrapper(HttpServletResponse response, long firstBytePos, long lastBytePos) throws IOException {

            assert response != null;
            assert firstBytePos >= 0;
            assert lastBytePos > firstBytePos;

            m_response = response;
            m_requestFirstBytePos = firstBytePos;
            m_requestLastBytePos = lastBytePos;

            // We use a file to buffer because Deployment Packages can be big and the current common ACE Agent case it a
            // range request for some start position up-to EOF.
            m_file = File.createTempFile("deploymentpackage", ".jar");
            m_os = new FileOutputStream(m_file);
        }

        @Override
        public void write(int b) throws IOException {

            // We only need to buffer the relevant bytes since we keep track of the instance length in the counter.
            if (m_instanceLen >= m_requestFirstBytePos
                && m_instanceLen <= m_requestLastBytePos) {
                m_os.write(b);
            }
            m_instanceLen++;
        }

        @Override
        public void close() throws IOException {
            closeQuietly(m_os);

            long instanceLastBytePos = m_instanceLen - 1;
            InputStream is = null;
            ServletOutputStream os = null;

            try {
                if (instanceLastBytePos < m_requestFirstBytePos) {

                    m_response.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    m_response.setHeader("Content-Range", String.format("bytes */%d", m_instanceLen));
                }
                else {

                    long firstBytePos = m_requestFirstBytePos;
                    long lastBytePos = instanceLastBytePos < m_requestLastBytePos ? instanceLastBytePos : m_requestLastBytePos;
                    long contentLength = lastBytePos - firstBytePos + 1;

                    m_response.setStatus(SC_PARTIAL_CONTENT);
                    m_response.setHeader("Content-Length", String.valueOf(contentLength));
                    m_response.setHeader("Content-Range", String.format("bytes %d-%d/%d", firstBytePos, lastBytePos, m_instanceLen));

                    is = new BufferedInputStream(new FileInputStream(m_file));
                    os = m_response.getOutputStream();

                    int b;
                    while ((b = is.read()) != -1) {
                        os.write(b);
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
    private ServletOutputStream m_outputStream;

    public ContentRangeResponseWrapper(HttpServletRequest request, HttpServletResponse response) throws IOException {
        super(response);

        assert request != null;
        assert response != null;

        m_response = response;
        m_response.setHeader("Accept-Ranges", "bytes");

        // If a valid Range request is present we install the ContentRangeOutputStreamWrapper. Otherwise we do not touch
        // the response ServletOutputStream until we have to in #getOutputStream().
        long[] requestRange = getRequestRange(request);
        if (requestRange != null) {
            m_outputStream = new ContentRangeOutputStreamWrapper(response, requestRange[0], requestRange[1]);
        }
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        // If a ContentRangeOutputStreamWrapper is installed we return it. Otherwise we simply delegate to the original
        // response directly.
        if (m_outputStream == null) {
            return m_response.getOutputStream();
        }
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
     * @param request the request to use
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
