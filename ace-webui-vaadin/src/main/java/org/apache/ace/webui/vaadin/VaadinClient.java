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
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.repository.Artifact2FeatureAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.test.utils.FileUtils;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.LoginWindow.LoginFunction;
import org.apache.ace.webui.vaadin.component.ArtifactsPanel;
import org.apache.ace.webui.vaadin.component.DistributionsPanel;
import org.apache.ace.webui.vaadin.component.FeaturesPanel;
import org.apache.ace.webui.vaadin.component.MainActionToolbar;
import org.apache.ace.webui.vaadin.component.TargetsPanel;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.event.Transferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.TargetDetails;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.event.dd.acceptcriteria.Or;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.AbstractSelect.VerticalLocationIs;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressIndicator;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.TableTransferable;
import com.vaadin.ui.Window;

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
@SuppressWarnings("serial")
public class VaadinClient extends com.vaadin.Application implements AssociationRemover, LoginFunction {

    private static final long serialVersionUID = 1L;

    private static long SESSION_ID = 12345;

    private static String targetRepo = "target";
    private static String shopRepo = "shop";
    private static String deployRepo = "deployment";
    private static String customerName = "apache";
    private static String endpoint = "/repository";

    private volatile AuthenticationService m_authenticationService;
    private volatile DependencyManager m_manager;
    private volatile BundleContext m_context;
    private volatile SessionFactory m_sessionFactory;
    private volatile UserAdmin m_userAdmin;
    private volatile ArtifactRepository m_artifactRepository;
    private volatile FeatureRepository m_featureRepository;
    private volatile DistributionRepository m_distributionRepository;
    private volatile StatefulTargetRepository m_statefulTargetRepository;
    private volatile Artifact2FeatureAssociationRepository m_artifact2featureAssociationRepository;
    private volatile Feature2DistributionAssociationRepository m_feature2distributionAssociationRepository;
    private volatile Distribution2TargetAssociationRepository m_distribution2targetAssociationRepository;
    private volatile RepositoryAdmin m_admin;
    private volatile LogService m_log;

    private String m_sessionID;
    private ArtifactsPanel m_artifactsPanel;
    private FeaturesPanel m_featuresPanel;
    private DistributionsPanel m_distributionsPanel;
    private TargetsPanel m_targetsPanel;
    private GridLayout m_grid;
    private boolean m_dynamicRelations = true;
    private File m_sessionDir; // private folder for session info
    private HorizontalLayout m_artifactToolbar;
    private Button m_featureToolbar;
    private Button m_distributionToolbar;
    private Button m_targetToolbar;
    private Window m_mainWindow;

    private final URL m_aceHost;
    private final URL m_obrUrl;

    private final Associations m_associations = new Associations();
    private final AtomicBoolean m_dependenciesResolved = new AtomicBoolean(false);

    private ProgressIndicator m_progress;

    // basic session ID generator
    private static long generateSessionID() {
        return SESSION_ID++;
    }

    public VaadinClient(URL aceHost, URL obrUrl) {
        m_aceHost = aceHost;
        m_obrUrl = obrUrl;
    }

    public void setupDependencies(Component component) {
        m_sessionID = "" + generateSessionID();
        File dir = m_context.getDataFile(m_sessionID);
        dir.mkdir();
        m_sessionDir = dir;
        m_sessionFactory.createSession(m_sessionID);
        addDependency(component, RepositoryAdmin.class);
        addDependency(component, DistributionRepository.class);
        addDependency(component, ArtifactRepository.class);
        addDependency(component, FeatureRepository.class);
        addDependency(component, Artifact2FeatureAssociationRepository.class);
        addDependency(component, Feature2DistributionAssociationRepository.class);
        addDependency(component, Distribution2TargetAssociationRepository.class);
        addDependency(component, StatefulTargetRepository.class);
    }

