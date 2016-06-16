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
package org.apache.ace.agent;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * Represents the result of a download task.
 * 
 */
@ConsumerType
public interface DownloadResult {
    /**
     * Returns an input stream to the downloaded result.
     * 
     * @return an input stream, can be <code>null</code> if the download was unsuccessful.
     */
    InputStream getInputStream() throws IOException;

    /**
     * @return <code>true</code> if the download is complete, <code>false</code> if not.
     */
    boolean isComplete();
}
