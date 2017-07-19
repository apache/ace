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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
import org.apache.ace.client.repository.repository.ArtifactRepository.ArtifactAlreadyExistsException;
import org.apache.ace.client.repository.repository.Distribution2TargetAssociationRepository;
import org.apache.ace.client.repository.repository.DistributionRepository;
import org.apache.ace.client.repository.repository.Feature2DistributionAssociationRepository;
import org.apache.ace.client.repository.repository.FeatureRepository;
import org.apache.ace.client.repository.repository.TargetRepository;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetRepository;
import org.apache.ace.connectionfactory.ConnectionFactory;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.domain.NamedTargetObject;
import org.apache.ace.webui.vaadin.LoginWindow.LoginFunction;
import org.apache.ace.webui.vaadin.UploadHelper.ArtifactDropHandler;
import org.apache.ace.webui.vaadin.UploadHelper.GenericUploadHandler;
import org.apache.ace.webui.vaadin.UploadHelper.UploadHandle;
import org.apache.ace.webui.vaadin.component.ArtifactsPanel;
import org.apache.ace.webui.vaadin.component.AssociationHelper;
import org.apache.ace.webui.vaadin.component.DistributionsPanel;
import org.apache.ace.webui.vaadin.component.FeaturesPanel;
import org.apache.ace.webui.vaadin.component.MainActionToolbar;
import org.apache.ace.webui.vaadin.component.StatusLine;
import org.apache.ace.webui.vaadin.component.TargetsPanel;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.event.ShortcutAction.KeyCode;
import com.vaadin.event.ShortcutAction.ModifierKey;
import com.vaadin.service.ApplicationContext;
import com.vaadin.terminal.gwt.server.WebApplicationContext;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.DragAndDropWrapper.DragStartMode;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressIndicator;
import com.vaadin.ui.Window;
import com.vaadin.ui.Window.Notification;

/**
 * Main application entry point.
 */
public class VaadinClient extends com.vaadin.Application implements AssociationManager, LoginFunction {

    // basic session ID generator
    private static long generateSessionID() {
        return SESSION_ID.getAndIncrement();
    }

    /**
     * Remove the given directory and all it's files and subdirectories
     *
     * @param directory
     *            the name of the directory to remove
     */
    private static void removeDirectoryWithContent(File directory) {
        if ((directory == null) || !directory.exists()) {
            return;
        }
        File[] filesAndSubDirs = directory.listFiles();
        for (int i = 0; i < filesAndSubDirs.length; i++) {
            File file = filesAndSubDirs[i];
            if (file.isDirectory()) {
                removeDirectoryWithContent(file);
            }
            // else just remove the file
            file.delete();
        }
        directory.delete();
    }

    private static final long serialVersionUID = 1L;
    private static final AtomicLong SESSION_ID = new AtomicLong(1L);
    private static final String targetRepo = "target";
    private static final String shopRepo = "shop";

    private static final String deployRepo = "deployment";

    private static final String customerName = "apache";

    private static final String endpoint = "/repository";

    private volatile AuthenticationService m_authenticationService;
    private volatile BundleContext m_context;
    private volatile SessionFactory m_sessionFactory;
    private volatile UserAdmin m_userAdmin;
    private volatile ArtifactRepository m_artifactRepository;
    private volatile FeatureRepository m_featureRepository;
    private volatile DistributionRepository m_distributionRepository;
    private volatile StatefulTargetRepository m_statefulTargetRepository;
    private volatile TargetRepository m_targetRepository;
    private volatile Artifact2FeatureAssociationRepository m_artifact2featureAssociationRepository;
    private volatile Feature2DistributionAssociationRepository m_feature2distributionAssociationRepository;
    private volatile Distribution2TargetAssociationRepository m_distribution2targetAssociationRepository;

    private volatile RepositoryAdmin m_admin;
    private volatile LogService m_log;
    private volatile ConnectionFactory m_connectionFactory;

    private String m_sessionID;

    private ArtifactsPanel m_artifactsPanel;
    private FeaturesPanel m_featuresPanel;
    private DistributionsPanel m_distributionsPanel;
    private TargetsPanel m_targetsPanel;
    private GridLayout m_grid;
    private StatusLine m_statusLine;
    private File m_sessionDir; // private folder for session info
    private HorizontalLayout m_artifactToolbar;
    private HorizontalLayout m_featureToolbar;
    private HorizontalLayout m_distributionToolbar;
    private HorizontalLayout m_targetToolbar;
    private Window m_mainWindow;
    private final URL m_obrUrl;
    private final String m_repositoryXML;

