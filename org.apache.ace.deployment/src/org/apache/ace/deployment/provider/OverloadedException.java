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
package org.apache.ace.deployment.provider;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Exception that indicates that the deployment provider is overloaded.
 * Callers that receive this exception should 
 */
@ProviderType
public class OverloadedException extends RuntimeException {

    private static final long serialVersionUID = 915400242733422258L;

    private final int m_backoffTime;
    
    /**
     * Exception that indicates that the caller should try again after at least the specified backoffTime
     * 
     * @param message the error message
     * @param backoffTime the requested backoff time in seconds
     */
    public OverloadedException(String message, int backoffTime) {
        super(message);
        m_backoffTime = backoffTime;
    }
    
    /**
     * Returns the time to "back off" from trying again.
     * 
     * @return a back off time, in seconds.
     */
    public int getBackoffTime() {
        return m_backoffTime;
    }
}
