package org.apache.ace.webui.vaadin;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.Group2LicenseAssociation;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.data.Item;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.event.Transferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.TargetDetails;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.terminal.Sizeable;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.CellStyleGenerator;
import com.vaadin.ui.Table.TableDragMode;
import com.vaadin.ui.Table.TableTransferable;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/*

TODO:
 - Add an editor that appears on double clicking on an item in a table (partially done)
 - Enable drag and drop to create associations (partially done)
 - Add buttons to remove associations (think about how we can better visualize this)
 - Add buttons to create new items in all of the tables (partially done)
 - Add drag and drop to the artifacts column (partially done)
 - Create a special editor for dealing with new artifact types
 */
public class VaadinClient extends com.vaadin.Application {
    private static final String OBJECT_NAME = "name";
	private static final String OBJECT_DESCRIPTION = "description";
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
    private volatile GatewayRepository m_targetRepository;
    private volatile Artifact2GroupAssociationRepository m_artifact2GroupAssciationRepository;
    private volatile Group2LicenseAssociationRepository m_group2LicenseAssociationRepository;
    private volatile License2GatewayAssociationRepository m_license2GatewayAssociationRepository;
    private volatile RepositoryAdmin m_admin;
    private volatile LogService m_log;
    private String m_sessionID;
    private volatile List<LicenseObject> m_distributions;
    private Table m_artifactsPanel;
    private Table m_featuresPanel;
	private Table m_distributionsPanel;
    private Table m_targetsPanel;
    private List<ArtifactObject> m_artifacts;
    private List<GroupObject> m_features;
    private List<GatewayObject> m_targets;
    private List<RepositoryObject> m_associatedItems = new ArrayList<RepositoryObject>();
    private List<RepositoryObject> m_relatedItems = new ArrayList<RepositoryObject>();
    
    private Table m_activeTable;
    private Set<?> m_activeSelection;
    public SelectionListener m_activeSelectionListener;

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
        addDependency(component, GatewayRepository.class);
        addDependency(component, Artifact2GroupAssociationRepository.class);
        addDependency(component, Group2LicenseAssociationRepository.class);
        addDependency(component, License2GatewayAssociationRepository.class);
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
        
