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
package org.apache.ace.gogo.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.felix.service.command.Descriptor;

/**
 * Provides the commands for putting and removing scripts from the queue.
 */
public class QueueCommands {
    public final static String SCOPE = "queue";
    public final static String[] FUNCTIONS = new String[] { "put", "get" };

    private final BlockingQueue<String> m_queue = new LinkedBlockingQueue<String>();

    @Descriptor("puts a new script on the queue")
    public void put(String script) throws Exception {
        if (script == null) {
            throw new IllegalArgumentException("Script cannot be null!");
        }
        m_queue.put(script);
    }

    @Descriptor("returns the first script from the queue, if available")
    public String get() throws Exception {
        return m_queue.poll();
    }
}
