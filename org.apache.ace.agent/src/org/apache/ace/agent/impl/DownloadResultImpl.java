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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;

public class DownloadResultImpl implements DownloadResult {
    private final DownloadState m_state;
    private final File m_file;
    private final int m_code;
    private final Throwable m_cause;

    DownloadResultImpl(DownloadState state, Throwable cause, int code) {
        m_state = state;
        m_file = null;
        m_code = code;
        m_cause = cause;
    }

    DownloadResultImpl(DownloadState state, File file, int code) {
        m_state = state;
        m_file = file;
        m_code = code;
        m_cause = null;
    }

    @Override
    public DownloadState getState() {
        return m_state;
    }

    @Override
    @SuppressWarnings("resource")
    public InputStream getInputStream() throws IOException {
        return m_file != null ? new FileInputStream(m_file) : null;
    }

    @Override
    public int getCode() {
        return m_code;
    }

    @Override
    public Throwable getCause() {
        return m_cause;
    }
}
