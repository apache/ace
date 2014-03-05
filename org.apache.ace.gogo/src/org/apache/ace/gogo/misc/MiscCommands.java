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
package org.apache.ace.gogo.misc;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Function;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public class MiscCommands {

    public final static String SCOPE = "misc";
    public final static String[] FUNCTIONS = new String[] { "shutdown", "sleep", "time" };

    private volatile BundleContext m_context;

    @Descriptor("report the time the execution of one or more given function(s) took")
    public void time(CommandSession session, Function[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: time {op1} ... {opN}");
        }
        long start = System.currentTimeMillis();
        for (Function func : args) {
            func.execute(session, null);
        }
        long diff = System.currentTimeMillis() - start;

        // Try to format into a little more readable units...
        double time = diff;
        String unit = "msec";
        if (time > 1000.0) {
            time = time / 1000.0;
            unit = "sec";
        }
        session.getConsole().printf("execution took %.3f %s.%n", time, unit);
    }

    @Descriptor("let the thread sleep")
    public void sleep(long delay) {
        try {
            Thread.sleep(delay);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Descriptor("schedules a framework shutdown")
    public void shutdown(long delay) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    m_context.getBundle(0).stop();
                }
                catch (BundleException e) {
                    e.printStackTrace();
                }
            }
        }, delay);
    }
}
