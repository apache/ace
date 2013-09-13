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
package org.apache.ace.agent.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandle.DownloadProgressListener;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;
import org.apache.ace.agent.EventsHandler;
import org.apache.ace.agent.LoggingHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Testing {@link DownloadHandlerImpl}.
 */
public class DownloadHandlerTest extends BaseAgentTest {

    static class TestErrorServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
            String retry = req.getParameter("retry");
            if (retry != null) {
                ((HttpServletResponse) res).setHeader("Retry-After", retry);
            }
            int code = 500;
            String status = req.getParameter("status");
            if (status != null) {
                code = Integer.parseInt(status);
            }
            ((HttpServletResponse) res).sendError(code, "You asked for it");
        }
    }

    private TestWebServer m_webServer;
    private URL m_200url;
    private File m_200file;
    private String m_200digest;

    private URL m_404url;
    private URL m_503url;

    private AgentContextImpl m_agentContextImpl;
    private AgentContext m_agentContext;

    @BeforeTest
    public void setUpOnceAgain() throws Exception {
        int port = 8883;

        m_200url = new URL("http://localhost:" + port + "/testfile.txt");
        m_404url = new URL("http://localhost:" + port + "/error?status=404");
        m_503url = new URL("http://localhost:" + port + "/error?status=503&retry=500");

        File dataLocation = new File("generated");

        m_200file = new File(dataLocation, "testfile.txt");

        DigestOutputStream dos = new DigestOutputStream(new FileOutputStream(m_200file), MessageDigest.getInstance("MD5"));
        for (int i = 0; i < 10000; i++) {
            dos.write(String.valueOf(System.currentTimeMillis()).getBytes());
            dos.write(" Lorum Ipsum Lorum Ipsum Lorum Ipsum Lorum Ipsum Lorum Ipsum\n".getBytes());
        }
        dos.close();
        m_200digest = new BigInteger(dos.getMessageDigest().digest()).toString();

        m_webServer = new TestWebServer(port, "/", "generated");
        m_webServer.addServlet(new TestErrorServlet(), "/error");
        m_webServer.start();

        m_agentContextImpl = mockAgentContext();
        m_agentContext = m_agentContextImpl;

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        m_agentContextImpl.setHandler(ScheduledExecutorService.class, executorService);
        m_agentContextImpl.setHandler(EventsHandler.class, new EventsHandlerImpl(mockBundleContext()));
        m_agentContextImpl.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        m_agentContextImpl.setHandler(LoggingHandler.class, new LoggingHandlerImpl());
        m_agentContextImpl.setHandler(DownloadHandler.class, new DownloadHandlerImpl(dataLocation));

        m_agentContextImpl.start();
        replayTestMocks();
    }

    @AfterTest
    public void tearDownOnceAgain() throws Exception {
        m_agentContextImpl.stop();
        m_webServer.stop();
        verifyTestMocks();
    }

    @Test
    public void testSuccessful_noresume_result() throws Exception {
        DownloadHandler downloadHandler = m_agentContext.getHandler(DownloadHandler.class);

        DownloadResult result = downloadHandler.getHandle(m_200url).startAndAwaitResult(10, TimeUnit.SECONDS);
        assertSuccessFul(result, 200, m_200digest);
    }

    @Test
    public void testSuccessful_resume_result() throws Exception {
        DownloadHandler downloadHandler = m_agentContext.getHandler(DownloadHandler.class);

        final AtomicReference<DownloadResult> resultRef = new AtomicReference<DownloadResult>();
        final CountDownLatch latch = new CountDownLatch(1);

        final DownloadHandle handle = downloadHandler.getHandle(m_200url);
        handle.start(new DownloadProgressListener() {
            @Override
            public void progress(long read, long total) {
                handle.stop();
            }

            @Override
            public void completed(DownloadResult downloadResult) {
                resultRef.set(downloadResult);
                latch.countDown();
            }
        });

        latch.await(5, TimeUnit.SECONDS);

        DownloadResult result = resultRef.get();

        assertStopped(result, 200);
        assertSuccessFul(handle.startAndAwaitResult(Integer.MAX_VALUE, TimeUnit.SECONDS), 206, m_200digest);
    }

    @Test
    public void testFailed404_noresume_result() throws Exception {
        DownloadHandler downloadHandler = m_agentContext.getHandler(DownloadHandler.class);

        DownloadResult result = downloadHandler.getHandle(m_404url).startAndAwaitResult(10, TimeUnit.SECONDS);
        assertFailed(result, 404);
    }

    @Test
    public void testFailed503_noresume_result() throws Exception {
        DownloadHandler downloadHandler = m_agentContext.getHandler(DownloadHandler.class);

        DownloadHandle handle = downloadHandler.getHandle(m_503url);

        DownloadResult result = handle.startAndAwaitResult(10, TimeUnit.SECONDS);
        assertFailed(result, 503);

        result = handle.startAndAwaitResult(10, TimeUnit.SECONDS);
        assertFailed(result, 503);
    }

    private static void assertSuccessFul(final DownloadResult result, int statusCode, String digest) throws Exception {
        assertEquals(result.getState(), DownloadState.SUCCESSFUL, "Expected state SUCCESSFUL after succesful completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after successful completion");
        assertNotNull(result.getInputStream(), "Expected non null file after successful completion");
        assertNull(result.getCause(), "Excpected null cause after successful completion");
        assertEquals(getDigest(result.getInputStream()), digest, "Expected same digest after successful completion");
    }

    private static void assertFailed(final DownloadResult result, int statusCode) throws Exception {
        assertEquals(result.getState(), DownloadState.FAILED, "DownloadState must be FAILED after failed completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after failed completion");
        assertNull(result.getInputStream(), "File must not be null after failed completion");
    }

    private static void assertStopped(final DownloadResult result, int statusCode) throws Exception {
        assertEquals(result.getState(), DownloadState.STOPPED, "DownloadState must be STOPPED after stopped completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after stopped completion");
        assertNull(result.getInputStream(), "File must not be null after failed download");
        assertNull(result.getCause(), "Excpected cause to null null after stopped completion");
    }

    private static String getDigest(InputStream is) throws Exception {
        DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("MD5"));
        while (dis.read() != -1) {
        }
        dis.close();
        return new BigInteger(dis.getMessageDigest().digest()).toString();
    }
}
