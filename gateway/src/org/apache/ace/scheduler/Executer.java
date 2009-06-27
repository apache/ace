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
package org.apache.ace.scheduler;

import java.util.Timer;
import java.util.TimerTask;

/**
 * This class wraps a <code>Runnable</code> in a <code>TimerTask</code> that allows
 * it to be periodically run and to be stopped as soon as possible.
 */
public class Executer extends TimerTask {
    private final Timer m_timer = new Timer();
    private final Runnable m_task;
    private boolean m_stop = false;
    private boolean m_stopped = true;

    /**
     * Creates a new instance of this class.
     *
     * @param task The task that should be periodically run.
     */
    public Executer(Runnable task) {
        m_task = task;
    }

    /**
     * Start executing the task repeatedly with an interval as specified.
     *
     * @param interval The interval between executions of the task, in milliseconds.
     */
    void start(long interval) {
        if (interval > 0) {
            m_timer.schedule(this, 0, interval);
        }
    }

    /**
     * Stop periodically executing this task. If the task is currently executing it
     * will never be run again after the current execution, otherwise it will simply
     * never run (again).
     */
    void stop() {
        synchronized (m_timer) {
            if (!m_stop) {
                m_stop = true;
                cancel();
                m_timer.cancel();
            }

            boolean interrupted = false;
            while (!m_stopped) {
                try {
                    m_timer.wait();
                }
                catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void run() {
        synchronized (m_timer) {
            m_stopped = false;
            if (m_stop) {
                m_stopped = true;
                m_timer.notifyAll();
                return;
            }
        }
        try {
            m_task.run();
        }
        catch (Exception e) {
            // TODO we should log this somehow
        }
        synchronized (m_timer) {
            m_stopped = true;
            m_timer.notifyAll();
        }
    }
}