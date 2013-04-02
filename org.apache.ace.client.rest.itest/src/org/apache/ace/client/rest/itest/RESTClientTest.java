package org.apache.ace.client.rest.itest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Enumeration;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.http.listener.constants.HttpConstants;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;

import com.google.gson.Gson;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;

public class RESTClientTest extends IntegrationTestBase {
	// From: ConfigurationHelper (somehow directly using them here fails)
    public static final String KEY_FILENAME = "filename";
    public static final String MIMETYPE = "application/xml:osgi-autoconf";
    public static final String PROCESSOR = "org.osgi.deployment.rp.autoconf";	
	
    private static boolean m_hasBeenSetup = false;
    private volatile BundleContext m_context;
    private volatile UserAdmin m_user;
    private volatile LogReaderService m_logReader;
    
    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(LogReaderService.class).setRequired(true))
        };
    }
    
    @Override
    protected void after() throws Exception {
        // there is some setup we only want to do once, before the first test we run, and since we cannot
        // predict which one that is, we use a static flag
        if (!m_hasBeenSetup) {
            configureServer();
            createServerUser();
            m_hasBeenSetup = true;
        }
    }
    
    /**
     * Creates a new workspace, ensures it works correctly by asking for a list of entity types, then
     * deletes the workspace again and ensures it's no longer available.
     */
    public void testCreateAndDestroyRESTSession() throws Exception {
        Client c = Client.create();
        c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
        WebResource r = c.resource("http://localhost:8080/client/work");
        try {
            r.post(String.class, "");
            fail("We should have been redirected to a new workspace.");
        }
        catch (UniformInterfaceException e) {
            ClientResponse response = e.getResponse();
            URI location = response.getLocation();
            assertTrue(location.toString().startsWith("http://localhost:8080/client/work/rest-"));
            WebResource r2 = c.resource(location);
            r2.get(String.class);            
            r2.delete();
            try {
                r2.get(String.class);
            }
            catch (UniformInterfaceException e2) {
                assertEquals(404, e2.getResponse().getStatus());
            }
        }
    }

    /**
     * Creates a workspace, three bundle artifacts that are associated to three features. The features are all grouped into
     * a single distribution, and the distribution is associated with a target. This workspace is then committed and a new
     * workspace is opened. From this new workspace, we read back the entities we committed before. Then, we check if this
     * has indeed led to a new version of the software for this target.
     */
    public void testDeployBundlesToTarget() throws Exception {
        Gson gson = new Gson();
        Client c = Client.create();
        c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
        
        File b1 = new File("b1.jar");
        File b2 = new File("b2.jar");
        File b3 = new File("b3.jar");
        createBundleOnDisk(b1, "b1", "1.0.0");
        createBundleOnDisk(b2, "b2", "1.0.0");
        createBundleOnDisk(b3, "b3", "1.0.0");
        b1.deleteOnExit();
        b2.deleteOnExit();
        b3.deleteOnExit();
        
        WebResource w1 = createWorkspace(c);
        WebResource a1 = createBundle(c, w1, "a1", "b1", "1.0.0", b1.toURI().toURL().toString(), BundleHelper.MIMETYPE);
        WebResource a2 = createBundle(c, w1, "a2", "b2", "1.0.0", b2.toURI().toURL().toString(), BundleHelper.MIMETYPE);
        WebResource a3 = createBundle(c, w1, "a3", "b3", "1.0.0", b3.toURI().toURL().toString(), BundleHelper.MIMETYPE);
        assertEntitiesExist(a1, a2, a3);
        WebResource a1f1 = createAssociationA2F(c, w1, "artifact2feature", "a1", "f1");
        WebResource a2f2 = createAssociationA2F(c, w1, "artifact2feature", "a2", "f2");
        WebResource a3f3 = createAssociationA2F(c, w1, "artifact2feature", "a3", "f3");
        assertEntitiesExist(a1f1, a2f2, a3f3);
        WebResource f1 = createFeature(c, w1, "f1");
        WebResource f2 = createFeature(c, w1, "f2");
        WebResource f3 = createFeature(c, w1, "f3");
        assertEntitiesExist(f1, f2, f3);
        WebResource f1d1 = createAssociationF2D(c, w1, "feature2distribution", "f1", "d1");
        WebResource f2d1 = createAssociationF2D(c, w1, "feature2distribution", "f2", "d1");
        WebResource f3d1 = createAssociationF2D(c, w1, "feature2distribution", "f3", "d1");
        assertEntitiesExist(f1d1, f2d1, f3d1);
        WebResource d1 = createDistribution(c, w1, "d1");
        assertEntitiesExist(d1);
        WebResource d1t1 = createAssociationD2T(c, w1, "distribution2target", "d1", "t1");
        assertEntitiesExist(d1t1);
        WebResource t1 = createTarget(c, w1, "t1");
        assertEntitiesExist(t1);
        w1.post();
        w1.delete();
        
        WebResource w2 = createWorkspace(c);
        assertResources(gson, w2, "artifact", 3);
        assertResources(gson, w2, "artifact2feature", 3);
        assertResources(gson, w2, "feature", 3);
        assertResources(gson, w2, "feature2distribution", 3);
        assertResources(gson, w2, "distribution", 1);
        assertResources(gson, w2, "distribution2target", 1);
        assertResources(gson, w2, "target", 1);
        w2.delete();
        
        WebResource t1versions = c.resource("http://localhost:8080/deployment/t1/versions");
        assertEquals("1.0.0\n", t1versions.get(String.class));
    }

    public void testDeployConfigurationTemplateToTargets() throws Exception {
        try {
            Client c = Client.create();
            c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
            
            File config = new File("template.xml");
            BufferedWriter bw = new BufferedWriter(new FileWriter(config));
            bw.write(
                "<MetaData xmlns='http://www.osgi.org/xmlns/metatype/v1.0.0'>\n" + 
                "  <OCD name='ocd' id='ocd'>\n" + 
                "    <AD id='name' type='STRING' cardinality='0' />\n" + 
                "  </OCD>\n" + 
                "  <Designate pid='simple' bundle='osgi-dp:location'>\n" + 
                "    <Object ocdref='ocd'>\n" + 
                "      <Attribute adref='name'>\n" + 
                "        <Value><![CDATA[${context.test}]]></Value>\n" + 
                "      </Attribute>\n" + 
                "    </Object>\n" + 
                "  </Designate>\n" + 
                "</MetaData>\n"
            );
            bw.close();
            config.deleteOnExit();
            File rp = new File("rp.jar");
            createBundleOnDisk(rp, "rp", "1.0.0", BundleHelper.KEY_RESOURCE_PROCESSOR_PID, PROCESSOR, "DeploymentPackage-Customizer", "true");
            rp.deleteOnExit();

            WebResource w1 = createWorkspace(c);
            createResourceProcessor(c, w1, "rp", "resourceprocessor", "1.0.0", rp.toURI().toURL().toString(), BundleHelper.MIMETYPE, PROCESSOR);
            createConfiguration(c, w1, "c1", config.toURI().toURL().toString(), MIMETYPE, "template.xml", PROCESSOR);
            createAssociationA2F(c, w1, "artifact2feature", "c1", "f4");
            createFeature(c, w1, "f4");
            createAssociationF2D(c, w1, "feature2distribution", "f4", "d2");
            createDistribution(c, w1, "d2");
            createAssociationD2T(c, w1, "distribution2target", "d2", "t4");
            createAssociationD2T(c, w1, "distribution2target", "d2", "t5");
            createAssociationD2T(c, w1, "distribution2target", "d2", "t6");
            createTarget(c, w1, "t4", "test", "one");
            createTarget(c, w1, "t5", "test", "two");
            createTarget(c, w1, "t6", "test", "three");
            w1.post();
            w1.delete();
            
            /* TODO: temporarily disabled these checks, because between test methods nothing
             * is cleaned up right now and this part of the test does rely on that
            Gson gson = new Gson();
            WebResource w2 = createWorkspace(c);
            assertResources(gson, w2, "artifact", 1);
            assertResources(gson, w2, "artifact2feature", 1);
            assertResources(gson, w2, "feature", 1);
            assertResources(gson, w2, "feature2distribution", 1);
            assertResources(gson, w2, "distribution", 1);
            assertResources(gson, w2, "distribution2target", 3);
            assertResources(gson, w2, "target", 3);
            w2.delete();
             */

            // just for debugging
            showLog();
            showBundles();
            
            WebResource t1versions = c.resource("http://localhost:8080/deployment/t4/versions");
            assertEquals("1.0.0\n", t1versions.get(String.class));
        }
        catch (Exception e) {
            showLog();
            throw e;
        }
    }

    /** Shows all log messages in the OSGi log service. */
    private void showLog() {
        Enumeration e = m_logReader.getLog();
        System.out.println("Log:");
        while (e.hasMoreElements()) {
            LogEntry entry = (LogEntry) e.nextElement();
            System.out.println(" * " + (new Date(entry.getTime())) + " - " + entry.getMessage() + " - " + entry.getBundle().getBundleId() + " - " + entry.getException());
            if (entry.getException() != null) {
                entry.getException().printStackTrace();
            }
        }
    }

    /** Shows all bundles in the framework. */
    private void showBundles() {
        for (Bundle b : m_context.getBundles()) {
            System.out.println(" * [" + b.getBundleId() + "] " + b.getState() + " - " + b.getSymbolicName() + " " + b.getVersion());
        }
    }

    /**
     * Creates and deletes a number of workspaces.
     */
    public void testCreateAndDeleteMultipleWorkspaces() throws Exception {
        int nr = 10;
        Client c = Client.create();
        c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
        for (int i = 0; i < nr; i++) {
            WebResource w1 = createWorkspace(c);
            w1.delete();
        }
        WebResource[] w = new WebResource[nr];
        for (int i = 0; i < w.length; i++) {
            w[i] = createWorkspace(c);
        }
        for (int i = 0; i < w.length; i++) {
            w[i].delete();
        }
    }
    
    /**
     * Creates two bundles, artifacts an a single target and then in a loop creates two
     * features that link to the bundle artifacts, one distribution and links all of that
     * to the target. Even though we create a lot of entities that way, all of them will
     * result in the two bundles being deployed to the target so we end up with only a single
     * deployment version.
     */
    public void testCreateLotsOfEntities() throws Exception {
        int nr = 20;
        Client c = Client.create();
        c.getProperties().put(ClientConfig.PROPERTY_FOLLOW_REDIRECTS, false);
        
        File b1 = new File("b1.jar");
        File b2 = new File("b2.jar");
        createBundleOnDisk(b1, "b1", "1.0.0");
        createBundleOnDisk(b2, "b2", "1.0.0");
        b1.deleteOnExit();
        b2.deleteOnExit();
        
        WebResource w1 = createWorkspace(c);
        createBundle(c, w1, "a1", "b1", "1.0.0", b1.toURI().toURL().toString(), BundleHelper.MIMETYPE);
        createBundle(c, w1, "a2", "b2", "1.0.0", b2.toURI().toURL().toString(), BundleHelper.MIMETYPE);
        createTarget(c, w1, "t1");
        w1.post();
        w1.delete();
        for (int i = 0; i < nr; i++) {
            WebResource w = createWorkspace(c);
            createAssociationA2F(c, w, "artifact2feature", "a1", "feat-1-" + i);
            createAssociationA2F(c, w, "artifact2feature", "a2", "feat-2-" + i);
            createFeature(c, w, "feat-1-" + i);
            createFeature(c, w, "feat-2-" + i);
            createAssociationF2D(c, w, "feature2distribution", "feat-1-" + i, "dist-" + i);
            createAssociationF2D(c, w, "feature2distribution", "feat-2-" + i, "dist-" + i);
            createDistribution(c, w, "dist-" + i);
            createAssociationD2T(c, w, "distribution2target", "dist-" + i, "t1");
            w.post();
            w.delete();
        }
        WebResource t1versions = c.resource("http://localhost:8080/deployment/t1/versions");
        assertEquals("1.0.0\n", t1versions.get(String.class));
    }    
    
    /** Asserts that a list of entities exist by trying to GET them. */
    private void assertEntitiesExist(WebResource... entities) throws Exception {
        for (WebResource r : entities) {
            r.get(String.class);
        }
    }
    
    /**
     * Asserts that a collection of resources exist by trying to GET the list, validate the
     * number of items and finally GET each item.
     */
    private void assertResources(Gson gson, WebResource w2, String type, int number) {
        String[] artifacts = gson.fromJson(w2.path(type).get(String.class), String[].class);
        assertEquals("Wrong number of " + type + "s", number, artifacts.length);
        for (String id : artifacts) {
            w2.path(type + "/" + id).get(String.class);
        }
    }

    /** Creates a new workspace. */
    private WebResource createWorkspace(Client c) {
        WebResource r = c.resource("http://localhost:8080/client/work");
        try {
            r.post(String.class, "");
            fail("We should have been redirected to a new workspace.");
            return null; // to keep the compiler happy, it does not understand what fail() does
        }
        catch (UniformInterfaceException e) {
            return c.resource(e.getResponse().getLocation());
        }
    }

    /** Creates a bundle artifact. */
    private WebResource createBundle(Client c, WebResource work, String name, String bsn, String v, String url, String mimetype) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "Bundle-SymbolicName: \"" + bsn + "\", " +
            "Bundle-Version: \"" + v + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            "url: \"" + url + "\"" +
            "}, tags: {}}");
    }

    /** Creates a resource processor bundle artifact. */
    private WebResource createResourceProcessor(Client c, WebResource work, String name, String bsn, String v, String url, String mimetype, String processorID) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "description: \"\", " +
            "Bundle-SymbolicName: \"" + bsn + "\", " +
            "Bundle-Version: \"" + v + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            BundleHelper.KEY_RESOURCE_PROCESSOR_PID + ": \"" + processorID + "\", " +
            "DeploymentPackage-Customizer: \"true\", " +
            "url: \"" + url + "\"" +
            "}, tags: {}}");
    }

    /** Creates a configuration artifact. */
    private WebResource createConfiguration(Client c, WebResource work, String name, String url, String mimetype, String filename, String processorID) throws IOException {
        return createEntity(c, work, "artifact", "{attributes: {" +
            "artifactName: \"" + name + "\", " +
            "filename: \"" + filename + "\", " +
            "mimetype: \"" + mimetype + "\", " +
            "url: \"" + url + "\", " +
            ArtifactObject.KEY_PROCESSOR_PID + ": \"" + processorID + "\"" +
            "}, tags: {}}");
    }
    
    /** Creates a feature. */
    private WebResource createFeature(Client c, WebResource work, String name) throws IOException {
        return createEntity(c, work, "feature", "{attributes: {name: \"" + name + "\"}, tags: {}}");
    }

    /** Creates a distribution. */
    private WebResource createDistribution(Client c, WebResource work, String name) throws IOException {
        return createEntity(c, work, "distribution", "{attributes: {name: \"" + name + "\"}, tags: {}}");
    }

    /** Creates a target. */
    private WebResource createTarget(Client c, WebResource work, String name, String... tags) throws IOException {
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < tags.length; i += 2) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(tags[i] + ": \"" + tags[i + 1] + "\"");
        }
        return createEntity(c, work, "target", "{attributes: {id: \"" + name + "\", autoapprove: \"true\"}, tags: {" + result.toString() + "}}");
    }

    /** Creates an association between an artifact and a feature. */
    private WebResource createAssociationA2F(Client c, WebResource work, String type, String left, String right) throws IOException {
        return createEntity(c, work, type, "{attributes: {leftEndpoint: \"(artifactName=" + left + ")\", rightEndpoint=\"(name=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }

    /** Creates an association between a feature and a distribution. */
    private WebResource createAssociationF2D(Client c, WebResource work, String type, String left, String right) throws IOException {
        return createEntity(c, work, type, "{attributes: {leftEndpoint: \"(name=" + left + ")\", rightEndpoint=\"(name=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }
    
    /** Creates an association between a distribution and a target. */
    private WebResource createAssociationD2T(Client c, WebResource work, String type, String left, String right) throws IOException {
        return createEntity(c, work, type, "{attributes: {leftEndpoint: \"(name=" + left + ")\", rightEndpoint=\"(id=" + right + ")\", leftCardinality: \"1\", rightCardinality=\"1\"}, tags: {}}");
    }

    /** Creates an entity. */
    private WebResource createEntity(Client c, WebResource work, String type, String data) throws IOException {
        WebResource entity = work.path(type);
        try {
            entity.post(String.class, data);
            throw new IOException("Could not create " + type + " with data " + data);
        }
        catch (UniformInterfaceException e2) {
            return c.resource(e2.getResponse().getLocation());
        }
    }

    /** Creates a bundle on disk, using the specified file, symbolic name and version. */
    private void createBundleOnDisk(File f, String bsn, String v, String... headers) throws Exception {
        Builder b = new Builder();
        try {
	        b.setProperty("Bundle-SymbolicName", bsn);
	        b.setProperty("Bundle-Version", v);
	        for (int i = 0; i < headers.length; i += 2) {
	            b.setProperty(headers[i], headers[i + 1]);
	        }
	        Jar jar = b.build();
	        jar.getManifest(); // Not sure whether this is needed...
	        jar.write(f);
        } finally {
        	b.close();
        }
    }
    
    /** Configure the server for this test. */
    private void configureServer() throws IOException {
        configure("org.apache.ace.client.rest",
            "org.apache.ace.server.servlet.endpoint", "/client",
            "authentication.enabled", "false");
        
        configure("org.apache.ace.deployment.servlet",
            "org.apache.ace.server.servlet.endpoint", "/deployment",
            "authentication.enabled", "false");

        configure("org.apache.ace.repository.servlet.RepositoryServlet",
            "org.apache.ace.server.servlet.endpoint", "/repository",
            "authentication.enabled", "false");

        configure("org.apache.ace.obr.servlet",
            "org.apache.ace.server.servlet.endpoint", "/obr",
            "authentication.enabled", "false");

        configure("org.apache.ace.obr.storage.file",
            "fileLocation", "store");

        configure("org.apache.ace.deployment.provider.repositorybased",
            "url", "http://localhost:8080/repository",
            "name", "deployment",
            "customer", "apache");

        configure("org.apache.ace.discovery.property",
            "serverURL", "http://localhost:8080");
        
        configure("org.apache.ace.identification.property",
            "targetID", "target-test");
        
        configureFactory("org.apache.ace.server.log.servlet.factory",
            "name", "auditlog",
            HttpConstants.ENDPOINT, "/auditlog",
            "authentication.enabled", "false");
    
        configureFactory("org.apache.ace.server.log.store.factory",
            "name", "auditlog");
        
        configureFactory("org.apache.ace.server.repository.factory",
            "name", "user",
            "customer", "apache",
            "master", "true"
            );
        
        configureFactory("org.apache.ace.server.repository.factory",
            "name", "shop",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "deployment",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "target",
            "customer", "apache",
            "master", "true");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "users",
            "customer", "apache",
            "master", "true");
        
        configure("org.apache.ace.configurator.useradmin.task.UpdateUserAdminTask",
            "repositoryLocation", "http://localhost:8080/repository",
            "repositoryCustomer", "apache",
            "repositoryName", "user");
    }
    
    /** Create a user so we can log in to the server. */
    @SuppressWarnings("unchecked")
    private void createServerUser() {
        User user = (User) m_user.createRole("d", Role.USER);
        user.getProperties().put("username", "d");
        user.getCredentials().put("password", "f");
    }
}
