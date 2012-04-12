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

package org.apache.ace.authentication.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.apache.ace.authentication.api.AuthenticationProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

/**
 * Test cases for {@link AuthenticationServiceImpl}.
 */
public class AuthenticationServiceImplTest {
    
    private LogService m_log;

    @Before
    public void setUp() {
        m_log = mock(LogService.class);
    }

    /**
     * Tests that an exception is thrown if a null context is given.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAuthenticateFailsWithNullContext() {
        new AuthenticationServiceImpl().authenticate((Object[]) null);
    }

    /**
     * Tests that without any authentication processors, no authentication will take place.
     */
    @Test
    public void testAuthenticateFailsWithoutAuthProcessors() {
        assertNull(createAuthenticationService().authenticate("foo", "bar"));
    }

    /**
     * Tests that an exception is thrown if no context is given.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAuthenticateFailsWithoutContext() {
        new AuthenticationServiceImpl().authenticate();
    }

    /**
     * Tests that with a single authentication processors, no authentication will take place if it is the wrong context.
     */
    @Test
    public void testAuthenticateFailsWithSingleAuthProcessorAndWrongContext() {
        AuthenticationServiceImpl authService = createAuthenticationService();
        
        AuthenticationProcessor authProc = mock(AuthenticationProcessor.class);
        when(authProc.canHandle(anyString())).thenReturn(Boolean.TRUE);

        registerAuthProcessor(authService, authProc);

        assertNull(authService.authenticate("foo", "bar"));
    }

    /**
     * Tests that with multiple authentication processors, authentication will take place if it is given the correct context.
     */
    @Test
    public void testAuthenticateSucceedsWithMultipleAuthProcessors() {
        Date now = new Date();
        
        User user1 = mock(User.class);
        User user2 = mock(User.class);

        AuthenticationProcessor authProc1 = mock(AuthenticationProcessor.class);
        when(authProc1.canHandle(any())).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return (args.length == 1 && args[0] instanceof Date);
            }
        });
        when(authProc1.authenticate(Mockito.<UserAdmin>any(), eq(now))).thenReturn(user1);

        AuthenticationProcessor authProc2 = mock(AuthenticationProcessor.class);
        when(authProc2.canHandle(anyString())).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return (args.length == 1 && args[0] instanceof String);
            }
        });
        when(authProc2.authenticate(Mockito.<UserAdmin>any(), eq("foo"))).thenReturn(user2);

        AuthenticationServiceImpl authService = createAuthenticationService();

        registerAuthProcessor(authService, authProc1);
        registerAuthProcessor(authService, authProc2);

        User result = authService.authenticate("foo");
        assertNotNull(result);
        assertSame(user2, result);
        
        result = authService.authenticate(now);
        assertNotNull(result);
        assertSame(user1, result);
    }

    /**
     * Tests that with a single authentication processors, authentication will take place if it is given the correct context.
     */
    @Test
    public void testAuthenticateSucceedsWithSingleAuthProcessorAndCorrectContext() {
        AuthenticationServiceImpl authService = createAuthenticationService();

        User user = mock(User.class);

        AuthenticationProcessor authProc = mock(AuthenticationProcessor.class);
        when(authProc.canHandle(anyString())).thenReturn(Boolean.TRUE);
        when(authProc.authenticate(Mockito.<UserAdmin>any(), eq("foo"))).thenReturn(user);

        registerAuthProcessor(authService, authProc);

        assertNotNull(authService.authenticate("foo"));
    }

    /**
     * Tests that with multiple authentication processors, the correct ones are returned based on the given context.
     */
    @Test
    public void testGetProcessorsSelectsCorrectProcessorsBasedOnContext() {
        Date now = new Date();
        
        User user1 = mock(User.class);
        User user2 = mock(User.class);

        AuthenticationProcessor authProc1 = mock(AuthenticationProcessor.class);
        when(authProc1.canHandle(any())).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return (args.length == 1 && args[0] instanceof Date);
            }
        });
        when(authProc1.authenticate(Mockito.<UserAdmin>any(), eq(now))).thenReturn(user1);

        AuthenticationProcessor authProc2 = mock(AuthenticationProcessor.class);
        when(authProc2.canHandle(anyString())).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return (args.length == 1 && args[0] instanceof String);
            }
        });
        when(authProc2.authenticate(Mockito.<UserAdmin>any(), eq("foo"))).thenReturn(user2);

        AuthenticationServiceImpl authService = createAuthenticationService();

        registerAuthProcessor(authService, authProc1);
        registerAuthProcessor(authService, authProc2);

        List<AuthenticationProcessor> processors = authService.getProcessors("foo");
        assertNotNull(processors);
        assertEquals(1, processors.size());
        
        processors = authService.getProcessors(now);
        assertNotNull(processors);
        assertEquals(1, processors.size());
        
        processors = authService.getProcessors(new Object());
        assertNotNull(processors);
        assertEquals(0, processors.size());
    }

    /**
     * @return a new {@link AuthenticationServiceImpl} instance, never <code>null</code>.
     */
    private AuthenticationServiceImpl createAuthenticationService() {
        return new AuthenticationServiceImpl(m_log);
    }
    
    /**
     * @param authService
     * @param authProcessor
     */
    private void registerAuthProcessor(AuthenticationServiceImpl authService, AuthenticationProcessor authProcessor) {
        ServiceReference sr = mock(ServiceReference.class);
        authService.addAuthenticationProcessor(sr, authProcessor);
    }
}
