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

package org.apache.ace.gogo.execute;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Descriptor;

/**
 * Executes a Gogo script.
 */
public class ExecuteCommands {
    public final static String SCOPE = "script";
    public final static String[] FUNCTIONS = new String[] { "execute" };

    // Injected by Felix DM...
    private volatile CommandProcessor m_processor;

    @Descriptor("executes a given Gogo script")
    public void execute(CommandSession session, String script) throws Exception {
        CommandSession newSession = m_processor.createSession(session.getKeyboard(), session.getConsole(), System.err);
        try {
            newSession.execute(script);
        }
        finally {
            newSession.close();
        }
    }
}
