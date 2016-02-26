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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.provider.DeploymentProvider;
import org.apache.ace.deployment.util.test.TestProvider;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Unit tests for the deployment admin stream.
 */
public class StreamTest {
    private static final Version V1_0_0 = new Version(1, 0, 0);
    private static final int COPY_BUFFER_SIZE = 4096;

    private StreamGeneratorImpl m_generator;
    private TestProvider m_provider;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_generator = new StreamGeneratorImpl();
        m_provider = new TestProvider();

        m_provider.addData("A1.jar", "A1", FileUtils.createEmptyBundle("org.apache.ace.test.bundle.A1", V1_0_0).toURI().toURL(), "1.0.0", true);
        m_provider.addData("A2.jar", "A2", FileUtils.createEmptyBundle("org.apache.ace.test.bundle.A2", V1_0_0).toURI().toURL(), "1.0.0", false);
        m_provider.addData("A3.jar", "A3", FileUtils.createEmptyBundle("org.apache.ace.test.bundle.A3", V1_0_0).toURI().toURL(), "1.0.0", true);
        TestUtils.configureObject(m_generator, DeploymentProvider.class, m_provider);
        TestUtils.configureObject(m_generator, LogService.class);
        TestUtils.configureObject(m_generator, ConnectionFactory.class, new MockConnectionFactory());
    }

    public static void main(String[] args) {
        final InputStream[] streams = new InputStream[300];
        for (int i = 1; i <= 250; i++) {
            final String id = "target-" + i;
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
                            fail(e.getMessage());
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
                            fail(e.getMessage());
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
                            fail(e.getMessage());
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
                            fail(e.getMessage());
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
                            fail(e.getMessage());
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
                                streams[i] = new URL("http://127.0.0.1:" + TestConstants.PORT + "/data/target-" + (i + 1) + "/versions/1.0.0").openStream();
                            }
                            int in = streams[i].read();
                            if (in == -1) {
                                System.out.println(5);
                                done++;
                            }
                        }
                        catch (IOException e) {
                            fail(e.getMessage());
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * The specification requires the stream to be readable by JarInputStream (114.3) so make sure it is.
     */
    public void isJarInputStreamReadable() throws Exception {
        isJarInputStreamReadable(new JarInputStream(m_generator.getDeploymentPackage("test", "1.0.0")), false);
        isJarInputStreamReadable(new JarInputStream(m_generator.getDeploymentPackage("test", "0.0.0", "1.0.0")), true);
    }

    private void isJarInputStreamReadable(JarInputStream jis, boolean fixPackage) throws Exception {
        assertNotNull(jis, "We should have got an input stream for this deployment package.");

        Manifest m = jis.getManifest();
        assertNotNull(m, "The stream should contain a valid manifest.");

        Attributes att = m.getMainAttributes();
        assertEquals(att.getValue("DeploymentPackage-SymbolicName"), "test");
        assertEquals(att.getValue("DeploymentPackage-Version"), "1.0.0");

        if (fixPackage) {
            assertEquals(att.getValue("DeploymentPackage-FixPack"), "[0.0.0,1.0.0)");
        }
        else {
            assertNull(att.getValue("DeploymentPackage-FixPack"));
        }

        HashSet<String> names = new HashSet<>();
        JarEntry e = jis.getNextJarEntry();
        while (e != null) {
            String name = e.getName();
            names.add(name);

            if (fixPackage && name.equals("A2.jar")) {
                assertEquals(e.getAttributes().getValue("DeploymentPackage-Missing"), "true");
            }
            // we could check the name here against the manifest
            // and make sure we actually get what was promised
            Attributes a = m.getAttributes(name);
            assertNotNull(a, "The stream should contain a named section for " + name + " in the manifest.");

            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int bytes = jis.read(buffer);
            while (bytes != -1) {
                bytes = jis.read(buffer);
            }

            // get the next entry, if any
            e = jis.getNextJarEntry();
        }
        if (!fixPackage) {
            assertEquals(names.size(), 3, "The stream should have contained three resources.");
        }
        else {
            assertEquals(names.size(), 2, "The stream should have contained three resources");
        }
        assertTrue(names.contains("A1.jar"), "The stream should have contained a resource called A1.jar");
        assertTrue(fixPackage ^ names.contains("A2.jar"), "The stream should have contained a resource called A2.jar");
        assertTrue(names.contains("A3.jar"), "The stream should have contained a resource called A3.jar");
    }

    /**
     * Test reading many streams sequentially.
     */
    @Test
    public void manyStreamsSequentially() {
        final int procs = Runtime.getRuntime().availableProcessors() + 1;
        final int loopCount = 50;
        final int totalReads = procs * loopCount;

        final List<Exception> failures = new ArrayList<>();

        for (int i = 0; i < totalReads; i++) {
            try {
                isJarInputStreamReadable();
            }
            catch (Exception exception) {
                failures.add(exception);
            }
        }

        assertTrue(failures.isEmpty(), "Test failed: " + failures);
    }

    /**
     * Test reading many streams concurrently.
     */
    @Test
    public void manyStreamsConcurrently() throws InterruptedException {
        final int procs = Runtime.getRuntime().availableProcessors() + 1;
        final int loopCount = 50;
        final int totalReads = procs * loopCount;

        final List<Exception> failures = new ArrayList<>();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(totalReads);

        ExecutorService e = Executors.newFixedThreadPool(procs);
        for (int i = 0; i < procs; i++) {
            e.submit(new Callable<Void>() {
                public Void call() throws Exception {
                    startLatch.await();

                    for (int i = 0; i < loopCount; i++) {
                        try {
                            isJarInputStreamReadable();
                            stopLatch.countDown();
                        }
                        catch (Exception e) {
                            failures.add(e);
                        }
                    }

                    return null;
                }
            });
        }

        // Let all threads start at the same time...
        startLatch.countDown();
        assertTrue(stopLatch.await(10, TimeUnit.SECONDS), "Not all streams were properly read?!");

        e.shutdown();
        assertTrue(e.awaitTermination(10, TimeUnit.SECONDS));

        assertTrue(failures.isEmpty(), "Test failed: " + failures);
    }

    /**
     * Mock implementation of {@link ConnectionFactory}.
     */
    static final class MockConnectionFactory implements ConnectionFactory {
        public URLConnection createConnection(URL url) throws IOException {
            return url.openConnection();
        }

        public URLConnection createConnection(URL url, User user) throws IOException {
            return createConnection(url);
        }
    }
}
