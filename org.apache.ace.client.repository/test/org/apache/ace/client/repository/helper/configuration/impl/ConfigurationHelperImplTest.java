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
package org.apache.ace.client.repository.helper.configuration.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.ace.client.repository.helper.ArtifactResource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ConfigurationHelperImplTest {

    // ACE-259 Basic recognizer tests

    @Test(groups = { UNIT })
    public void testNamespace10Recognized() throws Exception {
        ConfigurationHelperImpl c = new ConfigurationHelperImpl();
        String mime = c.recognize(convertToArtifactResource("valid10.xml"));
        Assert.assertNotNull(mime);
    }

    @Test(groups = { UNIT })
    public void testNamespace11Recognized() throws Exception {
        ConfigurationHelperImpl c = new ConfigurationHelperImpl();
        String mime = c.recognize(convertToArtifactResource("valid11.xml"));
        Assert.assertNotNull(mime);
    }

    @Test(groups = { UNIT })
    public void testNamespace12Recognized() throws Exception {
        ConfigurationHelperImpl c = new ConfigurationHelperImpl();
        String mime = c.recognize(convertToArtifactResource("valid12.xml"));
        Assert.assertNotNull(mime);
    }

    @Test(groups = { UNIT })
    public void testNamespace13NotRecognized() throws Exception {
        ConfigurationHelperImpl c = new ConfigurationHelperImpl();
        String mime = c.recognize(convertToArtifactResource("invalid13.xml"));
        Assert.assertNull(mime);
    }

    @Test(groups = { UNIT })
    public void testCanHandleCommentBeforeRoot() throws Exception {
        ConfigurationHelperImpl c = new ConfigurationHelperImpl();
        String mime = c.recognize(convertToArtifactResource("validWithComment.xml"));
        Assert.assertNotNull(mime);
    }

    /**
     * @param url
     * @return
     */
    private ArtifactResource convertToArtifactResource(final String res) {
        if (res == null) {
            return null;
        }
        
        final URL url = getClass().getClassLoader().getResource("./" + res);
        
        return new ArtifactResource() {
            public URL getURL() {
                return url;
            }
            
            @Override
            public long getSize() throws IOException {
                return -1L;
            }
            
            public InputStream openStream() throws IOException {
                return getURL().openStream();
            }
        };
    }
}