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
package org.apache.ace.deployment.verifier.ui;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.deployment.verifier.VerifierService;
import org.apache.ace.deployment.verifier.VerifierService.VerifyEnvironment;
import org.apache.ace.deployment.verifier.VerifierService.VerifyReporter;
import org.apache.ace.webui.UIExtensionFactory;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.log.LogEntry;

import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.PopupView;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.VerticalLayout;

public class ACEVerifierExtension implements UIExtensionFactory {

    /**
     *
     */
    final class ManifestArea extends VerticalLayout implements Property.ValueChangeListener, Button.ClickListener {
        private final String m_id;
        private final TextArea m_editor;
        private final Label m_plainText;
        private final StatefulTargetObject m_object;
        private final PopupView m_popup;

        public ManifestArea(String id, String initialText, StatefulTargetObject object) {
            setWidth("100%");

            m_id = id;
            m_object = object;

            m_editor = new TextArea(null, initialText);
            m_editor.setRows(15);
            m_editor.addListener(this);
            m_editor.setImmediate(true);
            m_editor.setWidth("100%");
            m_editor.setHeight("70%");

            m_plainText = new Label();
            m_plainText.setContentMode(Label.CONTENT_XHTML);
            m_plainText.setImmediate(true);
            m_plainText.setSizeFull();

            Panel panel = new Panel();
            panel.setCaption("Verification result");
            panel.getContent().addComponent(m_plainText);
            panel.setWidth("800px");
            panel.setHeight("300px");

            m_popup = new PopupView("Result", panel);
            m_popup.setCaption("Verification result");
            m_popup.setHideOnMouseOut(false);
            m_popup.setVisible(false);

            Button verify = new Button("Verify", this);

            addComponent(m_editor);
            addComponent(verify);
            addComponent(m_popup);
        }

        public void buttonClick(ClickEvent event) {
            if (m_popup.isPopupVisible()) {
                m_popup.setPopupVisible(false);
            }

            String output;
            try {
                String manifest = m_editor.getValue().toString();
                output = verify(m_id, manifest);
            }
            catch (Exception e) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(baos));
                output = baos.toString();
            }

            m_plainText.setValue(output);

