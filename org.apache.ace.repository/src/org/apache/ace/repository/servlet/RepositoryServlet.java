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
import org.apache.ace.repository.Repository;

public class RepositoryServlet extends RepositoryServletBase<Repository> {
    private static final long serialVersionUID = 1L;
    
    public RepositoryServlet() {
        super(Repository.class);
    }

    @Override
    public String getServletInfo() {
        return "Apache ACE Repository Servlet";
    }

    @Override
    protected InputStream doCheckout(Repository repo, long version) throws IllegalArgumentException, IOException {
        return repo.checkout(version);
    }

    @Override
    protected boolean doCommit(Repository repo, long version, InputStream data) throws IllegalArgumentException, IOException {
        return repo.commit(data, version);
    }

    @Override
    protected String getCheckoutCommand() {
        return "/checkout";
    }

    @Override
    protected String getCommitCommand() {
        return "/commit";
    }

    @Override
    protected SortedRangeSet getRange(Repository repo) throws IOException {
        return repo.getRange();
    }
}