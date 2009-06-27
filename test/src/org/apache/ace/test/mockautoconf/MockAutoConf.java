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
package org.apache.ace.test.mockautoconf;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.service.deploymentadmin.spi.DeploymentSession;
import org.osgi.service.deploymentadmin.spi.ResourceProcessor;
import org.osgi.service.deploymentadmin.spi.ResourceProcessorException;

public class MockAutoConf implements ResourceProcessor{

    public void start() {
        System.err.println("MockAutoConf started.");
    }

    public void stop() {
        System.err.println("MockAutoConf stopped.");
    }

    public void begin(DeploymentSession session) {
        System.err.println("Called begin(...)");
        // TODO Auto-generated method stub

    }

    public void cancel() {
        System.err.println("Called cancel(...)");
        // TODO Auto-generated method stub

    }

    public void commit() {
        System.err.println("Called commit()");
        // TODO Auto-generated method stub

    }

    public void dropAllResources() throws ResourceProcessorException {
        System.err.println("Called dropAllResources()");
        // TODO Auto-generated method stub

    }

    public void dropped(String resource) throws ResourceProcessorException {
        System.err.println("Called dropped(" + resource + ")");
        // TODO Auto-generated method stub

    }

    public void prepare() throws ResourceProcessorException {
        System.err.println("Called prepare()");
        // TODO Auto-generated method stub

    }

    public void process(String name, InputStream stream)
            throws ResourceProcessorException {
        System.err.println("Called process(...).\nAnd here's the resource, named '" + name + "':");
        byte[] buf = new byte[1024];
        try {
            while (stream.read(buf) > 0) {
                System.err.write(buf);
            }
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.err.println("That's it!");
    }

    public void rollback() {
        System.err.println("Called rollback()");
    }
}
