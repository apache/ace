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

package org.apache.ace.it.repositoryadmin;

import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_ADDED;
import static org.apache.ace.client.repository.stateful.StatefulTargetObject.TOPIC_STATUS_CHANGED;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.helper.ArtifactHelper;
import org.apache.ace.client.repository.helper.ArtifactPreprocessor;
import org.apache.ace.client.repository.helper.PropertyResolver;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject.StoreState;
import org.apache.felix.dm.Component;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.User;

/**
 * Test cases for the template processing functionality.
 */
public class TemplateProcessorTest extends BaseRepositoryAdminTest {

	static class MockArtifactHelper implements ArtifactHelper {
	    private final String m_mimetype;
	    private final ArtifactPreprocessor m_preprocessor;
	
	    MockArtifactHelper(String mimetype) {
	        this(mimetype, null);
	    }
	
	    MockArtifactHelper(String mimetype, ArtifactPreprocessor preprocessor) {
	        m_mimetype = mimetype;
	        m_preprocessor = preprocessor;
	    }
	
	    public boolean canUse(ArtifactObject object) {
	        return object.getMimetype().equals(m_mimetype);
	    }
	
	    public Map<String, String> checkAttributes(Map<String, String> attributes) {
	        return attributes;
	    }
	
	    public <TYPE extends ArtifactObject> String getAssociationFilter(TYPE obj, Map<String, String> properties) {
	        return ("(" + ArtifactObject.KEY_URL + "=" + obj.getURL() + ")");
	    }
	
	    public <TYPE extends ArtifactObject> int getCardinality(TYPE obj, Map<String, String> properties) {
	        return 1;
	    }
	
	    public Comparator<ArtifactObject> getComparator() {
	        return null;
	    }
	
	    public String[] getDefiningKeys() {
	        return new String[] { ArtifactObject.KEY_URL };
	    }
	
	    public String[] getMandatoryAttributes() {
	        return new String[] { ArtifactObject.KEY_URL };
	    }
	
	    public ArtifactPreprocessor getPreprocessor() {
	        return m_preprocessor;
	    }
	};
	
	static class MockArtifactPreprocessor implements ArtifactPreprocessor {
	    private PropertyResolver m_props;
	
	    public boolean needsNewVersion(String url, PropertyResolver props, String targetID, String fromVersion) {
	        return false;
	    }
	
	    public String preprocess(String url, PropertyResolver props, String targetID, String version, URL obrBase)
	        throws IOException {
	        m_props = props;
	        return url;
	    }
	
	    PropertyResolver getProps() {
	        return m_props;
	    }
	}

