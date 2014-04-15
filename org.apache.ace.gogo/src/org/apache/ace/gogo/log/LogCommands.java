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
package org.apache.ace.gogo.log;

import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.service.command.Descriptor;

public class LogCommands {

    public final static String SCOPE = "ace-log";
    public final static String[] FUNCTIONS = new String[] { "cleanup" };

    // Injected by Felix DM...
    private volatile LogStore m_logStore;

    @Descriptor("Apply the configured maximum to all existing logs")
    public void cleanup() throws Exception {
        m_logStore.clean();
        System.out.println("All logfiles processed");
    }

}
