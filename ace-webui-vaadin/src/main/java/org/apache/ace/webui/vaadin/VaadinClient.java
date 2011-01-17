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
package org.apache.ace.webui.vaadin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.object.Artifact2GroupAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.Transferable;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.TargetDetails;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.event.dd.acceptcriteria.Or;
import com.vaadin.terminal.Sizeable;
import com.vaadin.terminal.UserError;
import com.vaadin.ui.Button;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressIndicator;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.AbstractSelect.VerticalLocationIs;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Table.CellStyleGenerator;
import com.vaadin.ui.Table.TableTransferable;
import com.vaadin.ui.Upload.FailedEvent;
import com.vaadin.ui.Upload.SucceededEvent;

/*

TODO:
 - Add buttons to remove associations (think about how we can better visualize this)
 - Add buttons to remove objects
 - Handle ui updates better
 - Add functionality for adding an artifact
 - Allow live updates of the target column
 - Create a special editor for dealing with new artifact types

 - Enable drag and drop to create associations (done)
 - Add drag and drop to the artifacts column (done)
 - Add an editor that appears on double clicking on an item in a table (done)
 - Add buttons to create new items in all of the tables (done for those that make sense)
 */
public class VaadinClient extends com.vaadin.Application {
    public static final String OBJECT_NAME = "name";
	public static final String OBJECT_DESCRIPTION = "description";

	private static final long serialVersionUID = 1L;
    private static long SESSION_ID = 12345;
    private static String gatewayRepo = "gateway";
    private static String shopRepo = "shop";
    private static String deployRepo = "deployment";
    private static String customerName = "apache";
    private static String hostName = "http://localhost:8080";
    private static String endpoint = "/repository";
    private static String obr = "http://localhost:8080/obr/";

    private volatile DependencyManager m_manager;
    private volatile BundleContext m_context;
    private volatile SessionFactory m_sessionFactory;
    private volatile UserAdmin m_userAdmin;
    private volatile ArtifactRepository m_artifactRepository;
    private volatile GroupRepository m_featureRepository;
    private volatile LicenseRepository m_distributionRepository;
    private volatile StatefulGatewayRepository m_statefulTargetRepository;
    private volatile Artifact2GroupAssociationRepository m_artifact2GroupAssociationRepository;
    private volatile Group2LicenseAssociationRepository m_group2LicenseAssociationRepository;
    private volatile License2GatewayAssociationRepository m_license2GatewayAssociationRepository;
    private volatile RepositoryAdmin m_admin;
    private volatile LogService m_log;
    private String m_sessionID;
    private volatile List<LicenseObject> m_distributions;
    private ObjectPanel m_artifactsPanel;
    private ObjectPanel m_featuresPanel;
    private ObjectPanel m_distributionsPanel;
    private ObjectPanel m_targetsPanel;
    private List<ArtifactObject> m_artifacts;
    private List<GroupObject> m_features;
    private List<StatefulGatewayObject> m_targets;
    private final Associations m_associations = new Associations();
    
    private List<OBREntry> m_obrList;
    private GridLayout m_grid;

    // basic session ID generator
    private static long generateSessionID() {
        return SESSION_ID++;
    }
    
    public void setupDependencies(Component component) {
        System.out.println("SETUP " + this);
        m_sessionID = "" + generateSessionID();
        m_sessionFactory.createSession(m_sessionID);
        addDependency(component, RepositoryAdmin.class);
        addDependency(component, LicenseRepository.class);
        addDependency(component, ArtifactRepository.class);
        addDependency(component, GroupRepository.class);
        addDependency(component, Artifact2GroupAssociationRepository.class);
        addDependency(component, Group2LicenseAssociationRepository.class);
        addDependency(component, License2GatewayAssociationRepository.class);
        addDependency(component, StatefulGatewayRepository.class);
    }
    
