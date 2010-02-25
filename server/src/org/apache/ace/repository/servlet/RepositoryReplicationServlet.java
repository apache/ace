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
package org.apache.ace.repository.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;

import org.apache.ace.repository.RepositoryReplication;
import org.apache.ace.repository.SortedRangeSet;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;

public class RepositoryReplicationServlet extends RepositoryServletBase {
    private static final long serialVersionUID = 1L;

    @Override
    protected ServiceReference[] getRepositories(String filter) throws InvalidSyntaxException {
        return m_context.getServiceReferences(RepositoryReplication.class.getName(), filter);
    }

    @Override
    protected SortedRangeSet getRange(ServiceReference ref) throws IOException {
        RepositoryReplication repository = (RepositoryReplication) m_context.getService(ref);
        SortedRangeSet range = repository.getRange();
        m_context.ungetService(ref);
        return range;
    }

    @Override
    protected boolean doCommit(ServiceReference ref, long version, InputStream data) throws IllegalArgumentException, IOException {
        RepositoryReplication r = (RepositoryReplication) m_context.getService(ref);
        boolean result = r.put(data, version);
        m_context.ungetService(ref);
        return result;
    }

    @Override
    protected InputStream doCheckout(ServiceReference ref, long version) throws IllegalArgumentException, IOException {
        RepositoryReplication r = (RepositoryReplication) m_context.getService(ref);
        InputStream result = r.get(version);
        m_context.ungetService(ref);
        return result;
    }

    @Override
    public String getServletInfo() {
        return "LiQ Repository Replication Servlet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        // nothing special we want to do here
    }

    @Override
    protected String getCheckoutCommand() {
        return "/get";
    }

    @Override
    protected String getCommitCommand() {
        return "/put";
    }
}
