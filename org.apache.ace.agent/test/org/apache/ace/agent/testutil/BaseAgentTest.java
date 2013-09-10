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
package org.apache.ace.agent.testutil;

import static org.easymock.EasyMock.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.AgentContextAware;
import org.apache.ace.agent.impl.AgentContextImpl;
import org.osgi.framework.BundleContext;

/**
 * Simple base class.
 */
public abstract class BaseAgentTest {
    private final Set<Object> m_mocks = new HashSet<Object>();

    protected <T> T addTestMock(Class<T> clazz) {
        T mock = createNiceMock(clazz);
        m_mocks.add(mock);
        return mock;
    }

    protected void cleanDir(File dir) {
        if (!dir.isDirectory())
            throw new IllegalStateException();
        Stack<File> dirs = new Stack<File>();
        dirs.push(dir);
        while (!dirs.isEmpty()) {
            File currentDir = dirs.pop();
            File[] files = currentDir.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    dirs.push(file);
                }
                else {
                    file.delete();
                }
            }
        }
    }

    protected void clearTestMocks() {
        m_mocks.clear();
    }

    protected File getWorkDir() {
        return new File("generated");
    }

    protected AgentContextImpl mockAgentContext() throws Exception {
        return mockAgentContext("mockAgentContext" + System.currentTimeMillis());
    }

    protected AgentContextImpl mockAgentContext(String subDir) throws Exception {
        File contextDir = new File(getWorkDir(), subDir);
        contextDir.mkdirs();
        cleanDir(contextDir);

        AgentContextImpl context = new AgentContextImpl(contextDir);
        for (Class<?> handlerClass : AgentContextImpl.KNOWN_HANDLERS) {
            if (ScheduledExecutorService.class.equals(handlerClass)) {
                // always inject a proper executor service that simply invokes synchronously...
                context.setHandler(ScheduledExecutorService.class, new SynchronousExecutorService());
            }
            else {
                setMockedHandler(context, handlerClass);
            }
        }
        return context;
    }

    protected BundleContext mockBundleContext() throws Exception {
        BundleContext result = createNiceMock(BundleContext.class);
        expect(result.createFilter(anyObject(String.class))).andReturn(null).anyTimes();
        replay(result);
        return result;
    }

    protected void replayTestMocks() {
        for (Object mock : m_mocks) {
            replay(mock);
        }
    }

    protected <T> void setMockedHandler(AgentContextImpl context, Class<T> clazz) {
        context.setHandler(clazz, addTestMock(clazz));
    }

    protected void startHandler(Object handler, AgentContext agentContext) throws Exception {
        if (handler instanceof AgentContextAware) {
            ((AgentContextAware) handler).start(agentContext);
        }
    }

    protected void stopHandler(Object handler) throws Exception {
        if (handler instanceof AgentContextAware) {
            ((AgentContextAware) handler).stop();
        }
    }

    protected void verifyTestMocks() {
        for (Object mock : m_mocks) {
            verify(mock);
        }
    }
}
