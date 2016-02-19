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

    private static final String KEY_CONTEXT_PATH = "context.path";
    
    private static final String DEFAULT_CONTEXT_PATH = "/";
    
    private volatile Component m_component;
    
    public AceServletContextHelper(Bundle bundle) {
        super(bundle);
    }
    
    public void updated(Dictionary<String, ?> settings) throws ConfigurationException {
        if (settings != null) {
            String contextPath = (String) settings.get(KEY_CONTEXT_PATH);
            if (contextPath == null) {
                contextPath = DEFAULT_CONTEXT_PATH;
            } else if (!contextPath.equals(DEFAULT_CONTEXT_PATH) && (!contextPath.startsWith("/") || contextPath.endsWith("/"))) {
                throw new ConfigurationException(KEY_CONTEXT_PATH, "Invalid value context path, context path should start with a '/' and NOT end with a '/'!");
            } 
            
            updateContextPath(contextPath);
        }
        else {
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
    
}