    public void testStatefulApprovalWithArtifacts() throws Exception {
        setupRepository();
        
        // some setup: we need a helper.
        ArtifactHelper myHelper = new MockArtifactHelper("mymime");

        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Component myHelperService = m_dependencyManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(myHelper);

        m_dependencyManager.add(myHelperService);

        // Empty tag map to be reused througout test
        final Map<String, String> tags = new HashMap<>();

        // First, create a bundle and two artifacts, but do not provide a processor for the artifacts.
        ArtifactObject b1 = createBasicBundleObject("bundle1");
        Map<String, String> attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_URL, "http://myobject");
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, "my.processor.pid");
        attr.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        ArtifactObject a1 = m_artifactRepository.create(attr, tags);

        attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_URL, "http://myotherobject");
        attr.put(ArtifactObject.KEY_PROCESSOR_PID, "my.processor.pid");
        attr.put(ArtifactObject.KEY_RESOURCE_ID, "mymime");
        attr.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        ArtifactObject a2 = m_artifactRepository.create(attr, tags);

        FeatureObject g = createBasicFeatureObject("feature");
        DistributionObject l = createBasicDistributionObject("distribution");

        attr = new HashMap<>();
        attr.put(TargetObject.KEY_ID, "myTarget");

        StatefulTargetObject sgo = m_statefulTargetRepository.preregister(attr, tags);

        m_artifact2featureRepository.create(b1, g);
        m_artifact2featureRepository.create(a1, g);
        m_artifact2featureRepository.create(a2, g);
        m_feature2distributionRepository.create(g, l);
        m_distribution2targetRepository.create(l, sgo.getTargetObject());

        sgo.approve();
        
        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED);
        
        assertEquals("Store state for target should still be new, because the resource processor is missing.", StoreState.New, sgo.getStoreState());
        
        // Now, add a processor for the artifact.
        attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_URL, "http://myprocessor");
        attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "my.processor.pid");
        attr.put(BundleHelper.KEY_SYMBOLICNAME, "my.processor.bundle");
        attr.put(BundleHelper.KEY_VERSION, "1.0.0");
        attr.put(ArtifactHelper.KEY_MIMETYPE, BundleHelper.MIMETYPE);

        ArtifactObject b2 = m_artifactRepository.create(attr, tags);

        sgo.approve();
        
        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);        

        DeploymentVersionObject dep = m_deploymentVersionRepository.getMostRecentDeploymentVersion(sgo.getID());

        DeploymentArtifact[] toDeploy = dep.getDeploymentArtifacts();

        assertEquals("We expect to find four artifacts to deploy;", 4, toDeploy.length);
        DeploymentArtifact bundle1 = toDeploy[0];
        assertEquals(b1.getURL(), bundle1.getUrl());
        
        DeploymentArtifact bundle2 = toDeploy[1];
        assertEquals(b2.getURL(), bundle2.getUrl());
        assertEquals("true", bundle2.getDirective(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER));
        
        DeploymentArtifact artifact1 = toDeploy[2];
        assertEquals(a1.getURL(), artifact1.getUrl());
        assertEquals("my.processor.pid", artifact1.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
        
        DeploymentArtifact artifact2 = toDeploy[3];
        assertEquals(a2.getURL(), artifact2.getUrl());
        assertEquals("my.processor.pid", artifact2.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
        assertEquals(a2.getResourceId(), artifact2.getDirective(DeploymentArtifact.DIRECTIVE_KEY_RESOURCE_ID));
        
        // Now, add a new version of the processor (ACE-373)
        assertFalse("There should be no changes.", sgo.needsApprove());

        attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_URL, "http://myprocessor/v2");
        attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "my.processor.pid");
        attr.put(BundleHelper.KEY_SYMBOLICNAME, "my.processor.bundle");
        attr.put(BundleHelper.KEY_VERSION, "2.0.0");
        attr.put(ArtifactHelper.KEY_MIMETYPE, BundleHelper.MIMETYPE);

        ArtifactObject b3 = m_artifactRepository.create(attr, tags);

        assertTrue("By adding a resource processor, we should have triggered a change that needs to be approved.", sgo.needsApprove());

        sgo.approve();

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);

        dep = m_deploymentVersionRepository.getMostRecentDeploymentVersion(sgo.getID());

        toDeploy = dep.getDeploymentArtifacts();

        assertEquals("We expect to find four artifacts to deploy;", 4, toDeploy.length);
        boolean foundBundle = false;
        boolean foundProcessor = false;
        boolean foundArtifact1 = false;
        boolean foundArtifact2 = false;
        for (DeploymentArtifact a : toDeploy) {
            String url = a.getUrl();
            if (url.equals(b1.getURL())) {
                foundBundle = true;
            }
            else if (url.equals(b3.getURL())) {
                assertEquals("true", a.getDirective(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER));
                foundProcessor = true;
            }
            else if (url.equals(a1.getURL())) {
                assertEquals("my.processor.pid", a.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
                foundArtifact1 = true;
            }
            else if (url.equals(a2.getURL())) {
                assertEquals("my.processor.pid", a.getDirective(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID));
                assertEquals(a2.getResourceId(), a.getDirective(DeploymentArtifact.DIRECTIVE_KEY_RESOURCE_ID));
                foundArtifact2 = true;
            }
        }
        assertTrue("Could not find bundle in deployment", foundBundle);
        assertTrue("Could not find processor in deployment", foundProcessor);
        assertTrue("Could not find artifact 1 in deployment", foundArtifact1);
        assertTrue("Could not find artifact 2 in deployment", foundArtifact2);

        // Now, let's add a new resource processor that is *older* than the one we already have.
        // Nothing should change.

        assertFalse("There should be no changes.", sgo.needsApprove());

        attr = new HashMap<>();
        attr.put(ArtifactObject.KEY_URL, "http://myprocessor/v1.5");
        attr.put(BundleHelper.KEY_RESOURCE_PROCESSOR_PID, "my.processor.pid");
        attr.put(BundleHelper.KEY_SYMBOLICNAME, "my.processor.bundle");
        attr.put(BundleHelper.KEY_VERSION, "1.5.0");
        attr.put(ArtifactHelper.KEY_MIMETYPE, BundleHelper.MIMETYPE);

        m_artifactRepository.create(attr, tags);

        assertFalse("By adding an older resource processor, we should not have triggered a change.", sgo.needsApprove());

        cleanUp();

        m_dependencyManager.remove(myHelperService);
    }

    private void setupRepository() throws IOException, InterruptedException, InvalidSyntaxException {
        User user = new MockUser();

        addRepository("storeInstance", "apache", "store", true);
        addRepository("targetInstance", "apache", "target", true);
        addRepository("deploymentInstance", "apache", "deployment", true);

        RepositoryAdminLoginContext loginContext = m_repositoryAdmin.createLoginContext(user);
        loginContext
            .add(loginContext.createShopRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("store").setWriteable())
            .add(loginContext.createTargetRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("target").setWriteable())
            .add(loginContext.createDeploymentRepositoryContext()
                .setLocation(m_endpoint).setCustomer("apache").setName("deployment").setWriteable());

        m_repositoryAdmin.login(loginContext);
        m_repositoryAdmin.checkout();
    }

    /**
     * Tests the full template mechanism, from importing templatable artifacts, to creating deployment
     * versions with it. It uses the configuration (autoconf) helper, which uses a VelocityBased preprocessor.
     */
    public void testTemplateProcessing() throws Exception {
        setupRepository();
        
        addObr("/obr", "store");

        // create some template things
        String xmlHeader =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><metatype:MetaData xmlns:metatype=\"http://www.osgi.org/xmlns/metatype/v1.0.0\">";
        String xmlFooter = "</metatype:MetaData>";

        String noTemplate = "<Attribute content=\"http://someURL\"/>";
        String noTemplateProcessed = "<Attribute content=\"http://someURL\"/>";
        final File noTemplateFile = createFileWithContents("template", ".xml", xmlHeader + noTemplate + xmlFooter);

        String simpleTemplate = "<Attribute content=\"http://$context.name\"/>";
        String simpleTemplateProcessed = "<Attribute content=\"http://mydistribution\"/>";
        File simpleTemplateFile = createFileWithContents("template", ".xml", xmlHeader + simpleTemplate + xmlFooter);

        // create some tree from artifacts to a target
        FeatureObject go = runAndWaitForEvent(new Callable<FeatureObject>() {
            public FeatureObject call() throws Exception {
                ArtifactObject b1 = createBasicBundleObject("myBundle");
                createBasicBundleObject("myProcessor", "1.0.0", "org.osgi.deployment.rp.autoconf");
                FeatureObject go = createBasicFeatureObject("myfeature");
                DistributionObject lo = createBasicDistributionObject("mydistribution");
                TargetObject gwo = createBasicTargetObject("templatetarget2");
                m_artifact2featureRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_feature2distributionRepository.create(go, lo);
                m_distribution2targetRepository.create(lo, gwo);
                return go;
            }
        }, false, TOPIC_ADDED);

        ArtifactObject a1 = m_artifactRepository.importArtifact(noTemplateFile.toURI().toURL(), true);
        Artifact2FeatureAssociation a2g = m_artifact2featureRepository.create(a1, go);

        final StatefulTargetObject sgo = findStatefulTarget("templatetarget2");

        // create a deploymentversion
        assertTrue("With the new assignments, the SGO should need approval.", sgo.needsApprove());
        
        sgo.approve();

        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);  
        
        // find the deployment version
        DeploymentVersionObject dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templatetarget2");
        String inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assertEquals(xmlHeader + noTemplateProcessed + xmlFooter, inFile);

        // try the simple template
        m_artifact2featureRepository.remove(a2g);
        a1 = m_artifactRepository.importArtifact(simpleTemplateFile.toURI().toURL(), true);
        a2g = m_artifact2featureRepository.create(a1, go);

        sgo.approve();
        
        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);  


        // find the deployment version
        dvo = m_deploymentVersionRepository.getMostRecentDeploymentVersion("templatetarget2");
        // sleep for a while, to allow the OBR to process the file.
        Thread.sleep(250);

        inFile = tryGetStringFromURL(findXmlUrlInDeploymentObject(dvo), 10, 100);

        assertEquals(xmlHeader + simpleTemplateProcessed + xmlFooter, inFile);
    }

    /**
     * Tests the template processing mechanism: given a custom processor, do the correct calls go out?
     */
    public void testTemplateProcessingInfrastructure() throws Exception {
        setupRepository();
        
        // create a preprocessor
        MockArtifactPreprocessor preprocessor = new MockArtifactPreprocessor();

        // create a helper
        MockArtifactHelper helper = new MockArtifactHelper("mymime", preprocessor);

        // register preprocessor and helper
        Properties serviceProps = new Properties();
        serviceProps.put(ArtifactHelper.KEY_MIMETYPE, "mymime");

        Component helperService = m_dependencyManager.createComponent()
            .setInterface(ArtifactHelper.class.getName(), serviceProps)
            .setImplementation(helper);

        m_dependencyManager.add(helperService);
        
        String targetId = "templatetarget";

        createBasicBundleObject("myProcessor", "1.0.0", "myProcessor.pid");
        final ArtifactObject b1 = createBasicBundleObject("myBundle");
        final ArtifactObject a1 = createBasicArtifactObject("myArtifact", "mymime", "myProcessor.pid");
        final FeatureObject go = createBasicFeatureObject("myfeature");
        final DistributionObject lo = createBasicDistributionObject("mydistribution");
		final TargetObject gwo = createBasicTargetObject(targetId);

        // create some tree from artifacts to a target
        runAndWaitForEvent(new Callable<Object>() {
            public Object call() throws Exception {
                m_artifact2featureRepository.create(b1, go);
                // note that we do not associate b2: this is a resource processor, so it will be packed
                // implicitly. It should not be available to a preprocessor either.
                m_artifact2featureRepository.create(a1, go);
                m_feature2distributionRepository.create(go, lo);
                m_distribution2targetRepository.create(lo, gwo);
                return null;
            }
        }, false, TOPIC_STATUS_CHANGED);

        StatefulTargetObject sgo = findStatefulTarget(targetId);
        assertNotNull("Failed to find our target in the repository?!", sgo);

        // wait until needsApprove is true; depending on timing, this could have happened before or after the TOPIC_ADDED.
        int attempts = 0;
        while (!sgo.needsApprove() && (attempts++ < 10)) {
            Thread.sleep(100);
        }
        
        assertTrue("With the new assignments, the SGO should need approval.", sgo.needsApprove());
        // create a deploymentversion
        sgo.approve();
        
        
        runAndWaitForEvent(new Callable<Void>() {
            public Void call() throws Exception {
                m_repositoryAdmin.commit();
                return null;
            }
        }, false, DeploymentVersionObject.TOPIC_ADDED, TOPIC_STATUS_CHANGED);  

        // the preprocessor now has gotten its properties; inspect these
        PropertyResolver target = preprocessor.getProps();
        assertTrue("The property resolver should be able to resolve 'id'.", target.get("id").startsWith(targetId));
        assertTrue("The property resolver should be able to resolve 'name'.", target.get("name").startsWith("mydistribution"));
        assertNull("The property resolver should not be able to resolve 'someunknownproperty'.", target.get("someunknownproperty"));

        cleanUp(); // we need to do this before the helper goes away

        m_dependencyManager.remove(helperService);
    }

    private String tryGetStringFromURL(URL url, int tries, int interval) throws Exception {
        while (true) {
            try {
                List<String> result = getResponse(url);
                
                StringBuilder sb = new StringBuilder();
                for (String line : result) {
                	if (sb.length() > 0) {
                		sb.append('\n');
                	}
                	sb.append(line);
                }
                
                return sb.toString();
            }
            catch (IOException ioe) {
                if (--tries == 0) {
                    throw ioe;
                } else {
                	Thread.sleep(interval);
                }
            }
        }
    }

    /**
     * The following code is borrowed from RepositoryTest.java, and is used to instantiate and
     * use repository servlets.
     */
    private StatefulTargetObject findStatefulTarget(String targetID) throws Exception {
    	int count = 10;
    	while (count-- > 0) {
	        for (StatefulTargetObject sgo : m_statefulTargetRepository.get()) {
	            if (sgo.getID().equals(targetID)) {
	                return sgo;
	            }
	        }
	        Thread.sleep(100);
    	}
        return null;
    }

    /**
     * Creates a temporary file with the given name and extension, and stores the given
     * contents in it.
     */
    private File createFileWithContents(String name, String extension, String contents) throws IOException {
        File file = File.createTempFile(name, extension);
        file.deleteOnExit();
        Writer w = new OutputStreamWriter(new FileOutputStream(file));
        w.write(contents);
        w.close();
        return file;
    }

    /**
     * Helper method for testTemplateProcessing; finds the URL of the first deploymentartifact
     * with 'xml' in its url.
     */
    private URL findXmlUrlInDeploymentObject(DeploymentVersionObject dvo) throws MalformedURLException {
        DeploymentArtifact[] artifacts = dvo.getDeploymentArtifacts();
        for (DeploymentArtifact da : artifacts) {
            if (da.getUrl().contains("xml")) {
                return new URL(da.getUrl());
            }
        }
        return null;
    }
}
