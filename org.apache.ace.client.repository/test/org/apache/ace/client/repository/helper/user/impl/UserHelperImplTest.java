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
package org.apache.ace.client.repository.helper.user.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.ace.client.repository.helper.ArtifactResource;
import org.osgi.service.log.LogService;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test cases for {@link UserHelperImpl}.
 */
public class UserHelperImplTest {
    private LogService m_mockLogService;

    @Test()
    public void testCanHandleCommentBeforeRoot() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("validUserAdminWithComment.xml"));
        Assert.assertNotNull(mime);

        verifyZeroInteractions(m_mockLogService);
    }

    @Test()
    public void testInvalidUserAdminXmlContent() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("invalidUserAdmin.xml"));
        Assert.assertNull(mime);

        verifyZeroInteractions(m_mockLogService);
    }

    @Test()
    public void testInvalidXmlContentNotRecognized() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("invalid.xml"));
        Assert.assertNull(mime);

        verify(m_mockLogService, times(1)).log(anyInt(), anyString(), any(Throwable.class));
        verifyNoMoreInteractions(m_mockLogService);
    }

    @Test()
    public void testNoUserAdminXmlContent() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("valid10.xml"));
        Assert.assertNull(mime);

        verifyZeroInteractions(m_mockLogService);
    }

    @Test()
    public void testNoXmlContentNotRecognized() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("invalid.txt"));
        Assert.assertNull(mime);

        verify(m_mockLogService, times(1)).log(anyInt(), anyString(), any(Throwable.class));
        verifyNoMoreInteractions(m_mockLogService);
    }

    @Test()
    public void testValidUserAdminXmlContent() throws Exception {
        UserHelperImpl uh = createUserHelper();
        String mime = uh.recognize(convertToArtifactResource("validUserAdmin.xml"));
        Assert.assertNotNull(mime);

        verifyZeroInteractions(m_mockLogService);
    }

    private ArtifactResource convertToArtifactResource(final String res) {
        if (res == null) {
            return null;
        }

        final URL url = getClass().getClassLoader().getResource("./" + res);
        return new ArtifactResource() {
            @Override
            public long getSize() throws IOException {
                return -1L;
            }

            public URL getURL() {
                return url;
            }

            public InputStream openStream() throws IOException {
                return getURL().openStream();
            }
        };
    }

    /**
     * @return a new {@link UserHelperImpl} instance, never <code>null</code>.
     */
    private UserHelperImpl createUserHelper() {
        m_mockLogService = mock(LogService.class);

        UserHelperImpl uh = new UserHelperImpl();
        uh.setLog(m_mockLogService);
        return uh;
    }
}