            m_popup.setVisible(true);
            m_popup.setPopupVisible(true);
        }

        public void valueChange(ValueChangeEvent event) {
            String text = (String) m_editor.getValue();

            if (text != null) {
                m_object.addAttribute("manifest", text);
            }
        }
    }

    // Injected by Dependency Manager
    private volatile VerifierService m_verifier;
    private volatile DeploymentVersionRepository m_repo;
    private volatile ConnectionFactory m_connectionFactory;

    /**
     * {@inheritDoc}
     */
    public Component create(Map<String, Object> context) {
        StatefulTargetObject target = getRepositoryObjectFromContext(context);

        Component content = new Label("This target is not yet registered, so it can not verify anything.");
        if (target.isRegistered()) {
            content = new ManifestArea(target.getID(), getManifest(target), target);
        }

        VerticalLayout result = new VerticalLayout();
        result.setMargin(true);
        result.setCaption("Verify/resolve");
        result.addComponent(content);

        return result;
    }

    /**
     * Performs the actual verification.
     */
    final String verify(String targetId, String manifestText) throws Exception {
        DeploymentVersionObject version = m_repo.getMostRecentDeploymentVersion(targetId);
        if (version == null) {
            return "No deployment version available to verify.";
        }

        VerificationResult result = new VerificationResult();
        Map<String, String> manifestMap = getManifestEntries(manifestText);

        VerifyEnvironment env = createVerifyEnvironment(manifestMap, result);

        // Add the main entry...
        result.addBundle(env, manifestMap);

        processArtifacts(version.getDeploymentArtifacts(), env, result);

        StringBuilder sb = new StringBuilder();
        if (result.hasCustomizers()) {
            if (!result.allCustomizerMatch()) {
                sb.append("<p><b>Not all bundle customizers match!</b><br/>");
                sb.append("Provided = ").append(result.getCustomizers().toString()).append("<br/>");
                sb.append("Required = ").append(result.getProcessors().toString()).append(".</p>");
            }
            else {
                sb.append("<p>All bundle customizers match!</p>");
            }
        }

        boolean resolves = env.verifyResolve(result.getBundles(), null, null);
        if (resolves) {
            sb.append("<p>Deployment package resolves.<br/>");
        }
        else {
            sb.append("<p>Deployment package does <b>not</b> resolve!<br/>");
        }

        sb.append("Details:<br/>");
        sb.append(result.toString()).append("</p>");

        return sb.toString();
    }

    /**
     * Quietly closes a given {@link Closeable}.
     *
     * @param closeable
     *            the closeable to close, can be <code>null</code>.
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException ex) {
                // Ignore quietly...
            }
        }
    }

    /**
     * Factory method to create a suitable {@link VerifyEnvironment} instance.
     *
     * @param manifest
     *            the manifest to use;
     * @param verifyResult
     *            the verification result to use.
     * @return a new {@link VerifyEnvironment} instance, never <code>null</code>.
     */
    @SuppressWarnings("deprecation")
    private VerifyEnvironment createVerifyEnvironment(Map<String, String> manifest, final VerificationResult verifyResult) {
        String ee = manifest.get(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
        if (ee == null) {
            ee = VerifierService.EE_1_6;
        }

        Map<String, String> envMap = Collections.singletonMap(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, ee);

        VerifyEnvironment env = m_verifier.createEnvironment(envMap, new VerifyReporter() {
            public void reportException(Exception ex) {
                ex.printStackTrace(verifyResult.m_out);
            }

            public void reportLog(LogEntry logEntry) {
                verifyResult.m_out.printf("Log (%l): [%s] %s", logEntry.getTime(), logEntry.getLevel(), logEntry.getMessage());

                Throwable ex = logEntry.getException();
                if (ex != null) {
                    ex.printStackTrace(verifyResult.m_out);
                }
            }

            public void reportWire(BundleRevision importer, BundleRequirement requirement, BundleRevision exporter,
                BundleCapability capability) {
                verifyResult.m_out.println("<tt>WIRE: " + requirement + " -> " + capability + "</tt><br/>");
            }
        });
        return env;
    }

    /**
     * Returns a "static"/hardcoded manifest.
     *
     * @return a manifest, never <code>null</code>.
     */
    private String defineStaticManifest() {
        // @formatter:off
        return Constants.BUNDLE_MANIFESTVERSION + ": 2\n" +
               Constants.BUNDLE_SYMBOLICNAME + ": org.apache.felix.framework\n" +
               Constants.EXPORT_PACKAGE + ": " + VerifierService.SYSTEM_PACKAGES + "," + VerifierService.JRE_1_6_PACKAGES + "," +
               "org.osgi.service.cm; version=1.2," +
               "org.osgi.service.metatype; version=1.1.1," +
               "org.osgi.service.cm; version=1.3.0," +
               "org.osgi.service.deploymentadmin.spi; version=1.0.1," +
               "org.osgi.service.deploymentadmin; version=1.1.0\n";
        // @formatter:on
    }

    /**
     * Returns the manifest for a given repository object.
     * <p>
     * In case the given repository object does not provide a manifest, this method will return a hard-coded manifest.
     * </p>
     *
     * @param object
     *            the repository object to get the manifest for, cannot be <code>null</code>.
     * @return a manifest, never <code>null</code>.
     */
    private String getManifest(RepositoryObject object) {
        String manifest = object.getAttribute("manifest");
        if (manifest == null) {
            manifest = defineStaticManifest();
        }
        return manifest;
    }

    /**
     * Converts a given {@link Attributes} into a map.
     *
     * @param attributes
     *            the attributes to convert, cannot be <code>null</code>.
     * @return a manifest map, never <code>null</code>.
     */
    private Map<String, String> getManifestEntries(final Manifest manifest) {
        Attributes attributes = manifest.getMainAttributes();

        Map<String, String> entries = new HashMap<>();
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            entries.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return entries;
    }

    /**
     * @param manifestText
     * @return
     */
    private Map<String, String> getManifestEntries(String manifestText) {
        StringTokenizer tok = new StringTokenizer(manifestText, ":\n");

        Map<String, String> manMap = new HashMap<>();
        while (tok.hasMoreTokens()) {
            manMap.put(tok.nextToken(), tok.nextToken());
        }
        return manMap;
    }

    private StatefulTargetObject getRepositoryObjectFromContext(Map<String, Object> context) {
        return (StatefulTargetObject) context.get("object");
    }

    /**
     * Processes all artifacts.
     *
     * @param artifacts
     *            the artifacts to process.
     * @param env
     *            the environment to use;
     * @param verifyResult
     *            the verification result, cannot be <code>null</code>.
     */
    private void processArtifacts(DeploymentArtifact[] artifacts, VerifyEnvironment env, VerificationResult verifyResult) {
        String dir;
        for (DeploymentArtifact artifact : artifacts) {
            if (artifact.getDirective(Constants.BUNDLE_SYMBOLICNAME) != null) {
                processBundle(artifact, env, verifyResult);
            }
            else if ((dir = artifact.getDirective("Resource-Processor")) != null) {
                verifyResult.addProcessor(dir);
            }
        }
    }

    /**
     * Processes a single bundle.
     *
     * @param bundle
     *            the bundle to process;
     * @param env
     *            the environment to use;
     * @param verifyResult
     *            the verification result, cannot be <code>null</code>.
     */
    private void processBundle(DeploymentArtifact bundle, VerifyEnvironment env, VerificationResult verifyResult) {
        InputStream is = null;
        JarInputStream jis = null;

        try {
            is = getBundleContents(bundle.getUrl());
            jis = new JarInputStream(is, false /* verify */);

            Map<String, String> manifest = getManifestEntries(jis.getManifest());

            verifyResult.addBundle(env, manifest);

            if (manifest.get("DeploymentPackage-Customizer") != null) {
                String typeString = manifest.get("Deployment-ProvidesResourceProcessor");
                if (typeString != null) {
                    String[] types = typeString.split(",");
                    for (String type : types) {
                        verifyResult.addCustomizer(type);
                    }
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace(verifyResult.m_out);
        }
        finally {
            closeQuietly(is);
            closeQuietly(jis);
        }
    }

    /**
     * @param url
     *            the remote URL to connect to, cannot be <code>null</code>.
     * @return an {@link InputStream} to the remote URL, never <code>null</code>.
     * @throws IOException
     *             in case of I/O problems opening the remote connection.
     */
    private InputStream getBundleContents(String url) throws IOException {
        URLConnection conn = m_connectionFactory.createConnection(new URL(url));
        return conn.getInputStream();
    }
}
