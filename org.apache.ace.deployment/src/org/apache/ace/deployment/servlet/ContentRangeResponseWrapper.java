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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Wraps a HttpServletResponse to add byte range support allowing client to request partial content.
 * <p>
 * Note: this implementation does <em>not</em> strictly follow the recommendations made in RFC 2616! For example, it
 * does not ever send the "Content-Length" header, nor provide a "Resource-Length" value at any time. This is an
 * "optimization" we've added for ACE, as we do not know the content/resource length in advance, nor are willing to
 * sacrifice performance to get knowledge about this.
 * </p>
 * 
 * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35.1
 */
public class ContentRangeResponseWrapper extends HttpServletResponseWrapper {
    private final long m_requestFirstBytePos;
    private final long m_requestLastBytePos;

    private final HttpServletResponse m_response;

    public ContentRangeResponseWrapper(HttpServletRequest request, HttpServletResponse response) throws IOException {
        super(response);

        assert request != null;
        assert response != null;

        m_response = response;

        long[] requestRange = getRequestRange(request);
        if (requestRange != null) {
            m_requestFirstBytePos = requestRange[0];
            m_requestLastBytePos = requestRange[1];
        }
        else {
            m_requestFirstBytePos = 0;
            m_requestLastBytePos = Long.MAX_VALUE;
        }

        boolean streamAll = (m_requestFirstBytePos == 0) && (m_requestLastBytePos == Long.MAX_VALUE);

        m_response.setHeader("Accept-Ranges", "bytes");
        if (m_requestFirstBytePos < m_requestLastBytePos) {
            if (streamAll) {
                m_response.setStatus(SC_OK);
            }
            else {
                m_response.setStatus(SC_PARTIAL_CONTENT);

                StringBuilder cr = new StringBuilder("bytes ").append(m_requestFirstBytePos).append('-');
                if (m_requestLastBytePos > 0 && m_requestLastBytePos < Long.MAX_VALUE) {
                    cr.append(m_requestLastBytePos);
                }
                cr.append("/*"); // unknown instance length...
                m_response.setHeader("Content-Range", cr.toString());
            }
        }
        else {
            m_response.setStatus(SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        }
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        final ServletOutputStream delegate = m_response.getOutputStream();

        return new ServletOutputStream() {
            /** keeps the actual number of bytes written by our caller... */
            private final AtomicLong m_written = new AtomicLong(0L);

            @Override
            public void write(int b) throws IOException {
                // We only need to buffer the relevant bytes since we keep track of the instance length in the counter.
                long written = m_written.getAndIncrement();
                if (written >= m_requestFirstBytePos && written <= m_requestLastBytePos) {
                    delegate.write(b);
                }
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }

            @Override
            public boolean isReady() {
                return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener l) {
                delegate.setWriteListener(l);
            }
        };
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
