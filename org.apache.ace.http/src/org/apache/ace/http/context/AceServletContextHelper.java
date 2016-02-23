package org.apache.ace.http.context;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;

import java.io.IOException;
import java.util.Dictionary;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.authentication.api.AuthenticationService;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.log.LogService;
import org.osgi.service.useradmin.User;

public class AceServletContextHelper extends ServletContextHelper implements ManagedService{

    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    private static final String KEY_CONTEXT_PATH = "context.path";
    
    private static final String DEFAULT_CONTEXT_PATH = "/";
    
    private volatile DependencyManager m_dependencyManager;
    private volatile Component m_component;
    private volatile LogService m_log;
    private volatile AuthenticationService m_authenticationService;
    
    private volatile boolean m_useAuth;
    
    public AceServletContextHelper(Bundle bundle) {
        super(bundle);
    }
    
    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return authenticate(request); 
    }

    /**
     * Called by Dependency Manager upon initialization of this component.
     * 
     * @param comp the component to initialize, cannot be <code>null</code>.
     */
    protected void init() {
        m_component.add(m_dependencyManager.createServiceDependency()
            .setService(AuthenticationService.class)
            .setRequired(m_useAuth)
            );
    }
    
    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String useAuthString = (String) settings.get(KEY_USE_AUTHENTICATION);
            if ((useAuthString == null) ||
                !("true".equalsIgnoreCase(useAuthString) || "false".equalsIgnoreCase(useAuthString))) {
                throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
            }
            boolean useAuth = Boolean.parseBoolean(useAuthString);
            m_useAuth = useAuth;
            
            String contextPath = (String) settings.get(KEY_CONTEXT_PATH);
            if (contextPath == null) {
                contextPath = DEFAULT_CONTEXT_PATH;
            } else if (!contextPath.equals(DEFAULT_CONTEXT_PATH) && (!contextPath.startsWith("/") || contextPath.endsWith("/"))) {
                throw new ConfigurationException(KEY_CONTEXT_PATH, "Invalid value context path, context path should start with a '/' and NOT end with a '/'!");
            } 
            
            updateContextPath(contextPath);
        }
        else {
            m_useAuth = false;
            updateContextPath(DEFAULT_CONTEXT_PATH);
        }
    }
    
    private void updateContextPath(String pattern) {
        Dictionary<Object,Object> serviceProperties = m_component.getServiceProperties();
        String currentPath = (String) serviceProperties.get(HTTP_WHITEBOARD_CONTEXT_PATH);
        if (!pattern.equals(currentPath)){
            serviceProperties.put(HTTP_WHITEBOARD_CONTEXT_PATH, pattern);
            m_component.setServiceProperties(serviceProperties);
        }
    }
    

    
    /**
     * Authenticates, if needed the user with the information from the given request.
     * 
     * @param request
     *            The request to obtain the credentials from, cannot be <code>null</code>.
     * @return <code>true</code> if the authentication was successful, <code>false</code> otherwise.
     */
    private boolean authenticate(HttpServletRequest request) {
        if (m_useAuth) {
            User user = m_authenticationService.authenticate(request);

            if (user == null) {
                m_log.log(LogService.LOG_INFO, "Authentication failure!");
            }

            return (user != null);
        }

        return true;
    }

    
}