    private void addDependency(Component component, Class service) {
        component.add(m_manager.createServiceDependency()
            .setService(service, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(true)
            .setInstanceBound(true)
        );
    }
    
    public void start() {
        System.out.println("START " + this);
    }
    
    public void stop() {
        System.out.println("STOP " + this);
    }
    
    public void destroyDependencies() {
        System.out.println("DESTROY " + this);
        m_sessionFactory.destroySession(m_sessionID);
    }
    
    
    public void init() {
        System.out.println("INIT " + this);
        
//        login();
        
        setTheme("ace");
        final Window main = new Window("Apache ACE - User Interface - " + this);
        setMainWindow(main);
        main.getContent().setSizeFull();
        
        m_grid = new GridLayout(4, 4);
        m_grid.setSpacing(true);

        m_grid.setWidth(100, Sizeable.UNITS_PERCENTAGE);
        m_grid.setHeight(100, Sizeable.UNITS_PERCENTAGE);

        m_grid.addComponent(createToolbar(), 0, 0, 3, 0);

        m_artifactsPanel = createArtifactsPanel(main);
        m_grid.addComponent(m_artifactsPanel, 0, 2);
        Button addArtifactButton = new Button("Add artifact...");
        addArtifactButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                showAddArtifactDialog(main);
            }
        });

        m_grid.addComponent(addArtifactButton, 0, 1);

        m_featuresPanel = createFeaturesPanel(main);
        m_grid.addComponent(m_featuresPanel, 1, 2);
        m_grid.addComponent(createAddFeatureButton(main), 1, 1);
        
        m_distributionsPanel = createDistributionsPanel(main);
        m_grid.addComponent(m_distributionsPanel, 2, 2);
        m_grid.addComponent(createAddDistributionButton(main), 2, 1);

        m_targetsPanel = createTargetsPanel(main);
        m_grid.addComponent(m_targetsPanel, 3, 2);
        Button addTargetButton = new Button("Add target...");
        m_grid.addComponent(addTargetButton, 3, 1); 
        addTargetButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                new AddTargetWindow(main).show();
            }
        });
        
        m_grid.setRowExpandRatio(2, 1.0f);
        
        ProgressIndicator progress = new ProgressIndicator(0f);
        progress.setPollingInterval(500);
        m_grid.addComponent(progress, 0, 3);

        m_artifactsPanel.addListener(m_associations.createSelectionListener(m_artifactsPanel, m_artifactRepository, new Class[] {}, new Class[] { GroupObject.class, LicenseObject.class, GatewayObject.class }, new Table[] { m_featuresPanel, m_distributionsPanel, m_targetsPanel }));
        m_featuresPanel.addListener(m_associations.createSelectionListener(m_featuresPanel, m_featureRepository, new Class[] { ArtifactObject.class }, new Class[] { LicenseObject.class, GatewayObject.class }, new Table[] { m_artifactsPanel, m_distributionsPanel, m_targetsPanel }));
        m_distributionsPanel.addListener(m_associations.createSelectionListener(m_distributionsPanel, m_distributionRepository, new Class[] { GroupObject.class, ArtifactObject.class }, new Class[] { GatewayObject.class }, new Table[] { m_artifactsPanel, m_featuresPanel, m_targetsPanel }));
        m_targetsPanel.addListener(m_associations.createSelectionListener(m_targetsPanel, m_statefulTargetRepository, new Class[] { LicenseObject.class, GroupObject.class, ArtifactObject.class}, new Class[] {}, new Table[] { m_artifactsPanel, m_featuresPanel, m_distributionsPanel }));

        m_artifactsPanel.setDropHandler(new AssociationDropHandler((Table) null, m_featuresPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
            }

            @Override
            protected void associateFromRight(String left, String right) {
                m_artifact2GroupAssociationRepository.create(getArtifact(left), getFeature(right));
            }
        });
        m_featuresPanel.setDropHandler(new AssociationDropHandler(m_artifactsPanel, m_distributionsPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
                m_artifact2GroupAssociationRepository.create(getArtifact(left), getFeature(right));
            }

            @Override
            protected void associateFromRight(String left, String right) {
                m_group2LicenseAssociationRepository.create(getFeature(left), getDistribution(right));
            }
        });
        m_distributionsPanel.setDropHandler(new AssociationDropHandler(m_featuresPanel, m_targetsPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
                m_group2LicenseAssociationRepository.create(getFeature(left), getDistribution(right));
            }

            @Override
            protected void associateFromRight(String left, String right) {
                StatefulGatewayObject target = getTarget(right);
                System.out.println("Target is " + target + " and is " + (target.isRegistered() ? "registered." : "not registered yet."));
                if (!target.isRegistered()) {
                    target.register();
                    target.setAutoApprove(true);
                }
                m_license2GatewayAssociationRepository.create(getDistribution(left), target.getGatewayObject());
                System.out.println("Associated!");
            }
        });
        m_targetsPanel.setDropHandler(new AssociationDropHandler(m_distributionsPanel, (Table) null) {
            @Override
            protected void associateFromLeft(String left, String right) {
                StatefulGatewayObject target = getTarget(right);
                System.out.println("Target is " + target + " and is " + (target.isRegistered() ? "registered." : "not registered yet."));
                if (!target.isRegistered()) {
                    target.register();
                    target.setAutoApprove(true);
                }
                m_license2GatewayAssociationRepository.create(getDistribution(left), target.getGatewayObject());
                System.out.println("Associated!");
            }

            @Override
            protected void associateFromRight(String left, String right) {
            }
        });

        main.addComponent(m_grid);
        
        addListener(m_artifactsPanel, ArtifactObject.TOPIC_ALL);
        addListener(m_featuresPanel, GroupObject.TOPIC_ALL);
        addListener(m_distributionsPanel, LicenseObject.TOPIC_ALL);
        addListener(m_targetsPanel, StatefulGatewayObject.TOPIC_ALL);
        
        LoginWindow loginWindow = new LoginWindow();
        main.getWindow().addWindow(loginWindow);
        loginWindow.center();
    }
    
    public class LoginWindow extends Window {
        private TextField m_name;
        private TextField m_password;
        private Button m_loginButton;
        
        public LoginWindow() {
            super("Apache ACE Login");
            setResizable(false);
            setModal(true);
            setWidth("15em");
            
            LoginPanel p = new LoginPanel();
            setContent(p);
        }
        
        public void closeWindow() {
            getParent().removeWindow(this);
        }
        
        public class LoginPanel extends VerticalLayout {
            public LoginPanel() {
                setSpacing(true);
                setMargin(true);
                setClosable(false);
                setSizeFull();
                m_name = new TextField("Name", "");
                m_password = new TextField("Password", "");
                m_password.setSecret(true);
                m_loginButton = new Button("Login");
                addComponent(m_name);
                addComponent(m_password);
                addComponent(m_loginButton);
                setComponentAlignment(m_loginButton, "center");
                m_name.focus();
                m_name.selectAll();
                m_loginButton.addListener(new Button.ClickListener() {
                    public void buttonClick(ClickEvent event) {
                        if (login((String) m_name.getValue(), (String) m_password.getValue())) {
                            closeWindow();
                        }
                        else {
                            // TODO provide some feedback, login failed, for now don't close the login window
                            m_loginButton.setComponentError(new UserError("Invalid username or password."));
                        }
                    }
                });
            }
        }
    }

	private boolean login(String username, String password) {
		try {
            User user = m_userAdmin.getUser("username", "d");
            Dictionary credentials = user.getCredentials();
            String userPassword = (String) credentials.get("password");
            if (!password.equals(userPassword)) {
                return false;
            }
            RepositoryAdminLoginContext context = m_admin.createLoginContext(user);
            
            context.addShopRepository(new URL(hostName + endpoint), customerName, shopRepo, true)
                .setObrBase(new URL(obr))
                .addGatewayRepository(new URL(hostName + endpoint), customerName, gatewayRepo, true)
                .addDeploymentRepository(new URL(hostName + endpoint), customerName, deployRepo, true);
            m_admin.login(context);
            m_admin.checkout();
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
	}
    
    private void addListener(final Object implementation, final String topic) {
        m_manager.add(m_manager.createComponent()
            .setInterface(EventHandler.class.getName(), new Properties() {{
                put(EventConstants.EVENT_TOPIC, topic);
            }})
            .setImplementation(implementation)
        );
    }

    private GridLayout createToolbar() {
        GridLayout toolbar = new GridLayout(3, 1);
        toolbar.setSpacing(true);

        Button retrieveButton = new Button("Retrieve");
        retrieveButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                try {
                    m_admin.checkout();
                    System.out.println("checkout");
                    updateTableData();
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        toolbar.addComponent(retrieveButton, 0, 0);

        Button storeButton = new Button("Store");
        storeButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                try {
                    m_admin.commit();
                    System.out.println("commit");
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        toolbar.addComponent(storeButton, 1, 0);
        Button revertButton = new Button("Revert");
        revertButton.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                try {
                    m_admin.revert();
                    System.out.println("revert");
                    updateTableData();
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        });
        toolbar.addComponent(revertButton, 2, 0);
        return toolbar;
    }

    private ObjectPanel createArtifactsPanel(Window main) {
        return new ObjectPanel(m_associations, "Artifact", main, false, false) {
            @Override
            protected RepositoryObject getFromId(String id) {
                return getArtifact(id);
            }
            private void init() {
                populate();
            }
            public void populate() {
                removeAllItems();
                for (ArtifactObject artifact : m_artifactRepository.get()) {
                    add(artifact);
                }
            }
            public void handleEvent(org.osgi.service.event.Event event) {
                ArtifactObject artifact = (ArtifactObject) event.getProperty(ArtifactObject.EVENT_ENTITY);
                String topic = (String) event.getProperty(EventConstants.EVENT_TOPIC);
                if (ArtifactObject.TOPIC_ADDED.equals(topic)) {
                    add(artifact);
                }
                if (ArtifactObject.TOPIC_REMOVED.equals(topic)) {
                    remove(artifact);
                }
                if (ArtifactObject.TOPIC_CHANGED.equals(topic)) {
                    change(artifact);
                }
            }
            private void add(ArtifactObject artifact) {
                Item item = addItem(artifact.getName());
                item.getItemProperty(OBJECT_NAME).setValue(artifact.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(artifact.getDescription());
                Button removeLinkButton = new RemoveLinkButton<ArtifactObject>(artifact, null, m_featuresPanel) {
                    @Override
                    protected void removeLinkFromLeft(ArtifactObject object, RepositoryObject other) {}

                    @Override
                    protected void removeLinkFromRight(ArtifactObject object, RepositoryObject other) {
                        List<Artifact2GroupAssociation> associations = object.getAssociationsWith((GroupObject) other);
                        for (Artifact2GroupAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_artifact2GroupAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }
                };
                HorizontalLayout buttons = new HorizontalLayout();
                buttons.addComponent(removeLinkButton);
                item.getItemProperty(ACTIONS).setValue(buttons);

            }
            private void change(ArtifactObject artifact) {
                Item item = getItem(artifact.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(artifact.getDescription());
            }
            private void remove(ArtifactObject artifact) {
                removeItem(artifact.getName());
            }
        };
    }

    private ObjectPanel createFeaturesPanel(Window main) {
        return new ObjectPanel(m_associations, "Feature", main, true, true) {
            @Override
            protected RepositoryObject getFromId(String id) {
                return getFeature(id);
            }
            private void init() {
                populate();
            }
            public void populate() {
                removeAllItems();
                for (GroupObject feature : m_featureRepository.get()) {
                    add(feature);
                }
            }
            public void handleEvent(org.osgi.service.event.Event event) {
                GroupObject feature = (GroupObject) event.getProperty(GroupObject.EVENT_ENTITY);
                String topic = (String) event.getProperty(EventConstants.EVENT_TOPIC);
                if (GroupObject.TOPIC_ADDED.equals(topic)) {
                    add(feature);
                }
                if (GroupObject.TOPIC_REMOVED.equals(topic)) {
                    remove(feature);
                }
                if (GroupObject.TOPIC_CHANGED.equals(topic)) {
                    change(feature);
                }
            }
            private void add(GroupObject feature) {
                System.out.println("FEATURE-EVENT-ADD: " + feature);
                Item item = addItem(feature.getName());
                item.getItemProperty(OBJECT_NAME).setValue(feature.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(feature.getDescription());
                Button removeLinkButton = new RemoveLinkButton<GroupObject>(feature, m_artifactsPanel, m_distributionsPanel) {
                    @Override
                    protected void removeLinkFromLeft(GroupObject object, RepositoryObject other) {
                        List<Artifact2GroupAssociation> associations = object.getAssociationsWith((ArtifactObject) other);
                        for (Artifact2GroupAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_artifact2GroupAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }

                    @Override
                    protected void removeLinkFromRight(GroupObject object, RepositoryObject other) {
                        List<Group2LicenseAssociation> associations = object.getAssociationsWith((LicenseObject) other);
                        for (Group2LicenseAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_group2LicenseAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }
                };
                HorizontalLayout buttons = new HorizontalLayout();
                buttons.addComponent(removeLinkButton);
                item.getItemProperty(ACTIONS).setValue(buttons);
            }
            private void change(GroupObject go) {
                Item item = getItem(go.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(go.getDescription());
            }
            private void remove(GroupObject go) {
                removeItem(go.getName());
            }
        };
        
    }

    public abstract class RemoveLinkButton<REPO_OBJECT extends RepositoryObject> extends Button {
        // TODO generify?
        public RemoveLinkButton(final REPO_OBJECT object, final ObjectPanel toLeft, final ObjectPanel toRight) {
            super("-");
            setStyleName("small");
            addListener(new Button.ClickListener() {
                public void buttonClick(ClickEvent event) {
                    Set<?> selection = m_associations.getActiveSelection();
                    if (selection != null) {
	                    if (m_associations.isActiveTable(toLeft)) {
                            for (Object item : selection) {
                                RepositoryObject selected = m_associations.lookupInActiveSelection(item);
                                removeLinkFromLeft(object, selected);
                            }
	                    }
	                    else if (m_associations.isActiveTable(toRight)) {
                            for (Object item : selection) {
                                RepositoryObject selected = m_associations.lookupInActiveSelection(item);
                                removeLinkFromRight(object, selected);
                            }
	                    }
                    }
                }
            });
        }

        protected abstract void removeLinkFromLeft(REPO_OBJECT object, RepositoryObject other);

        protected abstract void removeLinkFromRight(REPO_OBJECT object, RepositoryObject other);
    }
    
    
    private ObjectPanel createDistributionsPanel(Window main) {
        return new ObjectPanel(m_associations, "Distribution", main, true, true) {
            @Override
            protected RepositoryObject getFromId(String id) {
                return getDistribution(id);
            }
            private void init() {
                populate();
            }
            public void populate() {
                removeAllItems();
                for (LicenseObject distribution : m_distributionRepository.get()) {
                    add(distribution);
                }
            }
            public void handleEvent(org.osgi.service.event.Event event) {
                LicenseObject distribution = (LicenseObject) event.getProperty(LicenseObject.EVENT_ENTITY);
                String topic = (String) event.getProperty(EventConstants.EVENT_TOPIC);
                if (LicenseObject.TOPIC_ADDED.equals(topic)) {
                    add(distribution);
                }
                if (LicenseObject.TOPIC_REMOVED.equals(topic)) {
                    remove(distribution);
                }
                if (LicenseObject.TOPIC_CHANGED.equals(topic)) {
                    change(distribution);
                }
            }
            private void add(LicenseObject distribution) {
                Item item = addItem(distribution.getName());
                item.getItemProperty(OBJECT_NAME).setValue(distribution.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(distribution.getDescription());
                Button removeLinkButton = new RemoveLinkButton<LicenseObject>(distribution, m_featuresPanel, m_targetsPanel) {
                    @Override
                    protected void removeLinkFromLeft(LicenseObject object, RepositoryObject other) {
                        List<Group2LicenseAssociation> associations = object.getAssociationsWith((GroupObject) other);
                        for (Group2LicenseAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_group2LicenseAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }

                    @Override
                    protected void removeLinkFromRight(LicenseObject object, RepositoryObject other) {
                        List<License2GatewayAssociation> associations = object.getAssociationsWith((GatewayObject) other);
                        for (License2GatewayAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_license2GatewayAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }
                };
                HorizontalLayout buttons = new HorizontalLayout();
                buttons.addComponent(removeLinkButton);
                item.getItemProperty(ACTIONS).setValue(buttons);
            }
            private void change(LicenseObject distribution) {
                Item item = getItem(distribution.getName());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue(distribution.getDescription());
            }
            private void remove(LicenseObject distribution) {
                removeItem(distribution.getName());
            }
        };
    }

    private ObjectPanel createTargetsPanel(Window main) {
        return new ObjectPanel(m_associations, "Target", main, false, false) {
            @Override
            protected RepositoryObject getFromId(String id) {
                return getTarget(id);
            }
            private void init(Component component) {
                populate();
                DependencyManager dm = component.getDependencyManager();
                component.add(dm.createServiceDependency()
                    .setInstanceBound(true)
                    .setService(UIExtensionFactory.class)
                    .setCallbacks("addExtension", "removeExtension")
                );
            }
            private List<UIExtensionFactory> m_factories = new ArrayList<UIExtensionFactory>();
            public void addExtension(UIExtensionFactory factory) {
                m_factories.add(factory);
                populate();
            }
            public void removeExtension(UIExtensionFactory factory) {
                m_factories.remove(factory);
                populate();
            }
            public void populate() {
                removeAllItems();
                for (StatefulGatewayObject statefulTarget : m_statefulTargetRepository.get()) {
                    add(statefulTarget);
                }
            }
            public void handleEvent(org.osgi.service.event.Event event) {
                StatefulGatewayObject statefulTarget = (StatefulGatewayObject) event.getProperty(StatefulGatewayObject.EVENT_ENTITY);
                String topic = (String) event.getProperty(EventConstants.EVENT_TOPIC);
                if (StatefulGatewayObject.TOPIC_ADDED.equals(topic)) {
                    add(statefulTarget);
                }
                if (StatefulGatewayObject.TOPIC_REMOVED.equals(topic)) {
                    remove(statefulTarget);
                }
                if (StatefulGatewayObject.TOPIC_CHANGED.equals(topic)) {
                    change(statefulTarget);
                }
            }
            private void add(StatefulGatewayObject statefulTarget) {
                Item item = addItem(statefulTarget.getID());
                item.getItemProperty(OBJECT_NAME).setValue(statefulTarget.getID());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue("TODO");
                Button removeLinkButton = new RemoveLinkButton<StatefulGatewayObject>(statefulTarget, m_distributionsPanel, null) {
                    @Override
                    protected void removeLinkFromLeft(StatefulGatewayObject object, RepositoryObject other) {
                        List<License2GatewayAssociation> associations = object.getAssociationsWith((LicenseObject) other);
                        for (License2GatewayAssociation association : associations) {
                            System.out.println("> " + association.getLeft() + " <-> " + association.getRight());
                            m_license2GatewayAssociationRepository.remove(association);
                        }
                        m_associations.removeAssociatedItem(object);
                        m_table.requestRepaint();
                    }

                    @Override
                    protected void removeLinkFromRight(StatefulGatewayObject object, RepositoryObject other) {
                    }
                };
                HorizontalLayout buttons = new HorizontalLayout();
                buttons.addComponent(removeLinkButton);
                Map<String, Object> context = new HashMap<String, Object>();
                context.put("object", statefulTarget);
                for (UIExtensionFactory factory : m_factories) {
                    com.vaadin.ui.Component component = factory.create(context);
                    buttons.addComponent(component);
                }
                item.getItemProperty(ACTIONS).setValue(buttons);
            }
            private void change(StatefulGatewayObject statefulTarget) {
                Item item = getItem(statefulTarget.getID());
                item.getItemProperty(OBJECT_DESCRIPTION).setValue("TODO");
            }
            private void remove(StatefulGatewayObject statefulTarget) {
                removeItem(statefulTarget.getID());
            }
        };
    }

    private abstract class AssociationDropHandler implements DropHandler {
        private final Table m_left;
        private final Table m_right;

        public AssociationDropHandler(Table left, Table right) {
            m_left = left;
            m_right = right;
        }

        public void drop(DragAndDropEvent event) {
            Transferable transferable = event.getTransferable();
            TargetDetails targetDetails = event.getTargetDetails();
            System.out.println("F: " + transferable);
            if (transferable instanceof TableTransferable) {
                TableTransferable tt = (TableTransferable) transferable;
                Object fromItemId = tt.getItemId();
                System.out.println("FF: " + fromItemId);
                // get the active selection, but only if we drag from the same table
                
                Set<?> selection = m_associations.isActiveTable(tt.getSourceComponent()) ? m_associations.getActiveSelection() : null;
                
                System.out.println("T: " + targetDetails.getClass().getName());
                if (targetDetails instanceof AbstractSelectTargetDetails) {
                    AbstractSelectTargetDetails ttd = (AbstractSelectTargetDetails) targetDetails;
                    Object toItemId = ttd.getItemIdOver();
                    System.out.println("TT: " + toItemId);
                    if (tt.getSourceComponent().equals(m_left)) {
                        if (selection != null) {
                            for (Object item : selection) {
                                System.out.println("FS: " + item);
                                associateFromLeft((String) item, (String) toItemId);
                            }
                        }
                        else {
                            associateFromLeft((String) fromItemId, (String) toItemId);
                        }
                    }
                    else {
                        if (selection != null) {
                            for (Object item : selection) {
                                System.out.println("FS: " + item);
                                associateFromRight((String) toItemId, (String) item);
                            }
                        }
                        else {
                            associateFromRight((String) toItemId, (String) fromItemId);
                        }
                    }
                    // TODO add to highlighting (it's probably easiest to recalculate the whole
                    // set of related and associated items here, see SelectionListener, or to manually
                    // figure out the changes in all cases
                }
            }
        }

        public AcceptCriterion getAcceptCriterion() {
//            return AcceptAll.get();
            return new Or(VerticalLocationIs.MIDDLE);
        }

        protected abstract void associateFromLeft(String left, String right);
        protected abstract void associateFromRight(String left, String right);
    }

    private static void showEditWindow(String objectName, final NamedObject object, Window main) {
        final Window featureWindow = new Window();
        featureWindow.setModal(true);
        featureWindow.setCaption("Edit " + objectName);
        featureWindow.setWidth("15em");

        // Configure the windws layout; by default a VerticalLayout
        VerticalLayout layout = (VerticalLayout) featureWindow.getContent();
        layout.setMargin(true);
        layout.setSpacing(true);

        final TextField name = new TextField("name");
        final TextField description = new TextField("description");

        name.setValue(object.getName());
        description.setValue(object.getDescription());

        layout.addComponent(name);
        layout.addComponent(description);

        Button close = new Button("Ok", new Button.ClickListener() {
            // inline click-listener
            public void buttonClick(ClickEvent event) {
                // close the window by removing it from the parent window
                (featureWindow.getParent()).removeWindow(featureWindow);
                // create the feature
                object.setDescription((String) description.getValue());
            }
        });
        // The components added to the window are actually added to the window's
        // layout; you can use either. Alignments are set using the layout
        layout.addComponent(close);
        layout.setComponentAlignment(close, "right");

        if (featureWindow.getParent() != null) {
            // window is already showing
            main.getWindow().showNotification(
                    "Window is already open");
        } else {
            // Open the subwindow by adding it to the parent
            // window
            main.getWindow().addWindow(featureWindow);
        }
        name.setReadOnly(true);
        description.focus();
    }

    private Button createAddFeatureButton(final Window main) {
        Button button = new Button("Add Feature...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                new AddFeatureWindow(main).show();
            }
        });
        return button;
    }

    private Button createAddDistributionButton(final Window main) {
        Button button = new Button("Add Distribution...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                new AddDistributionWindow(main).show();
            }
        });
        return button;
    }

    private class AddFeatureWindow extends AbstractAddWindow {
        public AddFeatureWindow(Window main) {
            super(main, "Add Feature");
        }

        @Override
        protected void create(String name, String description) {
            createFeature(name, description);
        }

    }

    private class AddDistributionWindow extends AbstractAddWindow {
        public AddDistributionWindow(Window main) {
            super(main, "Add Distribution");
        }

        @Override
        protected void create(String name, String description) {
            createDistribution(name, description);
        }
    }
    
    private class AddTargetWindow extends AbstractAddWindow {
        public AddTargetWindow(Window main) {
            super(main, "Add Target");
        }

        @Override
        protected void create(String name, String description) {
            createTarget(name, description);
        }
    }

    private void createFeature(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(GroupObject.KEY_NAME, name);
        attributes.put(GroupObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_featureRepository.create(attributes, tags);
    }
    
    private void createTarget(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(StatefulGatewayObject.KEY_ID, name);
        Map<String, String> tags = new HashMap<String, String>();
        m_statefulTargetRepository.preregister(attributes, tags);
    }

    private ArtifactObject getArtifact(String name) {
        try {
            List<ArtifactObject> list = m_artifactRepository.get(m_context.createFilter("(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + name + ")"));
            if (list.size() == 1) {
                return list.get(0);
            }
        }
        catch (InvalidSyntaxException e) {
        }
        return null;
    }

    private GroupObject getFeature(String name) {
        try {
            List<GroupObject> list = m_featureRepository.get(m_context.createFilter("(" + GroupObject.KEY_NAME + "=" + name + ")"));
            if (list.size() == 1) {
                return list.get(0);
            }
        }
        catch (InvalidSyntaxException e) {
        }
        return null;
    }
    
    private LicenseObject getDistribution(String name) {
        try {
            List<LicenseObject> list = m_distributionRepository.get(m_context.createFilter("(" + LicenseObject.KEY_NAME + "=" + name + ")"));
            if (list.size() == 1) {
                return list.get(0);
            }
        }
        catch (InvalidSyntaxException e) {
        }
        return null;
    }
    
    private StatefulGatewayObject getTarget(String name) {
        System.out.println("getTarget(" + name + ")");
        try {
            List<StatefulGatewayObject> list = m_statefulTargetRepository.get(m_context.createFilter("(" + StatefulGatewayObject.KEY_ID + "=" + name + ")"));
            if (list.size() == 1) {
                System.out.println("getTarget returning " + list.get(0));
                return list.get(0);
            }
        }
        catch (InvalidSyntaxException e) {
        }
        System.out.println("getTarget returning NULL!!!!");
        return null;
    }

    private void deleteFeature(String name) {
        GroupObject feature = getFeature(name);
        if (feature != null) {
            m_featureRepository.remove(feature);
            // TODO cleanup links?
        }
    }

    private void createDistribution(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(LicenseObject.KEY_NAME, name);
        attributes.put(LicenseObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_distributionRepository.create(attributes, tags);
    }

    private void updateTableData() {
        m_artifactsPanel.populate();
        m_featuresPanel.populate();
        m_distributionsPanel.populate();
        m_targetsPanel.populate();
    }
    

    @Override
    public void close() {
        super.close();
        // when the session times out
        // TODO: clean up the ace client session?
    }


    public abstract class ObjectPanel extends Table implements EventHandler {
        public static final String ACTIONS = "actions";
        protected Table m_table = this;
        protected Associations m_associations;
        
        public ObjectPanel(Associations associations, final String name, final Window main, boolean hasEdit, boolean hasDeleteButton) {
            super(name + "s");
            m_associations = associations;
            addContainerProperty(OBJECT_NAME, String.class, null);
            addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
            addContainerProperty(ACTIONS, HorizontalLayout.class, null);
            setSizeFull();
            setCellStyleGenerator(m_associations.createCellStyleGenerator());
            setSelectable(true);
            setMultiSelect(true);
            setImmediate(true);
            setDragMode(TableDragMode.MULTIROW);
            if (hasEdit) {
                addListener(new ItemClickListener() {
                    public void itemClick(ItemClickEvent event) {
                        if (event.isDoubleClick()) {
                            String itemId = (String) event.getItemId();
                            RepositoryObject object = getFromId(itemId);
                            showEditWindow(name, m_associations.getNamedObject(object), main);
                        }
                    }
                });
            }
        }
        public abstract void populate();
        protected abstract RepositoryObject getFromId(String id);
    }

    private class AddArtifactWindow extends Window {
    	private File m_file;
    	private List<File> m_uploadedArtifacts = new ArrayList<File>();
    	
    	public AddArtifactWindow(final Window main) {
    		super();
            setModal(true);
            setCaption("Add artifact");
            setWidth("50em");
            
            // Configure the windws layout; by default a VerticalLayout
            VerticalLayout layout = (VerticalLayout) getContent();
            layout.setMargin(true);
            layout.setSpacing(true);

            final TextField search = new TextField("search");
            final Table artifacts = new ArtifactTable(main);
            final Table uploadedArtifacts = new ArtifactTable(main);
            final Upload uploadArtifact = new Upload("Upload Artifact", new Upload.Receiver() {
    			public OutputStream receiveUpload(String filename, String MIMEType) {
    				FileOutputStream fos = null; // Output stream to write to
    		        try {
    		        	m_file = new File(filename); //File.createTempFile(filename, "tmp");
    		        	if (m_file.exists()) {
    		        		throw new IOException("Uploaded file already exists.");
    		        		// meaning probably somebody else is in the process of uploading 
    		        		// the exact same file...
    		        	}
    		            // Open the file for writing.
    		            fos = new FileOutputStream(m_file);
    		        }
    		        catch (final IOException e) {
    		            // Error while opening the file. Not reported here.
    		            e.printStackTrace();
    		            return null;
    		        }
    		        return fos; // Return the output stream to write to			
    	        }
    		});
            
            artifacts.setCaption("Artifacts in repository");
            uploadedArtifacts.setCaption("Uploaded artifacts");
            uploadedArtifacts.setSelectable(false);
            
            search.setValue("");
            try {
                getBundles(artifacts);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            
            uploadArtifact.setImmediate(true);
            
            uploadArtifact.addListener(new Upload.SucceededListener() {
    			
    			public void uploadSucceeded(SucceededEvent event) {
    				System.out.println("upload succeeded: " + event.getFilename());
    				try {
    					URL artifact = m_file.toURI().toURL();
    		            Item item = uploadedArtifacts.addItem(artifact);
    		            item.getItemProperty("symbolic name").setValue(m_file.getName());
    		            item.getItemProperty("version").setValue("");
    					m_uploadedArtifacts.add(m_file);
					}
    				catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    				
    			}
    		});
            uploadArtifact.addListener(new Upload.FailedListener() {
    			
    			public void uploadFailed(FailedEvent event) {
    				System.out.println("upload failed: " + event);
    			}
    		});

            layout.addComponent(search);
            layout.addComponent(artifacts);
            layout.addComponent(uploadArtifact);
            layout.addComponent(uploadedArtifacts);

            Button close = new Button("Add", new Button.ClickListener() {
                // inline click-listener
                public void buttonClick(ClickEvent event) {
                    // close the window by removing it from the parent window
                    (AddArtifactWindow.this.getParent()).removeWindow(AddArtifactWindow.this);
                    List<ArtifactObject> added = new ArrayList<ArtifactObject>();
                    // TODO add the selected artifacts
                    for (Object id : artifacts.getItemIds()) {
                        if (artifacts.isSelected(id)) {
                            for (OBREntry e : m_obrList) {
                                if (e.getUri().equals(id)) {
                                    System.out.println("Importing " + e);
                                    try {
                                        ArtifactObject ao = importBundle(e);
                                        added.add(ao);
                                    }
                                    catch (Exception e1) {
                                        // TODO Auto-generated catch block
                                        e1.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                    for (File artifact : m_uploadedArtifacts) {
                    	try {
                    		ArtifactObject ao = importBundle(artifact.toURI().toURL());
                            added.add(ao);
						}
                    	catch (IOException e) {
							e.printStackTrace();
						}
                    	finally {
                    		artifact.delete();
                    	}
                    }
                    // TODO: make a decision here
                    // so now we have enough information to show a list of imported artifacts (added)
                    // but do we want to show this list or do we just assume the user will see the new
                    // artifacts in the left most column? do we also report failures? or only report
                    // if there were failures?
                }
            });
            // The components added to the window are actually added to the window's
            // layout; you can use either. Alignments are set using the layout
            layout.addComponent(close);
            layout.setComponentAlignment(close, "right");
            search.focus();
    	}
    }
    
    private void showAddArtifactDialog(final Window main) {
        final AddArtifactWindow featureWindow = new AddArtifactWindow(main);
        if (featureWindow.getParent() != null) {
            // window is already showing
            main.getWindow().showNotification("Window is already open");
        } else {
            // Open the subwindow by adding it to the parent
            // window
            main.getWindow().addWindow(featureWindow);
        }
    }
    
    public void getBundles(Table table) throws Exception {
        getBundles(table, "http://localhost:8080/obr/");
    }
    
    public void getBundles(Table table, String obrBaseUrl) throws Exception {
        URL obrBase = new URL(obrBaseUrl);
        
        // retrieve the repository.xml as a stream
        URL url = null;
        try {
            url = new URL(obrBase, "repository.xml");
        }
        catch (MalformedURLException e) {
            System.err.println("Error retrieving repository.xml from " + obrBase);
            throw e;
        }

        InputStream input = null;
        NodeList resources = null;
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false); //We always want the newest repository.xml file.
            input = connection.getInputStream();

            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                // this XPath expressing will find all 'resource' elements which have an attribute 'uri'.
                resources = (NodeList) xpath.evaluate("/repository/resource[@uri]", new InputSource(input), XPathConstants.NODESET);
            }
            catch (XPathExpressionException e) {
                System.err.println("Error evaluating XPath expression.");
                e.printStackTrace(System.err);
                throw e;
            }
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
            throw e;
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                }
                catch (IOException e) {
                    // too bad, no worries.
                }
            }
        }

        m_obrList = new ArrayList<OBREntry>();
        for (int nResource = 0; nResource < resources.getLength(); nResource++) {
            Node resource = resources.item(nResource);
            NamedNodeMap attr = resource.getAttributes();
            String uri = attr.getNamedItem("uri").getTextContent();
            String symbolicname = attr.getNamedItem("symbolicname").getTextContent();
            String version = attr.getNamedItem("version").getTextContent();
            m_obrList.add(new OBREntry(symbolicname, version, uri));
        }

        // Create a list of filenames from the ArtifactRepository
        List<OBREntry> fromRepository = new ArrayList<OBREntry>();
        List<ArtifactObject> artifactObjects = m_artifactRepository.get();
        artifactObjects.addAll(m_artifactRepository.getResourceProcessors());
        for (ArtifactObject ao : artifactObjects) {
            String artifactURL = ao.getURL();
            if (artifactURL.startsWith(obrBase.toExternalForm())) {
                // we now know this artifact comes from the OBR we are querying, so we are interested.
                fromRepository.add(new OBREntry(ao.getName(), null /* TODO version */, new File(artifactURL).getName()));
            }
        }

        // remove all urls we already know
        m_obrList.removeAll(fromRepository);
        if (m_obrList.isEmpty()) {
            System.err.println("No data in obr...");
            return;
        }

        // Create a list of all bundle names
        for (OBREntry s : m_obrList) {
            Item item = table.addItem(s.getUri());
            item.getItemProperty("symbolic name").setValue(s.getSymbolicName());
            item.getItemProperty("version").setValue(s.getVersion());
        }
    }

    public ArtifactObject importBundle(OBREntry bundle) throws IOException {
        return m_artifactRepository.importArtifact(new URL(new URL(obr), bundle.getUri()), false);
    }
    
    public ArtifactObject importBundle(URL artifact) throws IOException {
		return m_artifactRepository.importArtifact(artifact, true);
    }
}