    private final URL m_repository;
    private final boolean m_useAuth;
    private final String m_userName;
    private final AssociationHelper m_associations = new AssociationHelper();
    private final AtomicBoolean m_dependenciesResolved = new AtomicBoolean(false);
    // for the artifacts list...
    private final double m_cacheRate;
    private final int m_pageLength;

    private ProgressIndicator m_progress;

    private DependencyManager m_manager;

    private Component m_component;

    private final List<Component> m_eventHandlers = new ArrayList<>();

    private GridLayout m_mainToolbar;

    /**
     * Creates a new {@link VaadinClient} instance.
     *
     * @param m_manager2
     *
     * @param aceHost
     *            the hostname where the management service can be reached;
     * @param obrUrl
     *            the URL of the OBR to use;
     * @param useAuth
     *            <code>true</code> to use authentication, <code>false</code> to disable authentication;
     * @param userName
     *            the hardcoded username to use when authentication is disabled.
     */
    public VaadinClient(DependencyManager manager, URL aceHost, URL obrUrl, String repositoryXML, boolean useAuth, String userName, String password, double cacheRate, int pageLength) {
        m_manager = manager;
        try {
            m_repository = new URL(aceHost, endpoint);
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException("Need a valid repository URL!", e);
        }
        m_obrUrl = obrUrl;
        m_repositoryXML = repositoryXML;
        m_useAuth = useAuth;
        m_userName = userName;
        m_cacheRate = cacheRate;
        m_pageLength = pageLength;

        if (!m_useAuth && (m_userName == null || "".equals(m_userName.trim()))) {
            throw new IllegalArgumentException("Need a valid user name when no authentication is used!");
        }
    }

    @Override
    public void close() {
        if (isRunning()) {
            m_admin.deleteLocal();
            cleanupListeners();
            m_manager.remove(m_component);
            super.close();
        }
    }

