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
package org.apache.ace.client.rest.itest;

import static org.apache.ace.client.rest.itest.ClientRestUtils.createAssociationA2F;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createAssociationD2T;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createAssociationF2D;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createBundleArtifact;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createClient;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createConfiguration;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createDistribution;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createFeature;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createResourceProcessor;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createTarget;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createTmpConfigOnDisk;
import static org.apache.ace.client.rest.itest.ClientRestUtils.createWorkspace;
import static org.apache.ace.client.rest.itest.ClientRestUtils.deleteResources;
import static org.apache.ace.client.rest.itest.ClientRestUtils.ensureCleanStore;
import static org.apache.ace.test.utils.FileUtils.createEmptyBundle;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Enumeration;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.rest.util.Client;
import org.apache.ace.client.rest.util.WebResource;
import org.apache.ace.client.rest.util.WebResourceException;
import org.apache.ace.client.rest.util.WebResponse;
import org.apache.ace.it.IntegrationTestBase;
import org.apache.ace.test.constants.TestConstants;
import org.apache.ace.test.utils.NetUtils;
import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.google.gson.Gson;

public class RESTClientTest extends IntegrationTestBase {
    // From: ConfigurationHelper (somehow directly using them here fails)
    public static final String KEY_FILENAME = "filename";
    public static final String MIMETYPE = "application/xml:osgi-autoconf";
    public static final String PROCESSOR = "org.osgi.deployment.rp.autoconf";

    private static final String STOREPATH = "generated/store";
    private static final String HOST = "http://localhost:" + TestConstants.PORT;
    private static final Version V1_0_0 = new Version(1, 0, 0);

    private static boolean m_hasBeenSetup = false;
    private static int m_testRunCount = 0;
    private static int m_totalTestCount = 0;

    private volatile BundleContext m_context;
    private volatile UserAdmin m_user;
    private volatile LogReaderService m_logReader;

    /**
     * Creates and deletes a number of workspaces.
     */
    public void testCreateAndDeleteMultipleWorkspaces() throws Exception {
        Client client = createClient();
        try {
            int nr = 10;
            for (int i = 0; i < nr; i++) {
                WebResource w1 = createWorkspace(HOST, client);
                w1.delete();
            }
            WebResource[] w = new WebResource[nr];
            for (int i = 0; i < w.length; i++) {
                w[i] = createWorkspace(HOST, client);
            }
            for (int i = 0; i < w.length; i++) {
                w[i].delete();
            }
        }
        catch (Exception e) {
            showBundles();
            showLog();
            throw e;
        }
    }

    /**
     * Creates a new workspace, ensures it works correctly by asking for a list of entity types, then deletes the
     * workspace again and ensures it's no longer available.
     */
    public void testCreateAndDestroyRESTSession() throws Exception {
        Client client = createClient();
        try {
            WebResource r = client.resource(HOST.concat("/client/work/"));
            try {
                r.post();
                fail("We should have been redirected to a new workspace.");
            }
            catch (WebResourceException e) {
                WebResponse response = e.getResponse();
                URI location = response.getLocation();
                assertTrue(location.toString(), location.toString().startsWith(HOST.concat("/client/work/rest-")));
                WebResource r2 = client.resource(location);
                r2.getString();
                r2.delete();
                try {
                    r2.getString();
                }
                catch (WebResourceException e2) {
                    assertEquals(404, e2.getResponse().getStatus());
                }
            }
        }
        catch (Exception e) {
            showBundles();
            showLog();
            throw e;
        }
    }

    /**
     * Creates two bundles, artifacts an a single target and then in a loop creates two features that link to the bundle
     * artifacts, one distribution and links all of that to the target. Even though we create a lot of entities that
     * way, all of them will result in the two bundles being deployed to the target so we end up with only a single
     * deployment version.
     */
    public void testCreateLotsOfEntities() throws Exception {
        Client client = createClient();
        Gson gson = new Gson();
        try {
            int nr = 20;
            File b1 = createEmptyBundle("bar.b1", V1_0_0);
            File b2 = createEmptyBundle("bar.b2", V1_0_0);

            WebResource w1 = createWorkspace(HOST, client);
            deleteResources(gson, w1);
            createBundleArtifact(client, w1, "bar.a1", "bar.b1", "1.0.0", b1);
            createBundleArtifact(client, w1, "bar.a2", "bar.b2", "1.0.0", b2);
            createTarget(client, w1, "bar.t1");
            w1.post();
            w1.delete();

            for (int i = 0; i < nr; i++) {
                WebResource w2 = createWorkspace(HOST, client);
                createAssociationA2F(client, w2, "bar.a1", "feat-1-" + i);
                createAssociationA2F(client, w2, "bar.a2", "feat-2-" + i);
                createFeature(client, w2, "feat-1-" + i);
                createFeature(client, w2, "feat-2-" + i);
                createAssociationF2D(client, w2, "feat-1-" + i, "dist-" + i);
                createAssociationF2D(client, w2, "feat-2-" + i, "dist-" + i);
                createDistribution(client, w2, "dist-" + i);
                createAssociationD2T(client, w2, "dist-" + i, "bar.t1");
                w2.post();
                w2.delete();
            }
            WebResource t1versions = client.resource(HOST.concat("/deployment/bar.t1/versions"));
            assertEquals("1.0.0\n", t1versions.getString());
        }
        catch (Exception e) {
            showBundles();
            showLog();
            throw e;
        }
    }

