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

import org.apache.ace.range.SortedRangeSet;
import org.apache.ace.repository.RepositoryReplication;

public class RepositoryReplicationServlet extends RepositoryServletBase<RepositoryReplication> {
    private static final long serialVersionUID = 1L;

    public RepositoryReplicationServlet() {
        super(RepositoryReplication.class);
    }

    @Override
    public String getServletInfo() {
        return "Apache ACE Repository Replication Servlet";
    }

    @Override
    protected InputStream doCheckout(RepositoryReplication repo, long version) throws IllegalArgumentException, IOException {
        return repo.get(version);
    }

    @Override
    protected boolean doCommit(RepositoryReplication repo, long version, InputStream data) throws IllegalArgumentException, IOException {
        return repo.put(data, version);
    }

    @Override
    protected String getCheckoutCommand() {
        return "/get";
    }

    @Override
    protected String getCommitCommand() {
        return "/put";
    }

    @Override
    protected SortedRangeSet getRange(RepositoryReplication repo) throws IOException {
        return repo.getRange();
    }
}
