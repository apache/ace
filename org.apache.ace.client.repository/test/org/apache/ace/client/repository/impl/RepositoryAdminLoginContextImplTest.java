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

package org.apache.ace.client.repository.impl;

import java.net.URL;

import org.apache.ace.client.repository.impl.RepositoryAdminLoginContextImpl.RepositorySetDescriptor;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.test.constants.TestConstants;
import org.mockito.Mockito;
import org.osgi.service.useradmin.User;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test cases for {@link RepositoryAdminLoginContextImpl}.
 */
@SuppressWarnings("unchecked")
public class RepositoryAdminLoginContextImplTest {

    private static final String CUSTOMER = "apache";

    private static final String NAME_SHOP = "shop";
    private static final String NAME_DEPLOYMENT = "deployment";

    private URL m_location;

    @BeforeMethod
    public void setUp() throws Exception {
        m_location = new URL("http://localhost:" + TestConstants.PORT);
    }

    /**
     * Test method for {@link RepositoryAdminLoginContextImpl#addDescriptor(RepositorySetDescriptor)}.
     */
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testAddDuplicateObjectRepositoryFail() throws Exception {
        RepositoryAdminLoginContextImpl context = new RepositoryAdminLoginContextImpl(Mockito.mock(User.class), "12345");

        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_SHOP, true, FeatureRepository.class));
        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_DEPLOYMENT, true, FeatureRepository.class));
    }

    /**
     * Test method for {@link RepositoryAdminLoginContextImpl#addDescriptor(RepositorySetDescriptor)}.
     */
    @Test()
    public void testAddDisjointObjectRepositoriesOk() throws Exception {
        RepositoryAdminLoginContextImpl context = new RepositoryAdminLoginContextImpl(Mockito.mock(User.class), "12345");

        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_SHOP, true, ArtifactRepository.class));
        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_DEPLOYMENT, true, FeatureRepository.class));
    }

    /**
     * Test method for {@link RepositoryAdminLoginContextImpl#addDescriptor(RepositorySetDescriptor)}.
     */
    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testDuplicateRepositoryNameFail() throws Exception {
        RepositoryAdminLoginContextImpl context = new RepositoryAdminLoginContextImpl(Mockito.mock(User.class), "12345");

        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_SHOP, true, ArtifactRepository.class));
        context.addDescriptor(new RepositorySetDescriptor(m_location, CUSTOMER, NAME_SHOP, true, FeatureRepository.class));
    }
}