    @Override
    public Artifact2FeatureAssociation createArtifact2FeatureAssociation(String artifactId, String featureId) {
        boolean dynamicRelation = false;

        FeatureObject feature = m_featureRepository.get(featureId);
        ArtifactObject artifact = m_artifactRepository.get(artifactId);
        if (artifact == null) {
            // Maybe a BSN?
            try {
                List<ArtifactObject> artifacts = m_artifactRepository.get(FrameworkUtil.createFilter(String.format("(%s=%s)", Constants.BUNDLE_SYMBOLICNAME, artifactId)));
                if (artifacts != null && artifacts.size() > 0) {
                    dynamicRelation = true;
                    // we only need this artifact for creating the association, so it does not matter which one we
                    // take...
                    artifact = artifacts.get(0);
                }
            }
            catch (InvalidSyntaxException exception) {
                m_log.log(LogService.LOG_ERROR, "Invalid filter syntax?!", exception);
            }
        }

        // Make sure we didn't drop on a resource processor bundle...
        if (artifact != null && artifact.getAttribute(BundleHelper.KEY_RESOURCE_PROCESSOR_PID) != null) {
            // if you drop on a resource processor, and try to get it, you
            // will get null because you cannot associate anything with a
            // resource processor so we check for null here
            return null;
        }

        Artifact2FeatureAssociation result = null;
        if (artifact != null) {
            if (dynamicRelation) {
                Map<String, String> properties = Collections.singletonMap(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
                result = m_artifact2featureAssociationRepository.create(artifact, properties, feature, null);
            }
            else {
                result = m_artifact2featureAssociationRepository.create(artifact, feature);
            }
        }
        return result;
    }

    @Override
    public Distribution2TargetAssociation createDistribution2TargetAssociation(String distributionId, String targetId) {
        DistributionObject distribution = m_distributionRepository.get(distributionId);
        StatefulTargetObject target = m_statefulTargetRepository.get(targetId);
        if (!target.isRegistered()) {
            target.register();
            target.setAutoApprove(true);
        }
        return m_distribution2targetAssociationRepository.create(distribution, target.getTargetObject());
    }

    @Override
    public Feature2DistributionAssociation createFeature2DistributionAssociation(String featureId, String distributionId) {
        FeatureObject feature = m_featureRepository.get(featureId);
        DistributionObject distribution = m_distributionRepository.get(distributionId);
        return m_feature2distributionAssociationRepository.create(feature, distribution);
    }

    public void destroyDependencies() {
        m_sessionFactory.destroySession(m_sessionID);
        removeDirectoryWithContent(m_sessionDir);
    }

    public void init() {
        setTheme("ace");

        if (!m_dependenciesResolved.get()) {
            final Window message = new Window("Apache ACE");
            message.getContent().setSizeFull();
            setMainWindow(message);

            Label richText = new Label("<h1>Apache ACE User Interface</h1>"
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
        m_mainWindow.setBorder(Window.BORDER_NONE);

        setMainWindow(m_mainWindow);

        // Authenticate the user either by showing a login window; or by another means...
        authenticate();
    }

    /**
     * {@inheritDoc}
     */
    public boolean login(String username, String password) {
        setUser(m_authenticationService.authenticate(username, password));
        return doLogin();
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

    public void setupDependencies(Component component) {
        m_sessionID = "web-" + generateSessionID();
        File dir = m_context.getDataFile(m_sessionID);
        dir.mkdir();
        m_sessionDir = dir.getAbsoluteFile();
        m_sessionFactory.createSession(m_sessionID, null);
        addSessionDependency(component, RepositoryAdmin.class);
        addSessionDependency(component, DistributionRepository.class);
        addSessionDependency(component, ArtifactRepository.class);
        addSessionDependency(component, FeatureRepository.class);
        addSessionDependency(component, Artifact2FeatureAssociationRepository.class);
        addSessionDependency(component, Feature2DistributionAssociationRepository.class);
        addSessionDependency(component, Distribution2TargetAssociationRepository.class);
        addSessionDependency(component, TargetRepository.class);
        addSessionDependency(component, StatefulTargetRepository.class);
        addDependency(component, ConnectionFactory.class);
    }

    public void start() {
        m_log.log(LogService.LOG_INFO, "Starting session #" + m_sessionID);
        m_dependenciesResolved.set(true);
    }

    @Override
    public void start(URL applicationUrl, Properties applicationProperties, ApplicationContext context) {
        m_component = m_manager.createComponent()
            .setImplementation(this)
            .setCallbacks("setupDependencies", "start", "stop", "destroyDependencies")
            .add(m_manager.createServiceDependency()
                .setService(SessionFactory.class)
                .setRequired(true)
            )
            .add(m_manager.createServiceDependency()
                .setService(UserAdmin.class)
                .setRequired(true)
            )
            .add(m_manager.createServiceDependency()
                .setService(AuthenticationService.class)
                .setRequired(m_useAuth)
            )
            .add(m_manager.createServiceDependency()
                .setService(LogService.class)
                .setRequired(false)
            );
        m_manager.add(m_component);
        super.start(applicationUrl, applicationProperties, context);
    }

    public void stop() throws Exception {
        m_log.log(LogService.LOG_INFO, "Stopping session #" + m_sessionID);

        try {
            close();

            try {
                m_admin.logout(true /* force */);
            }
            catch (IllegalStateException exception) {
                // Ignore, we're already logged out...
            }
        }
        finally {
            m_dependenciesResolved.set(false);
        }
    }

    final void showAddArtifactDialog() {
        final AddArtifactWindow window = new AddArtifactWindow(m_sessionDir, m_obrUrl, m_repositoryXML) {
            @Override
            protected ArtifactRepository getArtifactRepository() {
                return m_artifactRepository;
            }

            @Override
            protected ConnectionFactory getConnectionFactory() {
                return m_connectionFactory;
            }

            @Override
            protected LogService getLogger() {
                return m_log;
            }
        };

        // Open the subwindow by adding it to the parent window
        window.showWindow(getMainWindow());
    }

    final void showManageResourceProcessorsDialog() {
        ManageResourceProcessorWindow window = new ManageResourceProcessorWindow() {
            @Override
            protected ArtifactRepository getArtifactRepository() {
                return m_artifactRepository;
            }
        };
        // Open the subwindow by adding it to the parent window
        window.showWindow(getMainWindow());
    }

    /**
     * Create a new distribution in the distribution repository
     *
     * @param name
     *            the name of the new distribution;
     * @param description
     *            the description of the new distribution.
     */
    protected DistributionObject createDistribution(String name, String description) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(DistributionObject.KEY_NAME, name);
        attributes.put(DistributionObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<>();
        return m_distributionRepository.create(attributes, tags);
    }

    /**
     * Create a new feature in the feature repository.
     *
     * @param name
     *            the name of the new feature;
     * @param description
     *            the description of the new feature.
     */
    protected FeatureObject createFeature(String name, String description) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(FeatureObject.KEY_NAME, name);
        attributes.put(FeatureObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<>();
        return m_featureRepository.create(attributes, tags);
    }

    /**
     * Create a new target in the stateful target repository.
     *
     * @param name
     *            the name of the new target;
     */
    protected StatefulTargetObject createTarget(String name) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(StatefulTargetObject.KEY_ID, name);
        attributes.put(TargetObject.KEY_AUTO_APPROVE, "true");
        Map<String, String> tags = new HashMap<>();
        return m_statefulTargetRepository.preregister(attributes, tags);
    }

    private void addCrossPlatformAddShortcut(Button button, int keycode, String description) {
        // ACE-427 - NPE when using getMainWindow() if no authentication is used...
        WebApplicationContext context = (WebApplicationContext) getContext();
        ShortcutHelper.addCrossPlatformShortcut(context.getBrowser(), button, description, keycode, ModifierKey.SHIFT);
    }

    private void addDependency(Component component, Class<?> service) {
        component.add(m_manager.createServiceDependency()
            .setService(service)
            .setRequired(true)
        );
    }

    private void addListener(final Object implementation, final String... topics) {
        Properties props = new Properties();
        props.put(EventConstants.EVENT_TOPIC, topics);
        props.put(EventConstants.EVENT_FILTER, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")");
        Component component = m_manager.createComponent()
            .setInterface(EventHandler.class.getName(), props)
            .setImplementation(implementation);
        synchronized (m_eventHandlers) {
            m_eventHandlers.add(component);
        }
        m_manager.add(component);
    }

    private void addSessionDependency(Component component, Class<?> service) {
        component.add(m_manager.createServiceDependency()
            .setService(service, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(true)
        );
    }

    /**
     * Determines how authentication should take place.
     */
    private void authenticate() {
        if (m_useAuth) {
            showLoginWindow();
        }
        else {
            // Not using authentication; use fallback scenario...
            loginAutomatically();
        }
    }

    private void cleanupListeners() {
        Component[] components;
        synchronized (m_eventHandlers) {
            components = m_eventHandlers.toArray(new Component[m_eventHandlers.size()]);
            m_eventHandlers.clear();
        }
        for (Component component : components) {
            m_manager.remove(component);
        }
    }

    /**
     * Create a button to show a pop window for adding new features.
     *
     * @param user
     *
     * @param main
     *            Main Window
     * @return Button
     */
    private Button createAddArtifactButton() {
        Button button = new Button("+");
        addCrossPlatformAddShortcut(button, KeyCode.A, "Add a new artifact");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                showAddArtifactDialog();
            }
        });
        return button;
    }

    /**
     * Create a button to show a popup window for adding a new distribution. On success this calls the
     * createDistribution() method.
     *
     * @param user
     *
     * @return the add-distribution button instance.
     */
    private Button createAddDistributionButton() {
        Button button = new Button("+");
        addCrossPlatformAddShortcut(button, KeyCode.D, "Add a new distribution");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Distribution") {
                    public void handleError(Exception e) {
                        // ACE-241: notify user when the distribution-creation failed!
                        getWindow().showNotification("Failed to add new distribution!",
                            "<br/>Reason: " + e.getMessage(), Notification.TYPE_ERROR_MESSAGE);
                    }

                    public void onOk(String name, String description) {
                        createDistribution(name, description);
                    }
                };
                window.show(getMainWindow());
            }
        });

        return button;
    }

    /***
     * Create a button to show popup window for adding a new feature. On success this calls the createFeature() method.
     *
     * @param user
     *
     * @return the add-feature button instance.
     */
    private Button createAddFeatureButton() {
        Button button = new Button("+");
        addCrossPlatformAddShortcut(button, KeyCode.F, "Add a new feature");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Feature") {
                    public void handleError(Exception e) {
                        // ACE-241: notify user when the feature-creation failed!
                        getWindow().showNotification("Failed to add new feature!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    public void onOk(String name, String description) {
                        createFeature(name, description);
                    }
                };
                window.show(getMainWindow());
            }
        });
        return button;
    }

    /**
     * Create a button to show a popup window for adding a new target. On success this calls the createTarget() method
     *
     * @param user
     *
     * @return the add-target button instance.
     */
    private Button createAddTargetButton() {
        Button button = new Button("+");
        addCrossPlatformAddShortcut(button, KeyCode.G, "Add a new target");
        button.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                GenericAddWindow window = new GenericAddWindow("Add Target") {
                    protected void handleError(Exception e) {
                        // ACE-241: notify user when the target-creation failed!
                        getWindow().showNotification("Failed to add new target!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void initDialog() {
                        m_name.setCaption("Identifier");
                        m_description.setVisible(false);

                        super.initDialog();
                    }

                    protected void onOk(String id, String description) {
                        createTarget(id);
                    }
                };
                window.show(getMainWindow());
            }
        });
        return button;
    }

    /**
     * @return a button to approve one or more targets.
     */
    private Button createApproveTargetsButton() {
        final Button button = new Button("A");
        button.setDisableOnClick(true);
        button.setImmediate(true);
        button.setEnabled(false);
        button.addListener(new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                m_targetsPanel.approveSelectedTargets();
            }
        });
        m_targetsPanel.addListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                TargetsPanel targetsPanel = (TargetsPanel) event.getProperty();

                Collection<?> itemIDs = (Collection<?>) targetsPanel.getValue();

                boolean enabled = false;
                for (Object itemID : itemIDs) {
                    if (targetsPanel.isItemApproveNeeded(itemID)) {
                        enabled = true;
                        break;
                    }
                }

                button.setEnabled(enabled);
            }
        });
        return button;
    }

    private ArtifactsPanel createArtifactsPanel() {
        return new ArtifactsPanel(m_associations, this, m_cacheRate, m_pageLength) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Artifact", object, extensions) {
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit artifact!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
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

    private HorizontalLayout createArtifactToolbar() {
        HorizontalLayout result = new HorizontalLayout();
        result.setSpacing(true);
        result.addComponent(createAddArtifactButton());
        result.addComponent(createManageResourceProcessorsButton());
        return result;
    }

    private DistributionsPanel createDistributionsPanel() {
        return new DistributionsPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Distribution", object, extensions) {
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit distribution!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
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

    private HorizontalLayout createDistributionToolbar() {
        HorizontalLayout result = new HorizontalLayout();
        result.setSpacing(true);
        result.addComponent(createAddDistributionButton());
        return result;
    }

    private FeaturesPanel createFeaturesPanel() {
        return new FeaturesPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Feature", object, extensions) {
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit feature!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        object.setDescription(description);
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

    private HorizontalLayout createFeatureToolbar() {
        HorizontalLayout result = new HorizontalLayout();
        result.setSpacing(true);
        result.addComponent(createAddFeatureButton());
        return result;
    }

    private Button createManageResourceProcessorsButton() {
        // Solves ACE-224
        Button button = new Button("RP");
        button.addListener(new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                showManageResourceProcessorsDialog();
            }
        });
        return button;
    }

    private Button createRegisterTargetsButton() {
        final Button button = new Button("R");
        button.setDisableOnClick(true);
        button.setImmediate(true);
        button.setEnabled(false);
        button.addListener(new Button.ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                m_targetsPanel.registerSelectedTargets();
            }
        });
        m_targetsPanel.addListener(new ValueChangeListener() {
            @Override
            public void valueChange(ValueChangeEvent event) {
                TargetsPanel targetsPanel = (TargetsPanel) event.getProperty();

                Collection<?> itemIDs = (Collection<?>) targetsPanel.getValue();

                boolean enabled = false;
                for (Object itemID : itemIDs) {
                    if (targetsPanel.isItemRegistrationNeeded(itemID)) {
                        enabled = true;
                        break;
                    }
                }

                button.setEnabled(enabled);
            }
        });
        return button;
    }

    private TargetsPanel createTargetsPanel() {
        return new TargetsPanel(m_associations, this) {
            @Override
            protected EditWindow createEditor(final NamedObject object, final List<UIExtensionFactory> extensions) {
                return new EditWindow("Edit Target", object, extensions) {
                    @Override
                    protected void handleError(Exception e) {
                        getWindow().showNotification("Failed to edit target!", "<br/>Reason: " + e.getMessage(),
                            Notification.TYPE_ERROR_MESSAGE);
                    }

                    @Override
                    protected void initDialog(NamedObject object, List<UIExtensionFactory> factories) {
                        m_name.setCaption("Identifier");
                        m_name.setReadOnly(true);
                        m_description.setVisible(false);

                        super.initDialog(object, factories);
                    }

                    @Override
                    protected void onOk(String name, String description) throws Exception {
                        // Nothing to edit!
                    }

                    @Override
                    protected Map<String, Object> populateContext(Map<String, Object> context) {
                        if (object instanceof NamedTargetObject) {
                            context.put("object", m_statefulTargetRepository.get(object.getDefinition()));
                        }
                        return context;
                    }
                };
            }

            @Override
            protected TargetRepository getRepository() {
                return m_targetRepository;
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }

            @Override
            protected StatefulTargetRepository getStatefulTargetRepository() {
                return m_statefulTargetRepository;
            }
        };
    }

    private HorizontalLayout createTargetToolbar() {
        HorizontalLayout result = new HorizontalLayout();
        result.setSpacing(true);
        result.addComponent(createAddTargetButton());
        result.addComponent(createRegisterTargetsButton());
        result.addComponent(createApproveTargetsButton());
        return result;
    }

    private GridLayout createToolbar() {
        return new MainActionToolbar(m_useAuth) {
            @Override
            protected void doAfterCommit() throws IOException {
                updateTableData();

                m_statusLine.setStatus("Local changes committed...");
            }

            @Override
            protected void doAfterLogout() throws IOException {
                // Close the application and reload the main window...
                close();
            }

            @Override
            protected void doAfterRetrieve() throws IOException {
                updateTableData();

                m_statusLine.setStatus("Repositories updated...");
            }

            @Override
            protected void doAfterRevert() throws IOException {
                updateTableData();

                m_statusLine.setStatus("Local changes reverted...");
            }

            @Override
            protected RepositoryAdmin getRepositoryAdmin() {
                return m_admin;
            }

            @Override
            protected void log(int level, String msg, Exception e, Object... args) {
                m_log.log(level, String.format(msg, args), e);
            }

            private void updateTableData() {
                m_artifactsPanel.populate();
                m_featuresPanel.populate();
                m_distributionsPanel.populate();
                m_targetsPanel.populate();

                m_mainWindow.focus();
            }
        };
    }

    /**
     * Authenticates the given user by creating all dependent services.
     *
     * @param user
     * @throws IOException
     *             in case of I/O problems.
     */
    private boolean doLogin() {
        try {
            RepositoryAdminLoginContext context = m_admin.createLoginContext((User) getUser());

            // @formatter:off
            context
                .add(context.createShopRepositoryContext()
                    .setLocation(m_repository).setCustomer(customerName).setName(shopRepo).setWriteable())
                .add(context.createTargetRepositoryContext()
                    .setLocation(m_repository).setCustomer(customerName).setName(targetRepo).setWriteable())
                .add(context.createDeploymentRepositoryContext()
                    .setLocation(m_repository).setCustomer(customerName).setName(deployRepo).setWriteable());
            // @formatter:on

            m_admin.login(context);
            m_admin.checkout();

            initGrid();

            return true;
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_WARNING, "Login failed! Destroying session...", e);

            try {
                // Avoid errors when the user tries to login again (due to the stale session)...
                m_admin.logout(true /* force */);
            }
            catch (IllegalStateException inner) {
                // Ignore; probably we're not logged...
            }
            catch (IOException inner) {
                m_log.log(LogService.LOG_WARNING, "Logout failed! Session possibly not destroyed...", inner);
            }

            return false;
        }
    }

    private void initGrid() {
        User user = (User) getUser();
        Authorization auth = m_userAdmin.getAuthorization(user);
        int count = 0;
        for (String role : new String[] { "viewArtifact", "viewFeature", "viewDistribution", "viewTarget" }) {
            if (auth.hasRole(role)) {
                count++;
            }
        }

        final GenericUploadHandler uploadHandler = new GenericUploadHandler(m_sessionDir) {
            @Override
            public void updateProgress(long readBytes, long contentLength) {
                Float percentage = new Float(readBytes / (float) contentLength);
                m_progress.setValue(percentage);
            }

            @Override
            protected void artifactsUploaded(List<UploadHandle> uploadedArtifacts) {
                StringBuilder failedMsg = new StringBuilder();
                StringBuilder successMsg = new StringBuilder();
                Set<String> selection = new HashSet<>();

                for (UploadHandle handle : uploadedArtifacts) {
                    if (!handle.isSuccessful()) {
                        // Upload failed, so let's report this one...
                        appendFailure(failedMsg, handle);

                        m_log.log(LogService.LOG_ERROR, "Upload of " + handle.getFile() + " failed.", handle.getFailureReason());
                    }
                    else {
                        try {
                            // Upload was successful, try to upload it to our OBR...
                            ArtifactObject artifact = uploadToOBR(handle);
                            if (artifact != null) {
                                selection.add(artifact.getDefinition());

                                appendSuccess(successMsg, handle);
                            }
                        }
                        catch (ArtifactAlreadyExistsException exception) {
                            appendFailureExists(failedMsg, handle);

                            m_log.log(LogService.LOG_WARNING, "Upload of " + handle.getFilename() + " failed, as it already exists!");
                        }
                        catch (Exception exception) {
                            appendFailure(failedMsg, handle, exception);

                            m_log.log(LogService.LOG_ERROR, "Upload of " + handle.getFilename() + " failed.", exception);
                        }
                    }

                    // We're done with this (temporary) file, so we can remove it...
                    handle.cleanup();
                }

                m_artifactsPanel.setValue(selection);

                // Notify the user what the overall status was...
                Notification notification = createNotification(failedMsg, successMsg);
                getMainWindow().showNotification(notification);

                m_progress.setStyleName("invisible");
                m_statusLine.setStatus(notification.getCaption() + "...");
            }

            @Override
            protected void uploadStarted(UploadHandle upload) {
                m_progress.setStyleName("visible");
                m_progress.setValue(new Float(0.0f));

                m_statusLine.setStatus("Upload of '%s' started...", upload.getFilename());
            }

            private void appendFailure(StringBuilder sb, UploadHandle handle) {
                appendFailure(sb, handle, handle.getFailureReason());
            }

            private void appendFailure(StringBuilder sb, UploadHandle handle, Exception cause) {
                sb.append("<li>'").append(handle.getFile().getName()).append("': failed");
                if (cause != null) {
                    sb.append(", possible reason:<br/>").append(cause.getMessage());
                }
                sb.append("</li>");
            }

            private void appendFailureExists(StringBuilder sb, UploadHandle handle) {
                sb.append("<li>'").append(handle.getFile().getName()).append("': already exists in repository</li>");
            }

            private void appendSuccess(StringBuilder sb, UploadHandle handle) {
                sb.append("<li>'").append(handle.getFile().getName()).append("': added to repository</li>");
            }

            private Notification createNotification(StringBuilder failedMsg, StringBuilder successMsg) {
                String caption = "Upload completed";
                int delay = 500; // msec.
                StringBuilder notification = new StringBuilder();
                if (failedMsg.length() > 0) {
                    caption = "Upload completed with failures";
                    delay = -1;
                    notification.append("<ul>").append(failedMsg).append("</ul>");
                }
                if (successMsg.length() > 0) {
                    notification.append("<ul>").append(successMsg).append("</ul>");
                }
                if (delay < 0) {
                    notification.append("<p>(click to dismiss this notification).</p>");
                }

                Notification summary = new Notification(caption, notification.toString(), Notification.TYPE_TRAY_NOTIFICATION);
                summary.setDelayMsec(delay);
                return summary;
            }

            private ArtifactObject uploadToOBR(UploadHandle handle) throws IOException {
                return UploadHelper.importRemoteBundle(m_artifactRepository, handle.getFile());
            }
        };

        m_grid = new GridLayout(count, 4);
        m_grid.setSpacing(true);
        m_grid.setSizeFull();

        m_mainToolbar = createToolbar();
        m_grid.addComponent(m_mainToolbar, 0, 0, count - 1, 0);

        m_artifactsPanel = createArtifactsPanel();
        m_artifactToolbar = createArtifactToolbar();

        final DragAndDropWrapper artifactsPanelWrapper = new DragAndDropWrapper(m_artifactsPanel);
        artifactsPanelWrapper.setDragStartMode(DragStartMode.HTML5);
        artifactsPanelWrapper.setDropHandler(new ArtifactDropHandler(uploadHandler));
        artifactsPanelWrapper.setCaption(m_artifactsPanel.getCaption());
        artifactsPanelWrapper.setSizeFull();

        count = 0;
        if (auth.hasRole("viewArtifact")) {
            m_grid.addComponent(artifactsPanelWrapper, count, 2);
            m_grid.addComponent(m_artifactToolbar, count, 1);
            count++;
        }

        m_featuresPanel = createFeaturesPanel();
        m_featureToolbar = createFeatureToolbar();

        if (auth.hasRole("viewFeature")) {
            m_grid.addComponent(m_featuresPanel, count, 2);
            m_grid.addComponent(m_featureToolbar, count, 1);
            count++;
        }

        m_distributionsPanel = createDistributionsPanel();
        m_distributionToolbar = createDistributionToolbar();

        if (auth.hasRole("viewDistribution")) {
            m_grid.addComponent(m_distributionsPanel, count, 2);
            m_grid.addComponent(m_distributionToolbar, count, 1);
            count++;
        }

        m_targetsPanel = createTargetsPanel();
        m_targetToolbar = createTargetToolbar();

        if (auth.hasRole("viewTarget")) {
            m_grid.addComponent(m_targetsPanel, count, 2);
            m_grid.addComponent(m_targetToolbar, count, 1);
        }

        m_statusLine = new StatusLine();

        m_grid.addComponent(m_statusLine, 0, 3, 2, 3);

        m_progress = new ProgressIndicator(0f);
        m_progress.setStyleName("invisible");
        m_progress.setIndeterminate(false);
        m_progress.setPollingInterval(1000);

        m_grid.addComponent(m_progress, 3, 3);

        m_grid.setRowExpandRatio(2, 1.0f);

        m_grid.setColumnExpandRatio(0, 0.31f);
        m_grid.setColumnExpandRatio(1, 0.23f);
        m_grid.setColumnExpandRatio(2, 0.23f);
        m_grid.setColumnExpandRatio(3, 0.23f);

        // Wire up all panels so they have the correct associations...
        m_artifactsPanel.setAssociatedTables(null, m_featuresPanel);
        m_featuresPanel.setAssociatedTables(m_artifactsPanel, m_distributionsPanel);
        m_distributionsPanel.setAssociatedTables(m_featuresPanel, m_targetsPanel);
        m_targetsPanel.setAssociatedTables(m_distributionsPanel, null);

        addListener(m_statusLine, StatefulTargetObject.TOPIC_ALL, RepositoryObject.PUBLIC_TOPIC_ROOT.concat(RepositoryObject.TOPIC_ALL_SUFFIX));
        addListener(m_mainToolbar, StatefulTargetObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_LOGIN, RepositoryAdmin.TOPIC_REFRESH);
        addListener(m_artifactsPanel, ArtifactObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_LOGIN, RepositoryAdmin.TOPIC_REFRESH);
        addListener(m_featuresPanel, FeatureObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_LOGIN, RepositoryAdmin.TOPIC_REFRESH);
        addListener(m_distributionsPanel, DistributionObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_LOGIN, RepositoryAdmin.TOPIC_REFRESH);
        addListener(m_targetsPanel, StatefulTargetObject.TOPIC_ALL, TargetObject.TOPIC_ALL, RepositoryAdmin.TOPIC_STATUSCHANGED, RepositoryAdmin.TOPIC_LOGIN, RepositoryAdmin.TOPIC_REFRESH);

        m_mainWindow.addComponent(m_grid);
        // Ensure the focus is properly defined (for the shortcut keys to work)...
        m_mainWindow.focus();
    }

    /**
     * @return <code>true</code> if the login succeeded, <code>false</code> otherwise.
     */
    private boolean loginAutomatically() {
        setUser(m_userAdmin.getUser("username", m_userName));
        return doLogin();
    }

    /**
     * Shows the login window on the center of the main window.
     */
    private void showLoginWindow() {
        LoginWindow loginWindow = new LoginWindow(m_log, this);

        loginWindow.openWindow(getMainWindow());
    }
}