        try {
            User user = m_userAdmin.getUser("username", "d");
            RepositoryAdminLoginContext context = m_admin.createLoginContext(user);
            
            context.addShopRepository(new URL(hostName + endpoint), customerName, shopRepo, true)
                .setObrBase(new URL(obr))
                .addGatewayRepository(new URL(hostName + endpoint), customerName, gatewayRepo, true)
                .addDeploymentRepository(new URL(hostName + endpoint), customerName, deployRepo, true);
            m_admin.login(context);
            m_admin.checkout();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
        setTheme("ace");
        final Window main = new Window("Apache ACE - User Interface - " + this);
        setMainWindow(main);
        main.getContent().setSizeFull();
        
        final GridLayout grid = new GridLayout(4, 3);
        grid.setSpacing(true);

        grid.setWidth(100, Sizeable.UNITS_PERCENTAGE);
        grid.setHeight(100, Sizeable.UNITS_PERCENTAGE);

        // toolbar
        GridLayout toolbar = new GridLayout(3,1);
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
                    updateTableData();
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
        
        grid.addComponent(toolbar, 0, 0, 3, 0);
        
        m_distributionsPanel = new Table("Distributions");
        m_distributionsPanel.addContainerProperty(OBJECT_NAME, String.class, null);
        m_distributionsPanel.addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
        m_distributionsPanel.addContainerProperty("button", Button.class, null);
        m_distributionsPanel.setSizeFull();
        m_distributionsPanel.setCellStyleGenerator(new CellStyleGeneratorImplementation() {
            @Override
            public boolean equals(Object itemId, RepositoryObject object) {
                return ((object instanceof LicenseObject) && ((LicenseObject) object).getName().equals(itemId));
            }
        });
        m_distributionsPanel.setSelectable(true);
        m_distributionsPanel.setMultiSelect(true);
        m_distributionsPanel.setImmediate(true);
        m_distributionsPanel.setDropHandler(new DropHandler() {

            public void drop(DragAndDropEvent event) {
                Transferable transferable = event.getTransferable();
                TargetDetails targetDetails = event.getTargetDetails();
                System.out.println("F: " + transferable);
                if (transferable instanceof TableTransferable) {
                    TableTransferable tt = (TableTransferable) transferable;
                    Object fromItemId = tt.getItemId();
                    System.out.println("FF: " + fromItemId);
                    System.out.println("T: " + targetDetails.getClass().getName());
                    if (targetDetails instanceof AbstractSelectTargetDetails) {
                        AbstractSelectTargetDetails ttd = (AbstractSelectTargetDetails) targetDetails;
                        Object toItemId = ttd.getItemIdOver();
                        System.out.println("TT: " + toItemId);
                        m_group2LicenseAssociationRepository.create(getFeature((String) fromItemId), getDistribution((String) toItemId));
                        updateTableData();
                    }
                }
            }

            public AcceptCriterion getAcceptCriterion() {
                return AcceptAll.get();
            }});
        
        
        grid.setRowExpandRatio(2, 1.0f);
        
        grid.addComponent(m_distributionsPanel, 2, 2);
        grid.addComponent(new Button("Add distribution..."), 2, 1);
        
        m_artifactsPanel = new Table("Artifacts");
        m_artifactsPanel.addContainerProperty(OBJECT_NAME, String.class, null);
        m_artifactsPanel.addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
        m_artifactsPanel.setSizeFull();
        m_artifactsPanel.setCellStyleGenerator(new CellStyleGeneratorImplementation() {
            @Override
            public boolean equals(Object itemId, RepositoryObject object) {
                return ((object instanceof ArtifactObject) && ((ArtifactObject) object).getName().equals(itemId));
            }
        });
        m_artifactsPanel.setSelectable(true);
        m_artifactsPanel.setMultiSelect(true);
        m_artifactsPanel.setImmediate(true);
        grid.addComponent(m_artifactsPanel, 0, 2);
        grid.addComponent(new Button("Add artifact..."), 0, 1);
        
        Button addFeature = new Button("Add Feature...");
        addFeature.addListener(new Button.ClickListener() {
            public void buttonClick(ClickEvent event) {
                final Window featureWindow;
                featureWindow = new Window();
                featureWindow.setModal(true);
                featureWindow.setCaption("Add Feature");
                featureWindow.setWidth("15em");
                
             // Configure the windws layout; by default a VerticalLayout
                VerticalLayout layout = (VerticalLayout) featureWindow.getContent();
                layout.setMargin(true);
                layout.setSpacing(true);

                final TextField name = new TextField("name");
                final TextField description = new TextField("description");

                layout.addComponent(name);
                layout.addComponent(description);
                
                Button close = new Button("Close", new Button.ClickListener() {
                    // inline click-listener
                    public void buttonClick(ClickEvent event) {
                        // close the window by removing it from the parent window
                        (featureWindow.getParent()).removeWindow(featureWindow);
                        // create the feature
                        createFeature((String) name.getValue(), (String) description.getValue());
                        updateTableData();
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
                name.focus();
            }
        });
        
        
        
        grid.addComponent(addFeature, 1, 1);
        
        m_featuresPanel = new Table("Features");
        m_featuresPanel.addContainerProperty(OBJECT_NAME, String.class, null);
        m_featuresPanel.addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
        m_featuresPanel.setSizeFull();
        m_featuresPanel.setCellStyleGenerator(new CellStyleGeneratorImplementation() {
            @Override
            public boolean equals(Object itemId, RepositoryObject object) {
                return ((object instanceof GroupObject) && ((GroupObject) object).getName().equals(itemId));
            }
        });
        m_featuresPanel.setSelectable(true);
        m_featuresPanel.setMultiSelect(true);
        m_featuresPanel.setImmediate(true);
        m_featuresPanel.setDragMode(TableDragMode.ROW);
        m_featuresPanel.addListener(new ItemClickListener() {
            public void itemClick(ItemClickEvent event) {
                if (event.isDoubleClick()) {
                    String itemId = (String) event.getItemId();
                    final GroupObject feature = getFeature(itemId);
                    final Window featureWindow = new Window();
                    featureWindow.setModal(true);
                    featureWindow.setCaption("Edit Feature");
                    featureWindow.setWidth("15em");
                    
                 // Configure the windws layout; by default a VerticalLayout
                    VerticalLayout layout = (VerticalLayout) featureWindow.getContent();
                    layout.setMargin(true);
                    layout.setSpacing(true);

                    final TextField name = new TextField("name");
                    final TextField description = new TextField("description");

                    name.setValue(feature.getName());
                    description.setValue(feature.getDescription());
                    
                    layout.addComponent(name);
                    layout.addComponent(description);
                    
                    Button close = new Button("Close", new Button.ClickListener() {
                        // inline click-listener
                        public void buttonClick(ClickEvent event) {
                            // close the window by removing it from the parent window
                            (featureWindow.getParent()).removeWindow(featureWindow);
                            // create the feature
                            feature.setDescription((String) description.getValue());
                            updateTableData();
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
            }
        });

        
        grid.addComponent(m_featuresPanel, 1, 2);
        
        m_targetsPanel = new Table("Targets");
        m_targetsPanel.addContainerProperty(OBJECT_NAME, String.class, null);
        m_targetsPanel.addContainerProperty(OBJECT_DESCRIPTION, String.class, null);
        m_targetsPanel.setSizeFull();
        m_targetsPanel.setCellStyleGenerator(new CellStyleGeneratorImplementation() {
            @Override
            public boolean equals(Object itemId, RepositoryObject object) {
                return ((object instanceof GatewayObject) && ((GatewayObject) object).getID().equals(itemId));
            }
        });
        m_targetsPanel.setSelectable(true);
        m_targetsPanel.setMultiSelect(true);
        m_targetsPanel.setImmediate(true);
        grid.addComponent(m_targetsPanel, 3, 2);
        grid.addComponent(new Button("Add target..."), 3, 1);
        
        m_artifactsPanel.addListener(new SelectionListener(m_artifactsPanel, new Class[] {}, new Class[] { GroupObject.class, LicenseObject.class, GatewayObject.class }, new Table[] { m_featuresPanel, m_distributionsPanel, m_targetsPanel }) {
            @Override
            public RepositoryObject lookup(Object value) {
                for (ArtifactObject object : m_artifactRepository.get()) {
                    if (object.getName().equals(value)) {
                        System.out.println("Found: " + object.getName());
                        return object;
                    }
                }
                return null;
            }
        });
        m_featuresPanel.addListener(new SelectionListener(m_featuresPanel, new Class[] { ArtifactObject.class }, new Class[] { LicenseObject.class, GatewayObject.class }, new Table[] { m_artifactsPanel, m_distributionsPanel, m_targetsPanel }) {
            @Override
            public RepositoryObject lookup(Object value) {
                for (GroupObject object : m_featureRepository.get()) {
                    if (object.getName().equals(value)) {
                        System.out.println("Found: " + object.getName());
                        return object;
                    }
                }
                return null;
            }
        });
        m_distributionsPanel.addListener(new SelectionListener(m_distributionsPanel, new Class[] { GroupObject.class, ArtifactObject.class }, new Class[] { GatewayObject.class }, new Table[] { m_artifactsPanel, m_featuresPanel, m_targetsPanel }) {
            @Override
            public RepositoryObject lookup(Object value) {
                for (LicenseObject object : m_distributionRepository.get()) {
                    if (object.getName().equals(value)) {
                        System.out.println("Found: " + object.getName());
                        return object;
                    }
                }
                return null;
            }
        });
        m_targetsPanel.addListener(new SelectionListener(m_targetsPanel, new Class[] { LicenseObject.class, GroupObject.class, ArtifactObject.class}, new Class[] {}, new Table[] { m_artifactsPanel, m_featuresPanel, m_distributionsPanel }) {
            @Override
            public RepositoryObject lookup(Object value) {
                for (GatewayObject object : m_targetRepository.get()) {
                    if (object.getID().equals(value)) {
                        System.out.println("Found: " + object.getID());
                        return object;
                    }
                }
                return null;
            }
        });

        
        updateTableData();
        
        main.addComponent(grid);
    }
    
    private void createFeature(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(GroupObject.KEY_NAME, name);
        attributes.put(GroupObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_featureRepository.create(attributes, tags);
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
    
    private void deleteFeature(String name) {
        GroupObject feature = getFeature(name);
        if (feature != null) {
            m_featureRepository.remove(feature);
            // TODO cleanup links?
        }
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
    
//    private GroupObject editFeature(String name, String description) {
//        try {
//            List<GroupObject> list = m_featureRepository.get(m_context.createFilter("(" + GroupObject.KEY_NAME + "=" + name + ")"));
//            if (list.size() == 1) {
//                return list.get(0);
//            }
//            Map<String, String> attributes = new HashMap<String, String>();
////            attributes.put(GroupObject.KEY_NAME, name);
//            attributes.put(GroupObject.KEY_DESCRIPTION, description);
//            Map<String, String> tags = new HashMap<String, String>();
////            m_featureRepository.create(attributes, tags);
//        }
//        catch (InvalidSyntaxException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
    private void createDistribution(String name, String description) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(LicenseObject.KEY_NAME, name);
        attributes.put(LicenseObject.KEY_DESCRIPTION, description);
        Map<String, String> tags = new HashMap<String, String>();
        m_distributionRepository.create(attributes, tags);
    }
    
    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction'
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(FROM from, Class<TO> toClass) {
        return from.getAssociations(toClass);
    }
    
    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction', starting with a list of objects
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(List<FROM> from, Class<TO> toClass) {
        List<TO> result = new ArrayList<TO>();
        for (RepositoryObject o : from) {
            result.addAll(getRelated(o, toClass));
        }
        return result;
    }


    
    private void updateTableData() {
        m_artifacts = m_artifactRepository.get();
        m_artifactsPanel.removeAllItems();
        for (ArtifactObject artifact : m_artifacts) {
            Item item = m_artifactsPanel.addItem(artifact.getName());
            item.getItemProperty(OBJECT_NAME).setValue(artifact.getName());
            item.getItemProperty(OBJECT_DESCRIPTION).setValue(artifact.getDescription());
        }
        m_features = m_featureRepository.get();
        m_featuresPanel.removeAllItems();
        for (GroupObject group : m_features) {
            Item licenseItem = m_featuresPanel.addItem(group.getName());
            licenseItem.getItemProperty(OBJECT_NAME).setValue(group.getName());
            licenseItem.getItemProperty(OBJECT_DESCRIPTION).setValue(group.getDescription());
        }
        m_distributions = m_distributionRepository.get();
        m_distributionsPanel.removeAllItems();
        for (LicenseObject license : m_distributions) {
            Item licenseItem = m_distributionsPanel.addItem(license.getName());
            licenseItem.getItemProperty(OBJECT_NAME).setValue(license.getName());
            licenseItem.getItemProperty(OBJECT_DESCRIPTION).setValue(license.getDescription());
            Button removeLinkButton = new Button("-");
            final LicenseObject distribution = license;
            removeLinkButton.addListener(new Button.ClickListener() {
                public void buttonClick(ClickEvent event) {
                    System.out.println("Removing link to " + distribution.getName());
                    if (m_activeTable.equals(m_featuresPanel)) {
                        Set<?> selection = m_activeSelection;
                        if (selection != null) {
                            for (Object item : selection) {
                                RepositoryObject object = m_activeSelectionListener.lookup(item);
                                List<Group2LicenseAssociation> associations = distribution.getAssociationsWith((GroupObject) object);
                                for (Group2LicenseAssociation g2l : associations) {
                                    System.out.println("> " + g2l.getLeft() + " <-> " + g2l.getRight());
                                    m_group2LicenseAssociationRepository.remove(g2l);
                                }
                                m_associatedItems.remove(object);
                            }
//                            updateTableData();
                        }
                    }
                    if (m_activeTable.equals(m_targetsPanel)) {
                        
                    }
                }
            });
            licenseItem.getItemProperty("button").setValue(removeLinkButton);
        }
        m_targets = m_targetRepository.get();
        m_targetsPanel.removeAllItems();
        for (GatewayObject license : m_targets) {
            Item licenseItem = m_targetsPanel.addItem(license.getID());
            licenseItem.getItemProperty(OBJECT_NAME).setValue(license.getID());
            licenseItem.getItemProperty(OBJECT_DESCRIPTION).setValue("?");
        }
    }
    
    @Override
    public void close() {
        super.close();
        // when the session times out
        // TODO: clean up the ace client session?
    }

    public abstract class SelectionListener implements Table.ValueChangeListener {
        private final Table m_table;
        private final Table[] m_tablesToRefresh;
        private final Class[] m_left;
        private final Class[] m_right;
        public SelectionListener(Table table, Class[] left, Class[] right, Table[] tablesToRefresh) {
            m_table = table;
            m_left = left;
            m_right = right;
            m_tablesToRefresh = tablesToRefresh;
        }
        
        public void valueChange(ValueChangeEvent event) {
            m_activeSelectionListener = this;
            
            // set the active table
            m_activeTable = m_table;
            
            // in multiselect mode, a Set of itemIds is returned,
            // in singleselect mode the itemId is returned directly
            Set<?> value = (Set<?>) event.getProperty().getValue();
            if (null == value || value.size() == 0) {
//                    selected.setValue("No selection");
            } else {
//                    selected.setValue("Selected: " + table.getValue());
            }

            // remember the active selection too
            m_activeSelection = value;

            if (value == null) {
                System.out.println("no selection");
            }
            else {
                System.out.println("selection:");
                
                m_associatedItems.clear();
                m_relatedItems.clear();
                for (Object val : value) {
                    System.out.println(" - " + m_table.getItem(val).getItemProperty(OBJECT_NAME) + " " + val);
                    RepositoryObject lo = lookup(val);
                    
                    List related = null;
                    for (int i = 0; i < m_left.length; i++) {
                        if (i == 0) {
                            related = getRelated(lo, m_left[i]);
                            System.out.println("left associated:");
                            for (Object o : related) {
                                System.out.println(" -> " + o);
                            }
                            m_associatedItems.addAll(related);
                        }
                        else {
                            related = getRelated(related, m_left[i]);
                            System.out.println("left related:");
                            for (Object o : related) {
                                System.out.println(" -> " + o);
                            }
                            m_relatedItems.addAll(related);
                        }
                    }
                    for (int i = 0; i < m_right.length; i++) {
                        if (i == 0) {
                            related = getRelated(lo, m_right[i]);
                            System.out.println("right associated:");
                            for (Object o : related) {
                                System.out.println(" -> " + o);
                            }
                            m_associatedItems.addAll(related);
                        }
                        else {
                            related = getRelated(related, m_right[i]);
                            System.out.println("right related:");
                            for (Object o : related) {
                                System.out.println(" -> " + o);
                            }
                            m_relatedItems.addAll(related);
                        }
                    }
                    
                    for (Table t : m_tablesToRefresh) {
                        System.out.println("refreshing " + t);
                        t.setValue(null);
                        t.requestRepaint();
                    }
                    // when switching columns, we need to repaint, but it messes up the
                    // cursor position
//                    m_table.requestRepaint();
                }
            }
        }
        public abstract RepositoryObject lookup(Object value);
    }

    /** Highlights associated and related items in other columns. */
    private abstract class CellStyleGeneratorImplementation implements CellStyleGenerator {
        public String getStyle(Object itemId, Object propertyId) {
            if (propertyId == null) {
                // no propertyId, styling row
                for (RepositoryObject o : m_associatedItems) {
                    System.out.println("cellrenderer probing: " + o);
                    if (equals(itemId, o)) {
                        System.out.println(" -> associated");
                        return "associated";
                    }
                }
                for (RepositoryObject o : m_relatedItems) {
                    if (equals(itemId, o)) {
                        System.out.println(" -> related");
                        return "related";
                    }
                }
                System.out.println("cellrenderer: unrelated");
            }
            return null;
        }
        public abstract boolean equals(Object itemId, RepositoryObject object);
    }
}