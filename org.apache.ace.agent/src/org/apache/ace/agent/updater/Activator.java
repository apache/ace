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
package org.apache.ace.agent.updater;

import java.io.InputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * This class is dynamically deployed as part of the updater bundle that executes the
 * update of the management agent. It is both the bundle activator and the interface and
 * implementation of a service that is published and invoked by the management agent.
 * Care was taken to not create import dependencies on anything other than the core
 * framework. Also, no inner classes are used, to keep all the code in a single class file.
 */
public class Activator implements BundleActivator, Runnable {
    private Object LOCK = new Object();
    private BundleContext m_context;
    private Thread m_updaterThread;
    private InputStream m_oldStream;
    private InputStream m_newStream;
    private Bundle m_agent;

    @Override
    public void start(BundleContext context) throws Exception {
        m_context = context;
        m_context.registerService(Activator.class.getName(), this, null);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        Thread thread;
        synchronized (LOCK) {
            thread = m_updaterThread;
        }    
        if (thread != null) {
            thread.join(60000);
        }
    }
    
    public void update(Bundle agent, InputStream oldStream, InputStream newStream) {
        synchronized (LOCK) {
            m_updaterThread = new Thread(this, "Apache ACE Management Agent Updater");
            m_agent = agent;
            m_oldStream = oldStream;
            m_newStream = newStream;
        }
        m_updaterThread.start();
    }
    
    @Override
    public void run() {
        try {
            m_agent.update(m_newStream);
        }
        catch (BundleException e) {
            try {
                m_agent.update(m_oldStream);
            }
            catch (BundleException e1) {
                // at this point we simply give up
                e1.printStackTrace();
            }
        }
    }
}
