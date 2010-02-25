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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.helper.bundle.impl.BundleHelperImpl;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.test.utils.TestUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the behavior of the ArtifactObject class, most prominently, checking whether
 * delegation to the Helpers is done at the right moments.
 */
public class ArtifactTest {

    private ArtifactRepositoryImpl m_artifactRepository;

    @BeforeMethod(alwaysRun = true)
    public void init() {
        BundleContext bc = TestUtils.createMockObjectAdapter(BundleContext.class, new Object() {
            @SuppressWarnings("unused")
            public Filter createFilter(String filter) throws InvalidSyntaxException {
                return new org.apache.felix.framework.FilterImpl(filter);
            }
        });

        m_artifactRepository = new ArtifactRepositoryImpl(TestUtils.createNullObject(ChangeNotifier.class));
        TestUtils.configureObject(m_artifactRepository, LogService.class);
        TestUtils.configureObject(m_artifactRepository, BundleContext.class, bc);
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testAttributeChecking() {
        ArtifactHelper helper = new MockHelper("yourURL");

        try {
            createArtifact("myMime", "myUrl", null, null);
            assert false : "There is no helper for this type of artifact.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }

        m_artifactRepository.addHelper("myMime", helper);

        ArtifactObject obj = createArtifact("myMime", "myUrl", null, null);

        assert obj.getURL().equals("yourURL");

        try {
            m_artifactRepository.getHelper("yourMime");
            assert false : "We have not registered this helper.";
        }
        catch (IllegalArgumentException iae) {
            // expected
        }
    }

    @Test( groups = { TestUtils.UNIT } )
    public void testResourceProcessorFiltering() throws InvalidSyntaxException {
        m_artifactRepository.addHelper("myMime", new MockHelper());
        m_artifactRepository.addHelper(BundleHelper.MIMETYPE, new BundleHelperImpl());

        ArtifactObject normalBundle = createArtifact(BundleHelper.MIMETYPE, "normalBundle", "normalBundle", null);

        ArtifactObject resourceProcessor1 = createArtifact(BundleHelper.MIMETYPE, "resourceProcessor1", "resourceProcessor1", "somePID");
        ArtifactObject resourceProcessor2 = createArtifact(BundleHelper.MIMETYPE, "resourceProcessor2", "resourceProcessor2", "someOtherPID");

        ArtifactObject myArtifact = createArtifact("myMime", "myArtifact", null, null);

        assert m_artifactRepository.get().size() == 2 : "We expect to find two artifacts, but we find " + m_artifactRepository.get().size();

        List<ArtifactObject> list = m_artifactRepository.get(m_artifactRepository.createFilter("(!(" + BundleHelper.KEY_SYMBOLICNAME + "=normalBundle))"));
        assert (list.size() == 1) && list.contains(myArtifact) : "We expect to find one artifact when filtering, but we find " + list.size();

        list = m_artifactRepository.getResourceProcessors();
        assert (list.size() == 2) && list.contains(resourceProcessor1) && list.contains(resourceProcessor2) : "We expect to find both our resource processors when asking for them; we find " + list.size() + " artifacts.";

       m_artifactRepository.get(m_artifactRepository.createFilter("(" + BundleHelper.MIMETYPE + "=my\\(Mi\\*me)"));
    }

    private ArtifactObject createArtifact(String mimetype, String URL, String symbolicName, String processorPID) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(ArtifactObject.KEY_MIMETYPE, mimetype);
        attributes.put(ArtifactObject.KEY_URL, URL);
        Map<String, String> tags = new HashMap<String, String>();

        if (symbolicName != null) {
            attributes.put(BundleHelper.KEY_SYMBOLICNAME, symbolicName);
        }
        if (processorPID != null) {
            attributes.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, processorPID);
        }

        return m_artifactRepository.create(attributes, tags);
    }
}

/**
 * Helper for testing the ArtifactObject. In the constructor, a <code>replaceURL</code> can
 * be passed in to test the attribute normalization.
 */
class MockHelper implements ArtifactHelper {
    private final String m_replaceURL;

    MockHelper() {
        this(null);
    }

    MockHelper(String replaceURL) {
        m_replaceURL = replaceURL;
    }

    public Map<String, String> checkAttributes(Map<String, String> attributes) {
        if ((m_replaceURL != null) && attributes.containsKey(ArtifactObject.KEY_URL)) {
            attributes.put(ArtifactObject.KEY_URL, m_replaceURL);
        }
        return attributes;
    }

    public Comparator<ArtifactObject> getComparator() {
        return null;
    }

    public String[] getDefiningKeys() {
        return new String[0];
    }

    public String[] getMandatoryAttributes() {
        return new String[0];
    }

    public boolean canUse(ArtifactObject object) {
        return false;
    }

    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
        return null;
    }

    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
        return 0;
    }

    public ArtifactPreprocessor getPreprocessor() {
        return null;
    }
}





