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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * This class is dynamically deployed as part of the updater bundle that executes the update of the management agent. It
 * is both the bundle activator and the interface and implementation of a service that is published and invoked by the
 * management agent. Care was taken to not create import dependencies on anything other than the core framework. Also,
 * no inner classes are used, to keep all the code in a single class file.
 */
public class Activator implements BundleActivator, Runnable {
    private static final int BUFFER_SIZE = 4096;
    private Thread m_updaterThread;
    private InputStream m_oldStream;
    private InputStream m_newStream;
    private Bundle m_agent;
    private File m_oldFile;
    private File m_newFile;

    @Override
    public void start(BundleContext context) throws Exception {
        context.registerService(Activator.class.getName(), this, null);
        m_oldFile = context.getDataFile("old.jar");
        m_newFile = context.getDataFile("new.jar");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
    }

    public void update(Bundle agent, InputStream oldStream, InputStream newStream) throws IOException {
        m_updaterThread = new Thread(this, "Apache ACE Management Agent Updater");
        m_agent = agent;
        copy(oldStream, new FileOutputStream(m_oldFile));
        copy(newStream, new FileOutputStream(m_newFile));
        m_oldStream = new FileInputStream(m_oldFile);
        m_newStream = new FileInputStream(m_newFile);
        m_updaterThread.start();
    }

    public void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        try {
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
        }
        finally {
            try {
                in.close();
            }
            catch (IOException e) {
            }
            try {
                out.close();
            }
            catch (IOException e) {
            }
        }
    }

    @Override
    public void run() {
        try {
            m_agent.update(m_newStream);
        }
        catch (BundleException e) {
            try {
                m_agent.update(m_oldStream);
                m_agent.start();
                // FIXME we should probable refresh?
            }
            catch (BundleException e1) {
                // at this point we simply give up
                // and log the exceptions we got
                System.err.println("Error updating agent:");
                e.printStackTrace(System.err);
                e1.printStackTrace(System.err);
                // the best we can do is try to start the agent (again)
                try {
                    m_agent.start();
                    System.err.println("We did manage to start the agent again.");
                }
                catch (BundleException e2) {
                    e2.printStackTrace(System.err);
                }
            }
        }
    }
}
