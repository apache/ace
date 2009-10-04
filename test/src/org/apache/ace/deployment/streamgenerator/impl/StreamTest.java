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
package org.apache.ace.deployment.streamgenerator.impl;

import static org.apache.ace.test.utils.TestUtils.BROKEN;
import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.TestUtils;
import org.apache.ace.test.utils.deployment.TestProvider;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Unit tests for the deployment admin stream.
 */
public class StreamTest {
    private static final int COPY_BUFFER_SIZE = 4096;

    private StreamGeneratorImpl m_generator;

    private TestProvider m_provider;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_generator = new StreamGeneratorImpl();
        m_provider = new TestProvider();
        // IMPORTANT NOTE: if you run this test with Eclipse, a different base path is used
        // than when you run the tests via the console. So this test will only succeed when
        // ran in a console.
        m_provider.addData("A1.jar", "A1", new URL("file:ext/javax.servlet.jar"), "1.0.0", true);
        m_provider.addData("A2.jar", "A2", new URL("file:ext/javax.servlet.jar"), "1.0.0", false);
        m_provider.addData("A3.jar", "A3", new URL("file:ext/javax.servlet.jar"), "1.0.0", true);
        TestUtils.configureObject(m_generator, DeploymentProvider.class, m_provider);
        TestUtils.configureObject(m_generator, LogService.class);
    }

    public static void main(String[] args) {
        final InputStream[] streams = new InputStream[300];
        for (int i = 1; i <= 250; i++) {
            final String id = "gateway-" + i;
            try {
                streams[i - 1] = new URL("http://127.0.0.1:" + TestConstants.PORT + "/data/" + id + "/versions/1.0.0").openStream();
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 0; i < 50; i++) {
                        try {
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(1);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 50; i < 100; i++) {
                        try {
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(2);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 100; i < 150; i++) {
                        try {
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(3);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 150; i < 200; i++) {
                        try {
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(4);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 200; i < 250; i++) {
                        try {
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(5);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
        new Thread() {
            @Override
            public void run() {
                int done = 0;
                while (done < 50) {
                    for (int i = 250; i < 300; i++) {
                        try {
                            if (streams[i] == null) {
                                streams[i] = new URL("http://127.0.0.1:" + TestConstants.PORT + "/data/gateway-" + (i + 1) + "/versions/1.0.0").openStream();
                            }
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(5);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * The specification requires the stream to be readable by JarInputStream (114.3) so make sure it is.
     */
    @Test(groups = { UNIT })
    public void isJarInputStreamReadable() throws Exception {
        isJarInputStreamReadable(new JarInputStream(m_generator.getDeploymentPackage("test", "1.0.0")), false);
        isJarInputStreamReadable(new JarInputStream(m_generator.getDeploymentPackage("test", "0.0.0", "1.0.0")), true);
    }

    private void isJarInputStreamReadable(JarInputStream jis, boolean fixPackage) throws Exception {
        assert jis != null : "We should have got an input stream for this deployment package.";
        Manifest m = jis.getManifest();
        assert m != null : "The stream should contain a valid manifest.";
        Attributes att = m.getMainAttributes();
        assert att.getValue("DeploymentPackage-SymbolicName").equals("test");
        assert att.getValue("DeploymentPackage-Version").equals("1.0.0");
        assert (fixPackage && att.getValue("DeploymentPackage-FixPack").equals("[0.0.0,1.0.0)")) || (att.getValue("DeploymentPackage-FixPack") == null);
        HashSet<String> names = new HashSet<String>();
        JarEntry e = jis.getNextJarEntry();
        while (e != null) {
            String name = e.getName();
            names.add(name);
            if (fixPackage && name.equals("A2.jar")) {
                assert e.getAttributes().getValue("DeploymentPackage-Missing").equals("true");
            }
            // we could check the name here against the manifest
            // and make sure we actually get what was promised
            Attributes a = m.getAttributes(name);
            assert a != null : "The stream should contain a named section for " + name + " in the manifest.";

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int bytes = jis.read(buffer);
            long size = 0;
            while (bytes != -1) {
                size += bytes;
                bytes = jis.read(buffer);
            }

            // get the next entry, if any
            e = jis.getNextJarEntry();
        }
        if (!fixPackage) {
            assert names.size() == 3 : "The stream should have contained three resources.";
        }
        else {
            assert names.size() == 2 : "The stream should have contained three resources";
        }
        assert names.contains("A1.jar") : "The stream should have contained a resource called A1.jar";
        assert fixPackage ^ names.contains("A2.jar") : "The stream should have contained a resource called A2.jar";
        assert names.contains("A3.jar") : "The stream should have contained a resource called A3.jar";
    }

    /**
     * Test reading 100 streams sequentially.
     */
    @Test(groups = { UNIT, BROKEN })
    public void hundredStreamsSequentially() throws Exception {
        for (int i = 0; i < 100; i++) {
            isJarInputStreamReadable();
        }
    }

    private Exception m_failure;

    /**
     * Test reading 100 streams concurrently.
     */
    @Test(groups = { UNIT, BROKEN }) // marked broken after discussing it with Karl
    public void hundredStreamsConcurrently() throws Exception {
        ExecutorService e = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 10; i++) {
            e.execute(new Runnable() {
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        try {
                            isJarInputStreamReadable();
                        }
                        catch (Exception e) {
                            m_failure = e;
                        }
                    }
                }
            });
        }
        e.shutdown();
        e.awaitTermination(10, TimeUnit.SECONDS);

        assert m_failure == null : "Test failed: " + m_failure.getLocalizedMessage();
    }
}
