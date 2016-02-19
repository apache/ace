package org.apache.ace.webui.vaadin;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;

import java.net.URL;
import java.util.Dictionary;

import org.apache.felix.dm.Component;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.http.context.ServletContextHelper;

public class AceWebuiServletContextHelper extends ServletContextHelper implements ManagedService {
    
    private static final String RESOURCE_PATH = "/VAADIN";
    private static final String KEY_CONTEXT_PATH = "context.path";
    private static final String DEFAULT_CONTEXT_PATH = "/";
    
    private volatile Component m_component;
    private final Bundle m_bundle;
    
    public AceWebuiServletContextHelper(Bundle bundle) {
        super(bundle);
        m_bundle = bundle;
    }
    
    @Override
    public URL getResource(String name) {
        URL resource = null;
        // fix for ACE-156
        if (!name.startsWith("/")) {
            name = "/".concat(name);
        }

        String prefix = RESOURCE_PATH.concat("/");
        if (name.startsWith(prefix)) {
            String originalName = name.replace("/ace/", "/reindeer/");
            
            resource = m_bundle.getEntry(originalName);
            if (resource == null) {
                // try to find the resource in the Vaadin bundle instead
                resource = com.vaadin.Application.class.getResource(originalName);
            }
        }
        return resource;
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
