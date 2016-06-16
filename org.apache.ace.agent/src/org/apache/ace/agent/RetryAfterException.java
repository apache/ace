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

import org.osgi.annotation.versioning.ProviderType;


/**
 * Exception that indicates that the upstream server responded with a Retry-After.
 */
@ProviderType
public class RetryAfterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int m_seconds;

    public RetryAfterException(int seconds) {
        super("Server too busy. Retry after " + seconds + " seconds");
        m_seconds = seconds;
    }

    /**
     * Returns the time to "back off" from accessing the server.
     * 
     * @return a back off time, in seconds.
     */
    public int getBackoffTime() {
        return m_seconds;
    }
}
