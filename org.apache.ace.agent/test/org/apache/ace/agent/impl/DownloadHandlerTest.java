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

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.notNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.DownloadHandle;
import org.apache.ace.agent.DownloadHandle.CompletedListener;
import org.apache.ace.agent.DownloadHandle.ProgressListener;
import org.apache.ace.agent.DownloadHandler;
import org.apache.ace.agent.DownloadResult;
import org.apache.ace.agent.DownloadState;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.osgi.service.log.LogService;
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
            if (retry != null)
                ((HttpServletResponse) res).setHeader("Retry-After", retry);
            int code = 500;
            String status = req.getParameter("status");
            if (status != null)
                code = Integer.parseInt(status);
            ((HttpServletResponse) res).sendError(code, "You asked for it");
        }
    }

    private DownloadHandler m_downloadHandler;
    private TestWebServer m_webServer;
    private URL m_200url;
    private File m_200file;
    private String m_200digest;

    private URL m_404url;
    private URL m_503url;

    @BeforeTest
    public void setUpOnceAgain() throws Exception {

        int port = 8883;

        m_200url = new URL("http://localhost:" + port + "/testfile.txt");
        m_404url = new URL("http://localhost:" + port + "/error?status=404");
        m_503url = new URL("http://localhost:" + port + "/error?status=503&retry=500");

        m_200file = new File(new File("generated"), "testfile.txt");
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

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        AgentContext agentContext = addTestMock(AgentContext.class);
        expect(agentContext.getExecutorService()).andReturn(executorService).anyTimes();

        LogService logService = addTestMock(LogService.class);
        expect(agentContext.getLogService()).andReturn(logService).anyTimes();
        logService.log(anyInt(), notNull(String.class));
        expectLastCall().anyTimes();

        replayTestMocks();
        m_downloadHandler = new DownloadHandlerImpl();
        startHandler(m_downloadHandler, agentContext);
    }

    @AfterTest
    public void tearDownOnceAgain() throws Exception {
        stopHandler(m_downloadHandler);
        verifyTestMocks();
        m_webServer.stop();
    }

    @Test
    public void testSuccessful_noresume_result() throws Exception {
        final DownloadHandle handle = m_downloadHandler.getHandle(m_200url).start();
        final DownloadResult result = handle.result();
        assertSuccessFul(result, 200, m_200digest);
    }

    @Test
    public void testSuccessful_noresume_listener() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final List<DownloadResult> holder = new ArrayList<DownloadResult>();
        final DownloadHandle handle = m_downloadHandler.getHandle(m_200url)
            .setCompletionListener(new CompletedListener() {
                @Override
                public void completed(DownloadResult result) {
                    holder.add(result);
                    latch.countDown();
                }
            }).start();
        latch.await();
        assertSuccessFul(holder.get(0), 200, m_200digest);
        assertSame(handle.result(), holder.get(0), "Await should return same result given to the completion handler.");
    }

    @Test
    public void testSuccessful_resume_result() throws Exception {
        final DownloadHandle handle = m_downloadHandler.getHandle(m_200url);
        handle.setProgressListener(new ProgressListener() {
            @Override
            public void progress(long contentLength, long progress) {
                handle.stop();
            }
        }).start();
        assertStopped(handle.result(), 200);
        assertStopped(handle.start().result(), 206);
        assertSuccessFul(handle.setProgressListener(null).start().result(), 206, m_200digest);
    }

    @Test
    public void testFailedIO_nostatus_result() throws Exception {
        DownloadHandle handle = m_downloadHandler.getHandle(m_200url, 2048);

        DownloadResult result = ((DownloadHandleImpl) handle).start(DownloadCallableImpl.FAIL_OPENCONNECTION).result();
        assertFailed(result, 0);
        assertNull(result.getHeaders());

        result = ((DownloadHandleImpl) handle).start(DownloadCallableImpl.FAIL_OPENINPUTSTREAM).result();
        assertFailed(result, 200);
        assertNotNull(result.getHeaders());

        result = ((DownloadHandleImpl) handle).start(DownloadCallableImpl.FAIL_OPENOUTPUTSTREAM).result();
        assertFailed(result, 200);
        assertNotNull(result.getHeaders());

        result = ((DownloadHandleImpl) handle).start(DownloadCallableImpl.FAIL_AFTERFIRSTWRITE).result();
        assertFailed(result, 200);
        assertNotNull(result.getHeaders());

        result = ((DownloadHandleImpl) handle).start(DownloadCallableImpl.FAIL_AFTERFIRSTWRITE).result();
        assertFailed(result, 206);
        assertNotNull(result.getHeaders());

        result = handle.start().result();
        assertSuccessFul(result, 206, m_200digest);
    }

    @Test
    public void testFailed404_noresume_result() throws Exception {
        final DownloadResult result = m_downloadHandler.getHandle(m_404url).start().result();
        assertFailed(result, 404);
    }

    @Test
    public void testFailed503_noresume_result() throws Exception {
        DownloadResult result = m_downloadHandler.getHandle(m_503url).start().result();
        assertFailed(result, 503);
        assertNotNull(result.getHeaders().get("Retry-After"), "Expected a Retry-After header from error servlet");
        assertNotNull(result.getHeaders().get("Retry-After").get(0), "Expected a Retry-After header from error servlet");
        assertEquals(result.getHeaders().get("Retry-After").get(0), "500", "Expected a Retry-After header from error servlet");
    }

    private static void assertSuccessFul(final DownloadResult result, int statusCode, String digest) throws Exception {
        assertEquals(result.getState(), DownloadState.SUCCESSFUL, "Expected state SUCCESSFUL after succesful completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after successful completion");
        assertNotNull(result.getFile(), "Expected non null file after successful completion");
        assertNotNull(result.getHeaders(), "Expected non null headers after successful completion");
        assertNull(result.getCause(), "Excpected null cause after successful completion");
        assertEquals(getDigest(result.getFile()), digest, "Expected same digest after successful completion");
    }

    private static void assertFailed(final DownloadResult result, int statusCode) throws Exception {
        assertEquals(result.getState(), DownloadState.FAILED, "DownloadState must be FAILED after failed completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after failed completion");
        assertNull(result.getFile(), "File must not be null after failed completion");
    }

    private static void assertStopped(final DownloadResult result, int statusCode) throws Exception {
        assertEquals(result.getState(), DownloadState.STOPPED, "DownloadState must be STOPPED after stopped completion");
        assertEquals(result.getCode(), statusCode, "Expected statusCode " + statusCode + " after stopped completion");
        assertNotNull(result.getHeaders(), "Expected headers not to be null after stopped completion");
        assertNull(result.getFile(), "File must not be null after failed download");
        assertNull(result.getCause(), "Excpected cause to null null after stopped completion");
    }

    private static String getDigest(File file) throws Exception {
        DigestInputStream dis = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("MD5"));
        while (dis.read() != -1) {
        }
        dis.close();
        return new BigInteger(dis.getMessageDigest().digest()).toString();
    }
}