    /**
     * Creates a workspace, three bundle artifacts that are associated to three features. The features are all grouped
     * into a single distribution, and the distribution is associated with a target. This workspace is then committed
     * and a new workspace is opened. From this new workspace, we read back the entities we committed before. Then, we
     * check if this has indeed led to a new version of the software for this target.
     */
    public void testDeployBundlesToTarget() throws Exception {
        Client client = createClient();
        Gson gson = new Gson();
        try {
            File b1 = createEmptyBundle("foo.b1", V1_0_0);
            File b2 = createEmptyBundle("foo.b2", V1_0_0);
            File b3 = createEmptyBundle("foo.b3", V1_0_0);

            WebResource w1 = createWorkspace(HOST, client);
            deleteResources(gson, w1);

            WebResource a1 = createBundleArtifact(client, w1, "foo.a1", "foo.b1", "1.0.0", b1);
            WebResource a2 = createBundleArtifact(client, w1, "foo.a2", "foo.b2", "1.0.0", b2);
            WebResource a3 = createBundleArtifact(client, w1, "foo.a3", "foo.b3", "1.0.0", b3);
            assertEntitiesExist(a1, a2, a3);
            WebResource a1f1 = createAssociationA2F(client, w1, "foo.a1", "foo.f1");
            WebResource a2f2 = createAssociationA2F(client, w1, "foo.a2", "foo.f2");
            WebResource a3f3 = createAssociationA2F(client, w1, "foo.a3", "foo.f3");
            assertEntitiesExist(a1f1, a2f2, a3f3);
            WebResource f1 = createFeature(client, w1, "foo.f1");
            WebResource f2 = createFeature(client, w1, "foo.f2");
            WebResource f3 = createFeature(client, w1, "foo.f3");
            assertEntitiesExist(f1, f2, f3);
            WebResource f1d1 = createAssociationF2D(client, w1, "foo.f1", "foo.d1");
            WebResource f2d1 = createAssociationF2D(client, w1, "foo.f2", "foo.d1");
            WebResource f3d1 = createAssociationF2D(client, w1, "foo.f3", "foo.d1");
            assertEntitiesExist(f1d1, f2d1, f3d1);
            WebResource d1 = createDistribution(client, w1, "foo.d1");
            assertEntitiesExist(d1);
            WebResource d1t1 = createAssociationD2T(client, w1, "foo.d1", "foo.t1");
            assertEntitiesExist(d1t1);
            WebResource t1 = createTarget(client, w1, "foo.t1");
            assertEntitiesExist(t1);
            w1.post();
            w1.delete();

            WebResource w2 = createWorkspace(HOST, client);
            assertResources(gson, w2, "artifact", 3);
            assertResources(gson, w2, "artifact2feature", 3);
            assertResources(gson, w2, "feature", 3);
            assertResources(gson, w2, "feature2distribution", 3);
            assertResources(gson, w2, "distribution", 1);
            assertResources(gson, w2, "distribution2target", 1);
            assertResources(gson, w2, "target", 1);
            w2.post();
            w2.delete();

            WebResource t1versions = client.resource(HOST.concat("/deployment/foo.t1/versions"));
            assertEquals("1.0.0\n", t1versions.getString());
        }
        catch (Exception e) {
            showBundles();
            showLog();
            throw e;
        }
    }

    public void testDeployConfigurationTemplateToTargets() throws Exception {
        Client client = createClient();
        Gson gson = new Gson();
        try {
            File bundle = createEmptyBundle("rp", V1_0_0, BundleHelper.KEY_RESOURCE_PROCESSOR_PID, PROCESSOR, "DeploymentPackage-Customizer", "true");
            File config = createTmpConfigOnDisk(
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
                    "</MetaData>\n");

            WebResource w1 = createWorkspace(HOST, client);
            deleteResources(gson, w1);

            createResourceProcessor(client, w1, "rp", "resourceprocessor", "1.0.0", bundle.toURI().toURL().toString(), BundleHelper.MIMETYPE, PROCESSOR);
            createConfiguration(client, w1, "c1", config.toURI().toURL().toString(), MIMETYPE, "template.xml", PROCESSOR);
            createAssociationA2F(client, w1, "c1", "f4");
            createFeature(client, w1, "f4");
            createAssociationF2D(client, w1, "f4", "d2");
            createDistribution(client, w1, "d2");
            createAssociationD2T(client, w1, "d2", "t4");
            createAssociationD2T(client, w1, "d2", "t5");
            createAssociationD2T(client, w1, "d2", "t6");
            createTarget(client, w1, "t4", "test", "one");
            createTarget(client, w1, "t5", "test", "two");
            createTarget(client, w1, "t6", "test", "three");
            w1.post();
            w1.delete();

            WebResource w2 = createWorkspace(HOST, client);
            assertResources(gson, w2, "artifact", 1);
            assertResources(gson, w2, "artifact2feature", 1);
            assertResources(gson, w2, "feature", 1);
            assertResources(gson, w2, "feature2distribution", 1);
            assertResources(gson, w2, "distribution", 1);
            assertResources(gson, w2, "distribution2target", 3);
            assertResources(gson, w2, "target", 3);
            w2.delete();

            WebResource t1versions = client.resource(HOST.concat("/deployment/t4/versions"));
            assertEquals("1.0.0\n", t1versions.getString());
        }
        catch (Exception e) {
            showBundles();
            showLog();
            throw e;
        }
    }

