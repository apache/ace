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
package org.apache.ace.deployment.provider.repositorybased;

import java.lang.reflect.Field;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.deployment.provider.OverloadedException;
import org.apache.ace.repository.Repository;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RepositoryBasedProviderConcurrencyTest {

    private final String TARGET = "target";

    private RepositoryBasedProvider m_backend;
    private Semaphore m_semaphore;
    private Exception m_exception;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        // setup mock repository
        m_semaphore = new Semaphore(0);
        MockDeploymentRepository repository = new MockDeploymentRepository("", null, m_semaphore);
        m_backend = new RepositoryBasedProvider();
        TestUtils.configureObject(m_backend, Repository.class, repository);
        TestUtils.configureObject(m_backend, LogService.class);
    }

    @Test()
    public void testNoConcurrentUsersAllowed() throws Exception {
        // -1 number of users makes sure nobody can use the repository
        setConfigurationForUsers(-1);
        try {
            m_backend.getVersions(TARGET);
            assert false : "Expected an overloaded exception";
        }
        catch (OverloadedException oe) {
            assert true;
        }
        catch (Throwable t) {
            assert false : "Unknown exception";
        }
    }

    @Test()
    public void testConcurrentUsersWithLimit() throws Exception {
        setConfigurationForUsers(1);
        new Thread() {
            public void run() {
                try {
                    m_backend.getVersions(TARGET);
                }
                catch (Exception e) {
                    m_exception = e;
                }
            }
        }.start();

        try {
            boolean acquire = m_semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
            assert acquire : "Could not acquire semaphore, no concurrent threads ?";
            m_backend.getVersions(TARGET);
            assert false : "Expected an overloaded exception";
        }
        catch (OverloadedException oe) {
            assert true;
        }

        assert m_exception == null : "No Exception expected";
    }

    @Test()
    public void testConcurrentUsersWithoutLimit() throws Exception {
        // Because it's hard to test for unlimited users we'll just try 2 concurrent threads.
        setConfigurationForUsers(0);
        new Thread() {
            public void run() {
                try {
                    m_backend.getVersions(TARGET);
                }
                catch (Exception e) {
                    m_exception = e;
                }
            }
        }.start();

        m_semaphore.tryAcquire(1, 1, TimeUnit.SECONDS);
        m_backend.getVersions(TARGET);
        assert true;

        assert m_exception == null : "No Exception expected";
    }

    private void setConfigurationForUsers(int numberOfConcurrentUsers) throws Exception {
        // setting a new configuration on the repository also creates a cache repository etc. This way only the max
        // users is changed.
        Field field = m_backend.getClass().getDeclaredField("m_maximumNumberOfUsers");
        field.setAccessible(true);
        field.set(m_backend, new Integer(numberOfConcurrentUsers));
    }
}
