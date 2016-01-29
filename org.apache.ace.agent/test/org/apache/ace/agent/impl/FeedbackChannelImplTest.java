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

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.agent.ConnectionHandler;
import org.apache.ace.agent.DiscoveryHandler;
import org.apache.ace.agent.IdentificationHandler;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.agent.testutil.TestWebServer;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;
import org.apache.ace.range.SortedRangeSet;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link FeedbackChannelImpl}.
 */
public class FeedbackChannelImplTest extends BaseAgentTest {

    private static final int PORT = 8884;

    private AgentContextImpl m_agentContext;
    private TestWebServer m_webServer;
    private FeedbackChannelImpl m_feedbackChannelImpl;

    static class TestSendFeedbackServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        List<Event> m_events = new ArrayList<>();

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
            resp.setContentType("text/plain");
            BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));
            String eventString;
            while ((eventString = reader.readLine()) != null) {
                Event event = new Event(eventString);
                m_events.add(event);
            }
            resp.setStatus(200);
        }
    }

    static class TestQueryFeedbackServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            String targetID = request.getParameter("tid");
            long logID = Long.parseLong(request.getParameter("logid"));
            response.getOutputStream().print(new Descriptor(targetID, logID, new SortedRangeSet(new long[0])).toRepresentation());
            response.setStatus(200);
        }
    }

    @BeforeMethod
    public void setUpAgain(Method method) throws Exception {
        // this setup is needed because a real Feedbackstore is initialized in the constructor.
        m_agentContext = mockAgentContext();

        URL serverURL = new URL("http://localhost:" + PORT + "/");

        m_webServer = new TestWebServer(PORT, "/", "generated");
        m_webServer.start();

        DiscoveryHandler discoveryHandler = addTestMock(DiscoveryHandler.class);
        expect(discoveryHandler.getServerUrl()).andReturn(serverURL).anyTimes();

        IdentificationHandler identificationHandler = addTestMock(IdentificationHandler.class);
        expect(identificationHandler.getAgentId()).andReturn("identification").anyTimes();

        replayTestMocks();
        m_agentContext.setHandler(DiscoveryHandler.class, discoveryHandler);
        m_agentContext.setHandler(IdentificationHandler.class, identificationHandler);
        m_agentContext.setHandler(ConnectionHandler.class, new ConnectionHandlerImpl());
        m_feedbackChannelImpl = new FeedbackChannelImpl(m_agentContext, "test");
        m_agentContext.start();
    }

    @AfterTest
    public void tearDownAgain() throws Exception {
        m_webServer.stop();
        m_agentContext.stop();
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testSendFeedback() throws Exception {
        TestSendFeedbackServlet sendServlet = new TestSendFeedbackServlet();
        m_webServer.addServlet(sendServlet, "/test/send");
        TestQueryFeedbackServlet queryServlet = new TestQueryFeedbackServlet();
        m_webServer.addServlet(queryServlet, "/test/query");

        m_feedbackChannelImpl.write(1, new HashMap<String, String>());
        m_feedbackChannelImpl.sendFeedback();

        assertEquals(sendServlet.m_events.size(), 1);
    }
}