    @Override
    protected void configureAdditionalServices() throws Exception {
        // there is some setup we only want to do once, before the first test we run, and since we cannot
        // predict which one that is, we use a static flag
        if (!m_hasBeenSetup) {
            setAutoDeleteTrackedConfigurations(false);
            // count the number of tests, so we can determine when to clean up...
            m_totalTestCount = getTestCount();

            ensureCleanStore(STOREPATH);
            configureServer();
            createServerUser();

            // Wait until our RESTClientServlet is up and responding...
            NetUtils.waitForURL(HOST);

            m_hasBeenSetup = true;
        }

        m_testRunCount++;
    }

    @Override
    protected void doTearDown() throws Exception {
        if (m_testRunCount == m_totalTestCount) {
            setAutoDeleteTrackedConfigurations(true);
        }
    }

    @Override
    protected Component[] getDependencies() {
        return new Component[] {
            createComponent()
                .setImplementation(this)
                .add(createServiceDependency().setService(UserAdmin.class).setRequired(true))
                .add(createServiceDependency().setService(LogReaderService.class).setRequired(true))
        };
    }

    /** Asserts that a list of entities exist by trying to GET them. */
    private void assertEntitiesExist(WebResource... entities) throws Exception {
        for (WebResource r : entities) {
            System.out.println(r.getURI().toString());
            r.getString();
        }
    }

    /**
     * Asserts that a collection of resources exist by trying to GET the list, validate the number of items and finally
     * GET each item.
     */
    private void assertResources(Gson gson, WebResource w2, String type, int number) throws IOException {
        String[] artifacts = gson.fromJson(w2.path(type).getString(), String[].class);
        assertEquals("Wrong number of " + type + "s", number, artifacts.length);
        for (String id : artifacts) {
            w2.path(type + "/" + id).getString();
        }
    }

    private void configureServer() throws IOException {
        configure("org.apache.ace.client.repository",
            "obrlocation", HOST.concat("/obr/"));

        configure("org.apache.ace.client.rest",
            "repository.url", HOST.concat("/repository"));

        configure("org.apache.ace.obr.storage.file",
            "fileLocation", STOREPATH);

        configure("org.apache.ace.deployment.provider.repositorybased",
            "url", HOST.concat("/repository"),
            "name", "deployment",
            "customer", "apache");

        configure("org.apache.ace.discovery.property",
            "serverURL", HOST);

        configure("org.apache.ace.identification.property",
            "targetID", "target-test");

        configure("org.apache.ace.client.workspace",
            "repository.url", HOST.concat("/repository"),
            "authentication.enabled", "false",
            "user.name", "d",
            "customer.name", "apache",
            "store.repository.name", "shop",
            "distribution.repository.name", "target",
            "deployment.repository.name", "deployment");

        configureFactory("org.apache.ace.log.server.servlet.factory",
            "name", "auditlog", "endpoint", "/auditlog");

        configureFactory("org.apache.ace.log.server.store.factory",
            "name", "auditlog");

        configureFactory("org.apache.ace.server.repository.factory",
            "name", "user",
            "customer", "apache",
            "master", "true");

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

        configure("org.apache.ace.http.context", "authentication.enabled", "false");
    }

    /** Create a user so we can log in to the server. */
    private void createServerUser() {
        User user = (User) m_user.createRole("d", Role.USER);
        user.getProperties().put("username", "d");
        user.getCredentials().put("password", "f");
    }

    /** Shows all bundles in the framework. */
    private void showBundles() {
        for (Bundle b : m_context.getBundles()) {
            System.out.println(" * [" + b.getBundleId() + "] " + b.getState() + " - " + b.getSymbolicName() + " " + b.getVersion());
        }
    }

    /** Shows all log messages in the OSGi log service. */
    private void showLog() {
        Enumeration<?> e = m_logReader.getLog();
        System.out.println("Log:");
        while (e.hasMoreElements()) {
            LogEntry entry = (LogEntry) e.nextElement();
            System.out.println(" * " + (new Date(entry.getTime())) + " - " + entry.getMessage() + " - " + entry.getBundle().getBundleId() + " - " + entry.getException());
            if (entry.getException() != null) {
                entry.getException().printStackTrace();
            }
        }
    }
}
