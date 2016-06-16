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

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Test utility that manages a Jetty webserver with a {@link DefaultServlet} that support HTTP range downloads and a
 * simple HTTP protocol dump filter. It can be extended with custom test servlets.
 */
@SuppressWarnings("restriction")
public class TestWebServer {

    static class HttpDumpFilter implements Filter {
        @Override
        public void destroy() {
            // Nop
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest hreq = (HttpServletRequest) req;
            HttpServletResponse hres = (HttpServletResponse) res;

            ResponseInfoCollector coll = new ResponseInfoCollector(hres);
            chain.doFilter(req, coll);

            StringBuilder sb = new StringBuilder();
            sb.append("> ").append(hreq.getMethod()).append(" ").append(hreq.getRequestURI()).append(" ").append(req.getProtocol()).append('\n');
            Enumeration<String> attrs = hreq.getHeaderNames();
            while (attrs.hasMoreElements()) {
                String attr = attrs.nextElement();
                sb.append("> ").append(attr).append(": ").append(hreq.getHeader(attr)).append('\n');
            }

            sb.append("< ").append(hreq.getProtocol()).append(" ").append(coll.statusCode).append(" ").append(coll.statusMessage).append('\n');
            for (String headerName : coll.headers.keySet()) {
                sb.append("< ").append(headerName).append(": ").append(coll.headers.get(headerName)).append('\n');
            }

            System.out.println(sb);
        }

        @Override
        public void init(FilterConfig config) throws ServletException {
            // Nop
        }
    }

    static class ResponseInfoCollector extends HttpServletResponseWrapper {
        long statusCode;
        String statusMessage = "";
        Map<String, String> headers = new HashMap<>();

        public ResponseInfoCollector(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void sendError(int sc) throws IOException {
            statusCode = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            statusCode = sc;
            statusMessage = msg;
            super.sendError(sc, msg);
        }

        @Override
        public void setDateHeader(String name, long date) {
            headers.put(name, new Date(date).toString());
            super.setDateHeader(name, date);
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
            super.setHeader(name, value);
        }

        @Override
        public void setIntHeader(String name, int value) {
            headers.put(name, "" + value);
            super.setIntHeader(name, value);
        }

        @Override
        public void setStatus(int sc) {
            statusCode = sc;
            super.setStatus(sc);
        }

        @Override
        public void setStatus(int sc, String sm) {
            statusCode = sc;
            statusMessage = sm;
            super.setStatus(sc, sm);
        }
    }

    private final ServletContextHandler m_contextHandler;
    private final Server m_server;

    public TestWebServer(int port, String contextPath, String basePath) throws Exception {
        m_server = new Server(port);

        m_contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        m_contextHandler.setContextPath("/");

        ServletHolder holder = new ServletHolder(new DefaultServlet());
        holder.setInitParameter("resourceBase", basePath);
        holder.setInitParameter("pathInfoOnly", "true");
        holder.setInitParameter("acceptRanges", "true");
        holder.setInitParameter("dirAllowed", "true");

        m_contextHandler.addFilter(new FilterHolder(new HttpDumpFilter()), "/*", null);
        m_contextHandler.addServlet(holder, contextPath.concat(contextPath.endsWith("/") ? "*" : "/*"));
        m_server.setHandler(m_contextHandler);
    }

    public void addServlet(Servlet servlet, String pathPsec) {
        m_contextHandler.addServlet(new ServletHolder(servlet), pathPsec);
    }

    public void start() throws Exception {
        m_server.start();
    }

    public void stop() throws Exception {
        m_server.stop();
        m_server.join();
    }
}