    // @formatter:off
    private void addDependency(Component component, Class service) {
        component.add(m_manager.createServiceDependency()
            .setService(service, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(true)
            .setInstanceBound(true));
    }

    // @formatter:on

    public void start() {
        m_log.log(LogService.LOG_INFO, "Starting session #" + m_sessionID);
        m_dependenciesResolved.set(true);
    }

    public void stop() {
        m_log.log(LogService.LOG_INFO, "Stopping session #" + m_sessionID);
        m_dependenciesResolved.set(false);
    }

    public void destroyDependencies() {
        m_sessionFactory.destroySession(m_sessionID);
        FileUtils.removeDirectoryWithContent(m_sessionDir);
    }

    public void init() {
        setTheme("ace");

        if (!m_dependenciesResolved.get()) {
            final Window message = new Window("Apache ACE");
            message.getContent().setSizeFull();
            setMainWindow(message);

            Label richText =
                new Label(
                    "<h1>Apache ACE User Interface</h1>"
                        + "<p>Due to missing component dependencies on the server, probably due to misconfiguration, "
                        + "the user interface cannot be properly started. Please contact your server administrator. "
                        + "You can retry accessing the user interface by <a href=\"?restartApplication\">following this link</a>.</p>");
            richText.setContentMode(Label.CONTENT_XHTML);

            // TODO we might want to add some more details here as to what's
            // missing on the other hand, the user probably can't fix that anyway
            message.addComponent(richText);
            return;
        }

        m_mainWindow = new Window("Apache ACE");
        m_mainWindow.getContent().setSizeFull();

        setMainWindow(m_mainWindow);

        showLoginWindow();
    }

    /**
     * Shows the login window on the center of the main window.
     */
    private void showLoginWindow() {
        LoginWindow loginWindow = new LoginWindow(m_log, this);
        
        m_mainWindow.addWindow(loginWindow);
        
        loginWindow.center();
    }

    private void initGrid(User user) {
        Authorization auth = m_userAdmin.getAuthorization(user);
        int count = 0;
        for (String role : new String[] { "viewArtifact", "viewFeature", "viewDistribution", "viewTarget" }) {
            if (auth.hasRole(role)) {
                count++;
            }
        }
        m_grid = new GridLayout(count, 4);
        m_grid.setSpacing(true);
        m_grid.setSizeFull();

        m_grid.addComponent(createToolbar(), 0, 0, count - 1, 0);

        m_artifactsPanel = createArtifactsPanel();

        m_artifactToolbar = new HorizontalLayout();
        m_artifactToolbar.addComponent(createAddArtifactButton());

        CheckBox dynamicCheckBox = new CheckBox("Dynamic Links");
        dynamicCheckBox.setImmediate(true);
        dynamicCheckBox.setValue(Boolean.TRUE);
        dynamicCheckBox.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                m_dynamicRelations = event.getButton().booleanValue();
            }
        });
        m_artifactToolbar.addComponent(dynamicCheckBox);

        count = 0;
        if (auth.hasRole("viewArtifact")) {
            m_grid.addComponent(m_artifactsPanel, count, 2);
            m_grid.addComponent(m_artifactToolbar, count, 1);
            count++;
        }

        m_featuresPanel = createFeaturesPanel();
        m_featureToolbar = createAddFeatureButton();

        if (auth.hasRole("viewFeature")) {
            m_grid.addComponent(m_featuresPanel, count, 2);
            m_grid.addComponent(m_featureToolbar, count, 1);
            count++;
        }

        m_distributionsPanel = createDistributionsPanel();
        m_distributionToolbar = createAddDistributionButton();

        if (auth.hasRole("viewDistribution")) {
            m_grid.addComponent(m_distributionsPanel, count, 2);
            m_grid.addComponent(m_distributionToolbar, count, 1);
            count++;
        }

        m_targetsPanel = createTargetsPanel();
        m_targetToolbar = createAddTargetButton();

        if (auth.hasRole("viewTarget")) {
            m_grid.addComponent(m_targetsPanel, count, 2);
            m_grid.addComponent(m_targetToolbar, count, 1);
        }

        // Wire up all panels so they have the correct associations...
        m_artifactsPanel.setLeftTable(null);
        m_artifactsPanel.setRightTable(m_featuresPanel);

        m_featuresPanel.setLeftTable(m_artifactsPanel);
        m_featuresPanel.setRightTable(m_distributionsPanel);

        m_distributionsPanel.setLeftTable(m_featuresPanel);
        m_distributionsPanel.setRightTable(m_targetsPanel);

        m_targetsPanel.setLeftTable(m_distributionsPanel);
        m_targetsPanel.setRightTable(null);

        m_grid.setRowExpandRatio(2, 1.0f);

        m_progress = new ProgressIndicator(0f);
        m_progress.setStyleName("invisible");
        m_progress.setPollingInterval(500);

        m_grid.addComponent(m_progress, 0, 3);

        m_artifactsPanel.addListener(m_associations.createSelectionListener(m_artifactsPanel, m_artifactRepository,
            new Class[] {}, new Class[] { FeatureObject.class, DistributionObject.class, TargetObject.class },
            new Table[] { m_featuresPanel, m_distributionsPanel, m_targetsPanel }));
        m_featuresPanel.addListener(m_associations.createSelectionListener(m_featuresPanel, m_featureRepository,
            new Class[] { ArtifactObject.class }, new Class[] { DistributionObject.class, TargetObject.class },
            new Table[] { m_artifactsPanel, m_distributionsPanel, m_targetsPanel }));
        m_distributionsPanel.addListener(m_associations.createSelectionListener(m_distributionsPanel,
            m_distributionRepository,
            new Class[] { FeatureObject.class, ArtifactObject.class }, new Class[] { TargetObject.class },
            new Table[] { m_artifactsPanel, m_featuresPanel, m_targetsPanel }));
        m_targetsPanel.addListener(m_associations.createSelectionListener(m_targetsPanel, m_statefulTargetRepository,
            new Class[] { DistributionObject.class, FeatureObject.class, ArtifactObject.class }, new Class[] {},
            new Table[] { m_artifactsPanel, m_featuresPanel, m_distributionsPanel }));

        m_artifactsPanel.setDropHandler(new AssociationDropHandler((Table) null, m_featuresPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
            }

            @Override
            protected void associateFromRight(String left, String right) {
                ArtifactObject artifact = getArtifact(left);
                // if you drop on a resource processor, and try to get it, you
                // will get null because you cannot associate anything with a 
                // resource processor so we check for null here
                if (artifact != null) {
                    if (m_dynamicRelations) {
                        Map<String, String> properties = new HashMap<String, String>();
                        properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
                        m_artifact2featureAssociationRepository.create(artifact, properties, getFeature(right), null);
                    }
                    else {
                        m_artifact2featureAssociationRepository.create(artifact, getFeature(right));
                    }
                }
            }
        });
        m_featuresPanel.setDropHandler(new AssociationDropHandler(m_artifactsPanel, m_distributionsPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
                ArtifactObject artifact = getArtifact(left);
                // if you drop on a resource processor, and try to get it, you
                // will get null because you cannot associate anything with a 
                // resource processor so we check for null here
                if (artifact != null) {
                    if (m_dynamicRelations) {
                        Map<String, String> properties = new HashMap<String, String>();
                        properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
                        m_artifact2featureAssociationRepository.create(artifact, properties, getFeature(right), null);
                    }
                    else {
                        m_artifact2featureAssociationRepository.create(artifact, getFeature(right));
                    }
                }
            }

            @Override
            protected void associateFromRight(String left, String right) {
                m_feature2distributionAssociationRepository.create(getFeature(left), getDistribution(right));
            }
        });
        m_distributionsPanel.setDropHandler(new AssociationDropHandler(m_featuresPanel, m_targetsPanel) {
            @Override
            protected void associateFromLeft(String left, String right) {
                m_feature2distributionAssociationRepository.create(getFeature(left), getDistribution(right));
            }

            @Override
            protected void associateFromRight(String left, String right) {
                StatefulTargetObject target = getTarget(right);
                if (!target.isRegistered()) {
                    target.register();
                    target.setAutoApprove(true);
                }
                m_distribution2targetAssociationRepository.create(getDistribution(left), target.getTargetObject());
            }
        });
        m_targetsPanel.setDropHandler(new AssociationDropHandler(m_distributionsPanel, (Table) null) {
            @Override
            protected void associateFromLeft(String left, String right) {
                StatefulTargetObject target = getTarget(right);
                if (!target.isRegistered()) {
                    target.register();
                    target.setAutoApprove(true);
                }
                m_distribution2targetAssociationRepository.create(getDistribution(left), target.getTargetObject());
            }

            @Override
            protected void associateFromRight(String left, String right) {
            }
        });

        addListener(m_artifactsPanel, ArtifactObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED);
        addListener(m_featuresPanel, FeatureObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED);
        addListener(m_distributionsPanel, DistributionObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED);
        addListener(m_targetsPanel, StatefulTargetObject.TOPIC_ALL, TargetObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED);

        m_mainWindow.addComponent(m_grid);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAssociation(Artifact2FeatureAssociation association) {
        m_artifact2featureAssociationRepository.remove(association);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAssociation(Distribution2TargetAssociation association) {
        m_distribution2targetAssociationRepository.remove(association);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAssociation(Feature2DistributionAssociation association) {
        m_feature2distributionAssociationRepository.remove(association);
    }

    /**
     * {@inheritDoc}
     */
    public boolean login(String username, String password) {
        try {
            User user = m_authenticationService.authenticate(username, password);
            if (user == null) {
                return false;
            }

            RepositoryAdminLoginContext context = m_admin.createLoginContext(user);

            // @formatter:off
            context.addShopRepository(new URL(m_aceHost, endpoint), customerName, shopRepo, true)
                .setObrBase(m_obrUrl)
                .addTargetRepository(new URL(m_aceHost, endpoint), customerName, targetRepo, true)
                .addDeploymentRepository(new URL(m_aceHost, endpoint), customerName, deployRepo, true);
            // @formatter:on
            
            m_admin.login(context);
            initGrid(user);
            m_admin.checkout();
            
            return true;
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_WARNING, "Login failed!", e);
            return false;
        }
    }

    private void addListener(final Object implementation, final String... topics) {
        Properties props = new Properties();
        props.put(EventConstants.EVENT_TOPIC, topics);
        props.put(EventConstants.EVENT_FILTER, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")");
        // @formatter:off
        m_manager.add(
            m_manager.createComponent()
                .setInterface(EventHandler.class.getName(), props)
                .setImplementation(implementation));
        // @formatter:on
    }

    private GridLayout createToolbar() {
        MainActionToolbar mainActionToolbar = new MainActionToolbar() {
            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }

            @Override
            protected void doAfterRevert() throws IOException {
                updateTableData();
            }

            @Override
            protected void doAfterRetrieve() throws IOException {
                updateTableData();
            }

            @Override
            protected void doAfterCommit() throws IOException {
                updateTableData();
            }

            @Override
            protected void doAfterLogout() throws IOException {
                // Close the application and reload the main window...
                close();
            }

            private void updateTableData() {
                m_artifactsPanel.populate();
                m_featuresPanel.populate();
                m_distributionsPanel.populate();
                m_targetsPanel.populate();
            }
        };
        addListener(mainActionToolbar, RepositoryObject.PUBLIC_TOPIC_ROOT.concat(RepositoryObject.TOPIC_ALL_SUFFIX));
        return mainActionToolbar;
    }

    private ArtifactsPanel createArtifactsPanel() {
        return new ArtifactsPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Artifact", object, extensions) {
                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
                    }
                    
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit artifact!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }
                };
            }
            
            @Override
            protected ArtifactRepository getRepository() {
                return m_artifactRepository;
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }
        };
    }

    private FeaturesPanel createFeaturesPanel() {
        return new FeaturesPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Feature", object, extensions) {
                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
                    }
                    
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit feature!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }
                };
            }
            
            @Override
            protected FeatureRepository getRepository() {
                return m_featureRepository;
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }
        };
    }

    private DistributionsPanel createDistributionsPanel() {
        return new DistributionsPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Distribution", object, extensions) {
                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
                    }
                    
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit distribution!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }
                };
            }
            
            @Override
            protected DistributionRepository getRepository() {
                return m_distributionRepository;
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }
        };
    }

    private TargetsPanel createTargetsPanel() {
        return new TargetsPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Target", object, extensions) {
                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        // Nothing to edit!
                    }
                    
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit target!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void initDialog(NamedObject object, List<UIExtensionFactory> factories) {
                        m_name.setCaption("Identifier");
                        m_name.setReadOnly(true);
                        m_description.setVisible(false);
                        
                        super.initDialog(object, factories);
                    }
                };
            }

            @Override
            protected StatefulTargetRepository getRepository() {
                return m_statefulTargetRepository;
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
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
            if (transferable instanceof TableTransferable) {
                TableTransferable tt = (TableTransferable) transferable;
                Object fromItemId = tt.getItemId();
                // get the active selection, but only if we drag from the same table
                Set<?> selection =
                    m_associations.isActiveTable(tt.getSourceComponent()) ? m_associations.getActiveSelection() : null;
                if (targetDetails instanceof AbstractSelectTargetDetails) {
                    AbstractSelectTargetDetails ttd = (AbstractSelectTargetDetails) targetDetails;
                    Object toItemId = ttd.getItemIdOver();
                    if (tt.getSourceComponent().equals(m_left)) {
                        if (selection != null) {
                            for (Object item : selection) {
                                associateFromLeft((String) item, (String) toItemId);
                            }
                        }
                        else {
                            associateFromLeft((String) fromItemId, (String) toItemId);
                        }
                    }
                    else if (tt.getSourceComponent().equals(m_right)) {
                        if (selection != null) {
                            for (Object item : selection) {
                                associateFromRight((String) toItemId, (String) item);
                            }
                        }
                        else {
                            associateFromRight((String) toItemId, (String) fromItemId);
                        }
                    }
                    // TODO add to highlighting (it's probably easiest to
                    // recalculate the whole set of related and associated
                    // items here, see SelectionListener, or to manually figure
                    // out the changes in all cases
                }
            }
        }

        public AcceptCriterion getAcceptCriterion() {
            return new Or(VerticalLocationIs.MIDDLE);
        }

        protected abstract void associateFromLeft(String left, String right);

        protected abstract void associateFromRight(String left, String right);
    }

    /**
     * Create a button to show a pop window for adding new features.
     * 
     * @param main Main Window
     * @return Button
     */
    private Button createAddArtifactButton() {
        Button button = new Button("Add artifact...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                showAddArtifactDialog();
            }
        });
        return button;
    }

    /***
     * Create a button to show popup window for adding a new feature. On success
     * this calls the createFeature() method.
     * 
     * @return the add-feature button instance.
     */
    private Button createAddFeatureButton() {
        Button button = new Button("Add Feature...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Feature") {
                    public void onOk(String name, String description) {
                        createFeature(name, description);
                    }

                    public void handleError(Exception e) {
                        // ACE-241: notify user when the feature-creation failed!
                        getWindow().showNotification("Failed to add new feature!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }
                };
                window.show(getMainWindow());
            }
        });
        return button;
    }

    /**
     * Create a button to show a popup window for adding a new distribution. On
     * success this calls the createDistribution() method.
     * 
     * @return the add-distribution button instance.
     */
    private Button createAddDistributionButton() {
        Button button = new Button("Add Distribution...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Distribution") {
                    public void onOk(String name, String description) {
                        createDistribution(name, description);
                    }

                    public void handleError(Exception e) {
                        // ACE-241: notify user when the distribution-creation failed!
                        getWindow().showNotification("Failed to add new distribution!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }
                };
                window.show(getMainWindow());
            }
        });

        return button;
    }

    /**
     * Create a button to show a popup window for adding a new target. On
     * success this calls the createTarget() method
     * 
     * @return the add-target button instance.
     */
    private Button createAddTargetButton() {
        Button button = new Button("Add target...");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Target") {
                    protected void onOk(String id, String description) {
                        createTarget(id);
                    }

                    protected void handleError(Exception e) {
                        // ACE-241: notify user when the target-creation failed!
                        getWindow().showNotification("Failed to add new target!", "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void initDialog() {
                        m_name.setCaption("Identifier");
                        m_description.setVisible(false);

                        super.initDialog();
                    }
                };
                window.show(getMainWindow());
            }
        });
        return button;
    }

    /**
     * Create a new feature in the feature repository.
     * 
     * @param name the name of the new feature;
     * @param description the description of the new feature.
     */
    private void createFeature(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FeatureObject.KEY_NAME, name);
        attributes.put(FeatureObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_featureRepository.create(attributes, tags);
    }

    /**
     * Create a new target in the stateful target repository.
     * 
     * @param name the name of the new target;
     */
    private void createTarget(String name) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(StatefulTargetObject.KEY_ID, name);
        attributes.put(TargetObject.KEY_AUTO_APPROVE, "true");
        Map<String, String> tags = new HashMap<String, String>();
        m_statefulTargetRepository.preregister(attributes, tags);
    }

    /**
     * Create a new distribution in the distribution repository
     * 
     * @param name the name of the new distribution;
     * @param description the description of the new distribution.
     */
    private void createDistribution(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(DistributionObject.KEY_NAME, name);
        attributes.put(DistributionObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_distributionRepository.create(attributes, tags);
    }

    private ArtifactObject getArtifact(String definition) {
        return m_artifactRepository.get(definition);
    }

    private FeatureObject getFeature(String name) {
        return m_featureRepository.get(name);
    }

    private DistributionObject getDistribution(String name) {
        return m_distributionRepository.get(name);
    }

    private StatefulTargetObject getTarget(String name) {
        return m_statefulTargetRepository.get(name);
    }

    @Override
    public void close() {
        super.close();
        // when the session times out
        // TODO: clean up the ace client session?
    }

    private void showAddArtifactDialog() {
        final AddArtifactWindow window = new AddArtifactWindow(m_sessionDir, m_obrUrl) {
            @Override
            protected ArtifactRepository getArtifactRepository() {
                return m_artifactRepository;
            }

            @Override
            protected LogService getLogger() {
                return m_log;
            }
        };

        // Open the subwindow by adding it to the parent window
        getMainWindow().addWindow(window);
    }
}
