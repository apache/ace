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
package org.apache.ace.client.repositoryuseradmin.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.repository.SortedRangeSet;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.test.utils.TestUtils;
import org.apache.felix.framework.FilterImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RepositoryUserAdminSerializationTest {

    private RepositoryUserAdminImpl m_impl;

    private MockCachedRepository m_cachedRepository;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        m_impl = new RepositoryUserAdminImpl();
        TestUtils.configureObject(m_impl, BundleContext.class, TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public Filter createFilter(String s) throws InvalidSyntaxException {
                return new FilterImpl(s);
            }
        }));

        // We configure the CachedRepository ourselves, so there is no need to login
        m_cachedRepository = new MockCachedRepository();
        TestUtils.configureObject(m_impl, CachedRepository.class, m_cachedRepository);
        TestUtils.configureObject(m_impl, PreferencesService.class);
        TestUtils.configureObject(m_impl, LogService.class);
        TestUtils.configureObject(m_impl, Preferences.class); // A Preferences is cached for storing the version.
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void testRepositoryUserAdminSerialization() throws Exception {
        // Create some data
        User user = (User) m_impl.createRole("me", Role.USER);
        user.getProperties().put("fullname", "Mr. M. Me");
        user.getCredentials().put("password", "swordfish");
        user.getCredentials().put("certificate", new byte[] {'4', '2'});
        Group group = (Group) m_impl.createRole("myGroup", Role.GROUP);
        group.getProperties().put("description", "One group to rule them all.");
        group.addMember(user);

        // Write it to the store
        new Thread("RepositoryUserAdmin committer") {
            @Override
            public void run() {
                try {
                    m_impl.commit();
                }
                catch (Exception e) {
                    System.err.println("Error writing data");
                    e.printStackTrace(System.err);
                }
            }
        }.start();

        // wait for impl to be ready, and retrieve what he has written
        Object[] request  = m_cachedRepository.getRequest(true);
        assert request[0].equals("writeLocal");
        InputStream input = (InputStream) request[1];
        request = m_cachedRepository.getRequest(true);
        assert request[0].equals("commit");

        String data1 = getInputStreamAsString(input);

        // alter the contents
        m_impl.createRole("otherme", Role.USER);
        m_impl.removeRole("myGroup");

        m_cachedRepository.addResponse(new ByteArrayInputStream(data1.getBytes()));

        final Semaphore sem = new Semaphore(0);

        // make impl read what it has just written
        new Thread("RepositoryUserAdmin committer") {
            @Override
            public void run() {
                try {
                    m_impl.checkout();
                }
                catch (Exception e) {
                    System.err.println("Error reading data");
                    e.printStackTrace(System.err);
                }
                sem.release();
            }
        }.start();

        // wait for the reading to be done
        sem.tryAcquire(5, TimeUnit.SECONDS);

        request = m_cachedRepository.getRequest(true);
        assert request[0].equals("checkout");

        // inspect the current contents of impl
        Role[] roles = m_impl.getRoles(null);
        assert roles.length == 2 : "Found " + roles.length + " roles in stead of 2.";
        for (Role role : roles) {
            if (role.equals(user)) {
                assert user.hasCredential("password", "swordfish");
                assert user.hasCredential("certificate", new byte[] {'4', '2'});
            }
            else if (role.equals(group)) {
                assert ((Group) role).getMembers().length == 1 : "We expect one member in the group in stream of " + ((Group) role).getMembers().length;
                assert ((Group) role).getMembers()[0].equals(user);
            }
            else {
                assert false : "Found an unknown role: " + role.toString() + " (" + role.getName() + ")";
            }
        }
    }

    @Test(groups = { UNIT })
    public void testCircularDependency() throws Exception {
        Group g1 = (Group) m_impl.createRole("group1", Role.GROUP);
        Group g2 = (Group) m_impl.createRole("group2", Role.GROUP);
        g1.addMember(g2);
        g2.addMember(g1);

        try {
            m_impl.commit();
            assert false : "There is a circular dependency, this should be detected and reason for failure.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }
    }

    /**
     * A mock cached repository, used for checking calls and staging responses to impl.
     */
    private static class MockCachedRepository extends MockResponder implements CachedRepository {

        public InputStream checkout(boolean fail) throws IOException {
            handleRequest("checkout", fail);
            return (InputStream) handleResponse();
        }

        public boolean commit() throws IOException {
            handleRequest("commit");
            return false;
        }

        public InputStream getLocal(boolean fail) throws IOException {
            return null;
        }

        public long getMostRecentVersion() {
            return 0;
        }

        public boolean isCurrent() throws IOException {
            return false;
        }

        public boolean revert() throws IOException {
            return false;
        }

        public void writeLocal(InputStream data) throws IOException {
            handleRequest("writeLocal", data);
        }

        public InputStream checkout(long version) throws IOException, IllegalArgumentException {
            return null;
        }

        public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
            return false;
        }

        public SortedRangeSet getRange() throws IOException {
            return null;
        }

    }

    /**
     * Base class responder, used for inspecting calls and staging responses.
     */
    private static class MockResponder {
        protected BlockingQueue<Object> m_responses = new LinkedBlockingQueue<Object>();
        protected BlockingQueue<Object[]> m_requests = new LinkedBlockingQueue<Object[]>();

        public void addResponse(Object response) {
            m_responses.add(response);
        }

        public Object[] getRequest(boolean wait) {
            if (wait) {
                Object[] result = null;
                try {
                    result = m_requests.poll(5, TimeUnit.SECONDS);
                }
                catch (Exception e) {
                    System.err.println("Interrupted while waiting for blocked queue.");
                    Thread.currentThread().interrupt();
                }
                if (result == null) {
                    assert false : "Even after 5 seconds, no request was ready for us.";
                }
                return result;
            }
            else {
                return m_requests.poll();
            }
        }

        public boolean moreRequests() {
            return !m_requests.isEmpty();
        }

        protected void handleRequest(Object... objs) {
            m_requests.add(objs);
        }

        protected Object handleResponse() {
            return m_responses.poll();
        }
    }

    /**
     * Helper method that gets the contents of a stream into a single string.
     */
    private static String getInputStreamAsString(InputStream in) throws IOException {
        char[] buf = new char[1];
        StringBuilder found = new StringBuilder();
        InputStreamReader bf = new InputStreamReader(in);
        while (bf.read(buf) > 0) {
            found.append(buf);
        }
        bf.close();
        return found.toString();
    }

}
