package org.apache.ace.webui.vaadin.bazaar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.SessionFactory;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.UserAdmin;

import com.vaadin.Application;
import com.vaadin.terminal.gwt.server.AbstractApplicationServlet;

public class VaadinServlet extends AbstractApplicationServlet {
	private static final long serialVersionUID = 1L;

	private volatile DependencyManager m_manager;
    
    @Override
    protected Class<? extends Application> getApplicationClass() {
        return BazaarManager.class;
    }

    @Override
    protected Application getNewApplication(HttpServletRequest request)	throws ServletException {
        Application application = new BazaarManager();
        m_manager.add(m_manager.createComponent()
            .setImplementation(application)
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
                .setService(LogService.class)
                .setRequired(false)
                )
            );
        
        return application;
    }
}