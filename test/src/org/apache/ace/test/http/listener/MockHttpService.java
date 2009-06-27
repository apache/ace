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
package org.apache.ace.test.http.listener;

import java.util.Dictionary;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class MockHttpService implements HttpService {

    private boolean m_registerCalled = false;
    private boolean m_unregisterCalled = false;

    public HttpContext createDefaultHttpContext() {
        // TODO Auto-generated method stub
        return null;
    }

    public void registerResources(String arg0, String arg1, HttpContext arg2) throws NamespaceException {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("unchecked")
    public void registerServlet(String arg0, Servlet arg1, Dictionary arg2, HttpContext arg3) throws ServletException, NamespaceException {
        m_registerCalled = true;
    }

    public void unregister(String arg0) {
        m_unregisterCalled = true;
    }

    public boolean isRegisterCalled() {
        return m_registerCalled;
    }

    public boolean isUnregisterCalled() {
        return m_unregisterCalled;
    }
}
