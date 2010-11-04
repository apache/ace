package org.apache.ace.webui.vaadin.bazaar;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryAdminLoginContext;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.terminal.ExternalResource;
import com.vaadin.terminal.Sizeable;
import com.vaadin.terminal.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Embedded;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;
import com.vaadin.ui.Button.ClickEvent;

public class BazaarManager extends com.vaadin.Application {
	private static final long serialVersionUID = 1L;

    private volatile DependencyManager m_manager;
    private volatile SessionFactory m_sessionFactory;
    private volatile UserAdmin m_userAdmin;
    private volatile LicenseRepository m_distributionRepository;
    private volatile RepositoryAdmin m_admin;
    private volatile LogService m_log;
    private String m_sessionID;
    
    private static long SESSION_ID = 1;

    private static String gatewayRepo = "gateway";
    private static String shopRepo = "shop";
    private static String deployRepo = "deployment";
    private static String customerName = "apache";
    private static String hostName = "http://localhost:8080";
    private static String endpoint = "/repository";
    private static String obr = "http://localhost:8080/obr/";
    private volatile List<LicenseObject> m_distributions;

	private Panel m_distributionsPanel;
    
	private static long generateSessionID() {
        return SESSION_ID++;
    }
    
    public void setupDependencies(Component component) {
        System.out.println("SETUP " + this);
        m_sessionID = "" + generateSessionID();
        m_sessionFactory.createSession(m_sessionID);
        component.add(m_manager.createServiceDependency()
            .setService(RepositoryAdmin.class, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
            .setRequired(true)
            .setInstanceBound(true)
            );
        component.add(m_manager.createServiceDependency()
            .setService(LicenseRepository.class, "(" + SessionFactory.SERVICE_SID + "=" + m_sessionID + ")")
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
        
        setTheme("reindeer");
        final Window main = new Window("Apache ACE - Bazaar Manager - " + this);
        setMainWindow(main);
        main.getContent().setSizeFull();
        
        final GridLayout grid = new GridLayout(1, 2);
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
        
        grid.addComponent(toolbar, 0, 0);
        
        m_distributionsPanel = new Panel();
        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setMargin(true);
        m_distributionsPanel.setContent(layout);
        m_distributionsPanel.setSizeFull();
        m_distributionsPanel.setWidth(800, Sizeable.UNITS_PIXELS);
        grid.setRowExpandRatio(1, 1.0f);
        
        grid.addComponent(m_distributionsPanel, 0, 1);
        
        updateTableData();
        
        main.addComponent(grid);
    }
    
    /**
     * Panel representing a single distribution (license), allowing editing.
     * TODO we probably could do this way smarter using Properties and Items
     */
    private class DistributionPanel extends Panel {
		private static final long serialVersionUID = 1L;
		
		private final LicenseObject m_license;
		private Embedded m_icon;

		public DistributionPanel(LicenseObject license) {
			super(license.getName());
			m_license = license;
			setContent();
    	}

		private void setContent() {
			GridLayout grid = new GridLayout(3, 2);
			setContent(grid);
			grid.setSpacing(true);
			grid.setColumnExpandRatio(1, 1.0f);
			grid.setColumnExpandRatio(2, 1.0f);
			grid.setSizeFull();
			String icon = m_license.getTag("icon");
			m_icon = new Embedded("", icon == null ? new ThemeResource("../runo/icons/64/document.png") : new ExternalResource(icon));
			grid.addComponent(m_icon, 0, 0);
			
	        TextField description = new TextField("", m_license.getDescription() == null ? "" : m_license.getDescription());
	        description.setImmediate(true);
	        description.setRows(5);
	        description.setWidth(100, Sizeable.UNITS_PERCENTAGE);
	        description.addListener(new Property.ValueChangeListener() {
	            public void valueChange(ValueChangeEvent event) {
	            	String value = (String) event.getProperty().getValue();
	            	m_license.setDescription(value);
	            }
	        });
	        grid.addComponent(description, 1, 0, 2, 0);
	        
	        CheckBox visible = new CheckBox("Show in Bazaar", "true".equals(m_license.getTag("bazaar")));
	        visible.setImmediate(true);
	        visible.addListener(new Property.ValueChangeListener() {
	            public void valueChange(ValueChangeEvent event) {
	            	m_license.addTag("bazaar", "" + (((Boolean) event.getProperty().getValue()) ? "true" : "false"));
	            }
	        });
	        grid.addComponent(visible, 1, 1);
	        
	        Label installed = new Label("Installed on " + m_license.getAssociations(GatewayObject.class).size() + " devices.");
			grid.addComponent(installed, 2, 1);
	        grid.setComponentAlignment(installed, Alignment.MIDDLE_RIGHT);

	        Button iconButton = new Button("icon...");
	        grid.addComponent(iconButton, 0, 1);
	        
			iconButton.addListener(new Button.ClickListener() {
				public void buttonClick(ClickEvent event) {
					IconWindow window = new IconWindow(m_license);
					getMainWindow().addWindow(window);
				}
			});
		}

		/**
		 * Helper window, which allows the user to set an icon for a given license.
		 */
		private class IconWindow extends Window {
			public IconWindow(final LicenseObject license) {
				setModal(true);
				setWidth(350, UNITS_PIXELS);

				VerticalLayout layout = new VerticalLayout();
				layout.setSizeFull();
				layout.setSpacing(true);
				layout.setMargin(true);
				setContent(layout);

				final TextField textField = new TextField("", license.getTag("icon"));
				textField.setImmediate(true);
				addComponent(textField);
				textField.setWidth(100, Sizeable.UNITS_PERCENTAGE);
				layout.setExpandRatio(textField, 1.0f);
				layout.setComponentAlignment(textField, Alignment.MIDDLE_LEFT);
				
				Button ok = new Button("OK");
				addComponent(ok);
				layout.setComponentAlignment(ok, Alignment.MIDDLE_RIGHT);
				
				ok.addListener(new Button.ClickListener() {
					public void buttonClick(ClickEvent event) {
						license.addTag("icon", (String) textField.getValue());
						m_icon.setSource(new ExternalResource((String) textField.getValue()));
						IconWindow.this.close();
					}
				});
	    	}
	    }
    }

    private void updateTableData() {
        m_distributions = m_distributionRepository.get();
        
        m_distributionsPanel.removeAllComponents();
        
        for (LicenseObject license : m_distributions) {
        	m_distributionsPanel.addComponent(new DistributionPanel(license));
        }
    }
    
    @Override
    public void close() {
        super.close();
        // when the session times out
        // TODO: clean up the ace client session?
    }
}