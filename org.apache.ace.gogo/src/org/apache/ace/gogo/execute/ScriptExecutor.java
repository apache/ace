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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;

public class ScriptExecutor {
    private final ScriptTask m_task;
    private final long m_delay;

    private volatile DependencyManager m_dm;
    private volatile Component m_component;
    private volatile Timer m_timer;

    public ScriptExecutor(String scriptPath, long delay) {
        m_task = new ScriptTask(scriptPath);
        m_delay = delay;
    }

    /**
     * @return an array with all components that need to be injected with dependencies.
     */
    protected Object[] getInstances() {
        return new Object[] { this, m_task };
    }

    /**
     * Called by Felix DM.
     */
    protected void start() throws Exception {
        m_timer = new Timer();
        m_timer.schedule(m_task, m_delay);
    }

    /**
     * Called by Felix DM.
     */
    protected void stop() {
        m_timer.cancel();
        m_timer = null;
    }

    /**
     * Called when the script is executed and this service needs to be removed.
     */
    void complete() {
        m_dm.remove(m_component);
    }

    final class ScriptTask extends TimerTask {
        private final String m_scriptPath;
        // Injected by Felix DM...
        private volatile CommandProcessor m_processor;

        public ScriptTask(String scriptPath) {
            m_scriptPath = scriptPath;
        }

        @Override
        public void run() {
            CommandSession session = null;
            BufferedReader reader = null;
            String line;

            try {
                reader = new BufferedReader(new FileReader(new File(m_scriptPath)));
                StringBuilder builder = new StringBuilder();
                while ((line = reader.readLine()) != null)
                    builder.append(line).append("\n");

                session = m_processor.createSession(System.in, System.out, System.err);
                session.execute(builder.toString());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                if (session != null) {
                    session.close();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                complete();
            }
        }
    }
}
