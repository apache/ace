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
import java.io.PrintStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.deployment.verifier.VerifierService;
import org.apache.ace.deployment.verifier.VerifierService.VerifyEnvironment;
import org.apache.ace.deployment.verifier.VerifierService.VerifyReporter;
import org.apache.ace.webui.NamedObject;
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
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.PopupView;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.VerticalLayout;

public class ACEVerifierExtension implements UIExtensionFactory {
    volatile VerifierService m_verifier;
    volatile DeploymentVersionRepository m_repo;

    public Component create(Map<String, Object> context) {
        RepositoryObject object = getRepositoryObjectFromContext(context);
        if (object instanceof StatefulGatewayObject) {
            StatefulGatewayObject statefulTarget = (StatefulGatewayObject) object;
            if (statefulTarget.isRegistered()) {
                String id = object.getAttribute(GatewayObject.KEY_ID);
                return new ManifestArea(id, (object.getAttribute("manifest") == null) ? Constants.BUNDLE_MANIFESTVERSION + ": " + "2" + "\n" + Constants.BUNDLE_SYMBOLICNAME + ": " + "org.apache.felix.framework" + "\n" + Constants.EXPORT_PACKAGE + ": " + VerifierService.SYSTEM_PACKAGES + "," + VerifierService.JRE_1_6_PACKAGES + "," + "org.osgi.service.cm;version=1.2,org.osgi.service.metatype;version=1.1.1,org.osgi.service.cm; version=1.3.0,org.osgi.service.deploymentadmin.spi; version=1.0.1,org.osgi.service.deploymentadmin; version=1.1.0" + "\n" : object.getAttribute("manifest"), object);
            }
        }
        VerticalLayout result = new VerticalLayout();
        result.setCaption("VerifyResolve");
        result.addComponent(new Label("This target is not yet registered, so it can not verify anything."));
        return result;
    }

    private RepositoryObject getRepositoryObjectFromContext(Map<String, Object> context) {
        Object contextObject = context.get("object");
        if (contextObject == null) {
            throw new IllegalStateException("No context object found");
        }
        // It looks like there is some bug (or some other reason that escapes
        // me)
        // why ace is using either the object directly or wraps it in a
        // NamedObject first.
        // Its unclear when it does which so for now we cater for both.
        return ((RepositoryObject) (contextObject instanceof NamedObject ? ((NamedObject) contextObject)
                .getObject() : contextObject));
    }
    
    class ManifestArea extends VerticalLayout implements Property.ValueChangeListener {
        private volatile String text;
        private final com.vaadin.ui.TextArea editor;
        private final String m_id;
        private TextArea plainText;
        private final RepositoryObject m_object;

        public ManifestArea(String id, String initialText, RepositoryObject object) {
            m_object = object;
            setCaption("VerifyResolve");
            m_id = id;
            setSpacing(true);
            setWidth("100%");
            text = initialText;
            editor = new com.vaadin.ui.TextArea(null, initialText);
            editor.setRows(18);
            editor.addListener(this);
            editor.setImmediate(true);
            editor.setWidth("100%");
            editor.setHeight("70%");
            addComponent(editor);

            Button verify = new Button("Verify");
            verify.addListener(new ClickListener() {
                private PopupView popup;

                public void buttonClick(ClickEvent event) {
                    plainText.setReadOnly(false);
                    try {
                        DeploymentVersionObject version = m_repo.getMostRecentDeploymentVersion(m_id);
                        if (version != null) {
                            DeploymentArtifact[] artifacts = version.getDeploymentArtifacts();
                            ByteArrayOutputStream output = new ByteArrayOutputStream();
                            final PrintStream out = new PrintStream(output);
                            Set<BundleRevision> bundles = new HashSet<BundleRevision>();
                            StringTokenizer tok = new StringTokenizer(editor.getValue().toString(), ":\n");
                            Map<String, String> manMap = new HashMap<String, String>();
                            while (tok.hasMoreTokens()) {
                                manMap.put(tok.nextToken(), tok.nextToken());
                            }
                            String ee = manMap.get(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
                            if (ee == null) {
                                ee = VerifierService.EE_1_6;
                            }
                            final Map<String, String> envMap = new HashMap<String, String>();
                            envMap.put(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, ee);
                            VerifyEnvironment env = m_verifier.createEnvironment(envMap, new VerifyReporter() {
                                public void reportWire(BundleRevision importer, BundleRequirement reqirement, BundleRevision exporter, BundleCapability capability) {
                                    out.println("WIRE: " + reqirement + " -> " + capability + "\n");
                                }

                                public void reportLog(LogEntry logEntry) {
                                    out.println("Log(" + logEntry.getTime() + "): " + logEntry.getLevel() + " " + logEntry.getMessage());
                                    if (logEntry.getException() != null) {
                                        logEntry.getException().printStackTrace();
                                    }
                                }

                                public void reportException(Exception ex) {
                                    ex.printStackTrace(out);
                                }
                            });
                            bundles.add(env.addBundle(0, manMap));
                            Set<String> customizers = new HashSet<String>();
                            Set<String> processors = new HashSet<String>();
                            for (DeploymentArtifact data : artifacts) {
                                if (data.getDirective(Constants.BUNDLE_SYMBOLICNAME) != null) {
                                    JarInputStream input = null;
                                    try {
                                        input = new JarInputStream(new URL(data.getUrl()).openStream(), false);
                                        final Attributes attributes = input.getManifest().getMainAttributes();
                                        Map<String, String> manifest = new HashMap<String, String>();
                                        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
                                            manifest.put(entry.getKey().toString(), entry.getValue().toString());
                                        }
                                        bundles.add(env.addBundle(bundles.size(), manifest));
                                        if (attributes.getValue("DeploymentPackage-Customizer") != null) {
                                            String typeString = attributes.getValue("Deployment-ProvidesResourceProcessor");
                                            if (typeString != null) {
                                                String[] types = typeString.split(",");
                                                for (String type : types) {
                                                    customizers.add(type.trim());
                                                }
                                            }
                                        }
                                    }
                                    catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                    finally {
                                        if (input != null) {
                                            try {
                                                input.close();
                                            }
                                            catch (Exception ex) {
                                                ex.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                else {
                                    String processor = data.getDirective("Resource-Processor");
                                    if (processor != null) {
                                        processors.add(processor.trim());
                                    }
                                }
                            }
                            plainText.setValue(("Customizers match: " + customizers.containsAll(processors) + "\n" + " (provided=" + customizers + ",required=" + processors + ")\n\n" + "Resolve: " + env.verifyResolve(bundles, null, null) + "\n\n" + output.toString()));
                        }
                    }
                    catch (Exception e) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        e.printStackTrace(new PrintStream(output));
                        plainText.setValue(output.toString());
                    }
                    if (popup != null) {
                        removeComponent(popup);
                    }
                    plainText.setReadOnly(true);
                    popup = new PopupView("Result", plainText);
                    popup.setHideOnMouseOut(false);
                    addComponent(popup);
                    popup.setPopupVisible(true);
                }
            });
            addComponent(verify);
            plainText = new TextArea();
            plainText.setImmediate(true);
            plainText.setWidth("600px");
            plainText.setHeight("400px");
        }
        
        public void valueChange(ValueChangeEvent event) {
            String text = (String) editor.getValue();
            if (text != null) {
            	m_object.addAttribute("manifest", text);
            }
        }
    }
}
