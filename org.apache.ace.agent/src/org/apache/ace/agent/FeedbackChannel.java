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
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents a channel on which feedback information can be reported back to the server. An agent can configure
 * multiple feedback channels for reporting different pieces of information. By default, one feedback channel is
 * present, which is used for reporting the audit events.
 */
@ProviderType
public interface FeedbackChannel {

    /**
     * Synchronizes the current feedback with the server(s), ensuring that those servers have the same (snapshot of)
     * feedback data as the agent currently has.
     */
    void sendFeedback() throws RetryAfterException, IOException;

    /**
     * Logs a new message to this feedback channel.
     * 
     * @param type
     *            the type of the log-event to write;
     * @param properties
     *            the actual contents of the message to write, cannot be <code>null</code>.
     */
    void write(int type, Map<String, String> properties) throws IOException;
}
