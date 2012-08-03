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
package org.apache.ace.client.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.log.LogEvent;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;

/**
 * Servlet that offers a REST client API.
 */
public class RESTClientServlet extends HttpServlet implements ManagedService {
    
    /** Alias that redirects to the latest version automatically. */
    private static final String LATEST_FOLDER = "latest";
    /** Name of the folder where working copies are kept. */
    private static final String WORK_FOLDER = "work";
    /** A boolean denoting whether or not authentication is enabled. */
    private static final String KEY_USE_AUTHENTICATION = "authentication.enabled";
    /** URL of the repository to talk to. */
    private static final String KEY_REPOSITORY_URL = "repository.url";
    /** URL of the OBR to talk to. */
    private static final String KEY_OBR_URL = "obr.url";
    /** Name of the customer. */
    private static final String KEY_CUSTOMER_NAME = "customer.name";
    /** Name of the store repository. */
    private static final String KEY_STORE_REPOSITORY_NAME = "store.repository.name";
    /** Name of the distribution repository. */
    private static final String KEY_DISTRIBUTION_REPOSITORY_NAME = "distribution.repository.name";
    /** Name of the deployment repository. */
    private static final String KEY_DEPLOYMENT_REPOSITORY_NAME = "deployment.repository.name";
    /** Name of the user to log in as, in case no actual authentication is used. */
    private static final String KEY_USER_NAME = "user.name";
    /** The action name for approving targets. */
    private static final String ACTION_APPROVE = "approve";
    /** The action name for registering targets. */
    private static final String ACTION_REGISTER = "register";
    /** The action name for reading audit events. */
    private static final String ACTION_AUDITEVENTS = "auditEvents";

    private static long m_sessionID = 1;

    private volatile LogService m_logger;
    private volatile DependencyManager m_dm;
    private volatile SessionFactory m_sessionFactory;

    private final Map<String, Workspace> m_workspaces;
    private final Map<String, Component> m_workspaceComponents;
    private final Gson m_gson;
    
    private boolean m_useAuthentication;
    private String m_repositoryURL;
    private String m_obrURL;
    private String m_customerName;
    private String m_storeRepositoryName;
    private String m_targetRepositoryName;
    private String m_deploymentRepositoryName;
    private String m_serverUser;

    /**
     * Creates a new {@link RESTClientServlet} instance.
     */
    public RESTClientServlet() {
        m_gson = (new GsonBuilder())
            .registerTypeHierarchyAdapter(RepositoryObject.class, new RepositoryObjectSerializer())
            .registerTypeHierarchyAdapter(LogEvent.class, new LogEventSerializer())
            .create();
        
        m_workspaces = new HashMap<String, Workspace>();
        m_workspaceComponents = new HashMap<String, Component>();
    }
    
    public void destroy() {
    	Set<String> keySet = m_workspaces.keySet();
    	if (!keySet.isEmpty()) {
    		String[] keys = keySet.toArray(new String[keySet.size()]);
    		for (String key : keys) {
    			try {
					removeWorkspace(key);
				}
    			catch (IOException e) {
    				m_logger.log(LogService.LOG_WARNING, "Could not properly remove workspace.", e);
				}
    		}
    	}
    }

    public void updated(Dictionary properties) throws ConfigurationException {
        // First check whether all mandatory configuration keys are available...
        String useAuth = getProperty(properties, KEY_USE_AUTHENTICATION);
        if (useAuth == null || !("true".equalsIgnoreCase(useAuth) || "false".equalsIgnoreCase(useAuth))) {
            throw new ConfigurationException(KEY_USE_AUTHENTICATION, "Missing or invalid value!");
        }

        // Note that configuration changes are only applied to new work areas, started after the
        // configuration was changed. No attempt is done to "fix" existing work areas, although we
        // might consider flushing/invalidating them.
        synchronized (m_workspaces) {
            m_useAuthentication = Boolean.valueOf(useAuth);
            m_repositoryURL = getProperty(properties, KEY_REPOSITORY_URL, "http://localhost:8080/repository");
            m_obrURL = getProperty(properties, KEY_OBR_URL, "http://localhost:8080/obr");
            m_customerName = getProperty(properties, KEY_CUSTOMER_NAME, "apache");
            m_storeRepositoryName = getProperty(properties, KEY_STORE_REPOSITORY_NAME, "shop");
            m_targetRepositoryName = getProperty(properties, KEY_DISTRIBUTION_REPOSITORY_NAME, "target");
            m_deploymentRepositoryName = getProperty(properties, KEY_DEPLOYMENT_REPOSITORY_NAME, "deployment");
            m_serverUser = getProperty(properties, KEY_USER_NAME, "d");
        }
    }

    /**
     * Builds a URL path from the supplied elements. Each individual element is URL encoded.
     * 
     * @param elements the elements
     * @return the URL path
     */
    String buildPathFromElements(String... elements) {
        StringBuilder result = new StringBuilder();
        for (String element : elements) {
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(urlEncode(element));
        }
        return result.toString();
    }

    /**
     * Returns the separate path parts from the request, and URL decodes them.
     * 
     * @param req the request
     * @return the separate path parts
     */
    String[] getPathElements(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null) {
            return new String[0];
        }
        if (path.startsWith("/") && path.length() > 1) {
            path = path.substring(1);
        }
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        
        String[] pathElements = path.split("/");
        for (int i = 0; i < pathElements.length; i++) {
            pathElements[i] = urlDecode(pathElements[i]);
        }
        
        return pathElements;
    }

    /**
     * Helper method to safely obtain a property value from the given dictionary.
     * 
     * @param properties the dictionary to retrieve the value from, can be <code>null</code>;
     * @param key the name of the property to retrieve, cannot be <code>null</code>;
     * @param defaultValue the default value to return in case the property does not exist, or the given dictionary was <code>null</code>.
     * @return a property value, can be <code>null</code>.
     */
    String getProperty(Dictionary properties, String key, String defaultValue) {
        String value = getProperty(properties, key);
        return (value == null) ? defaultValue : value; 
    }

    /**
     * Helper method to safely obtain a property value from the given dictionary.
     * 
     * @param properties the dictionary to retrieve the value from, can be <code>null</code>;
     * @param key the name of the property to retrieve, cannot be <code>null</code>.
     * @return a property value, can be <code>null</code>.
     */
    String getProperty(Dictionary properties, String key) {
        if (properties != null) {
            Object value = properties.get(key);
            if (value != null && value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    /**
     * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length < 1 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final String id = pathElements[1];

        Workspace workspace = getWorkspace(id);
        if (workspace == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + id);
            return;
        }

        if (pathElements.length == 2) {
        	try {
        		removeWorkspace(id);
        	}
        	catch (IOException ioe) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not delete work area: " + ioe.getMessage());
        	}
        }
        else if (pathElements.length == 4) {
            deleteRepositoryObject(workspace, pathElements[2], pathElements[3], resp);
        }
        else {
            // All other path lengths...
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length == 0) {
            // TODO return a list of versions
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not implemented: list of versions");
            return;
        }

        if (pathElements.length == 1) {
            if (LATEST_FOLDER.equals(pathElements[0])) {
                // TODO redirect to latest version
                // resp.sendRedirect("notImplemented" /* to latest version */);
                resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not implemented: redirect to latest version");
                return;
            }
            else {
                // All other paths...
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
        else {
            // path elements of length > 1...
            final String id = pathElements[1];

            Workspace workspace = getWorkspace(id);
            if (workspace == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + id);
                return;
            }

            if (pathElements.length == 2) {
                // TODO this should be the current set of repository objects?!
                JsonArray result = new JsonArray();
                result.add(new JsonPrimitive(Workspace.ARTIFACT));
                result.add(new JsonPrimitive(Workspace.ARTIFACT2FEATURE));
                result.add(new JsonPrimitive(Workspace.FEATURE));
                result.add(new JsonPrimitive(Workspace.FEATURE2DISTRIBUTION));
                result.add(new JsonPrimitive(Workspace.DISTRIBUTION));
                result.add(new JsonPrimitive(Workspace.DISTRIBUTION2TARGET));
                result.add(new JsonPrimitive(Workspace.TARGET));
                resp.getWriter().println(m_gson.toJson(result));
                return;
            }
            else if (pathElements.length == 3) {
                listRepositoryObjects(workspace, pathElements[2], resp);
            }
            else if (pathElements.length == 4) {
                readRepositoryObject(workspace, pathElements[2], pathElements[3], resp);
            }
            else if (pathElements.length == 5) {
                handleWorkspaceAction(workspace, pathElements[2], pathElements[3], pathElements[4], req, resp);
            }
            else {
                // All other path lengths...
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    /**
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length < 1 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (pathElements.length == 1) {
            createWorkspace(req, resp);
        }
        else {
            // more than one path elements...
            Workspace workspace = getWorkspace(pathElements[1]);
            if (workspace == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + pathElements[1]);
                return;
            }

            if (pathElements.length == 2) {
                // Possible commit of workspace...
                commitWorkspace(workspace, resp);
            }
            else if (pathElements.length == 3) {
                // Possible repository object creation...
                RepositoryValueObject data = getRepositoryValueObject(req);
                createRepositoryObject(workspace, pathElements[2], data, resp);
            }
            else if (pathElements.length == 5) {
                // Possible workspace action...
                performWorkspaceAction(workspace, pathElements[2], pathElements[3], pathElements[4], resp);
            }
            else {
                // All other path lengths...
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length != 4 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Workspace workspace = getWorkspace(pathElements[1]);
        if (workspace == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + pathElements[1]);
            return;
        }

        RepositoryValueObject data = getRepositoryValueObject(req);
        updateRepositoryObject(workspace, pathElements[2], pathElements[3], data, resp);
    }

    /**
     * Commits the given workspace.
     * 
     * @param workspace the workspace to commit;
     * @param resp the servlet repsonse to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void commitWorkspace(Workspace workspace, HttpServletResponse resp) throws IOException {
        try {
            workspace.commit();
        }
        catch (Exception e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to commit workspace!", e);
            resp.sendError(HttpServletResponse.SC_CONFLICT, "Commit failed: " + e.getMessage());
        }
    }

    /**
     * Creates a new repository object.
     * 
     * @param workspace the workspace to create the new repository object in;
     * @param entityType the type of repository object to create;
     * @param data the repository value object to use as content for the to-be-created repository object;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void createRepositoryObject(Workspace workspace, String entityType, RepositoryValueObject data, HttpServletResponse resp) throws IOException {
        try {
            RepositoryObject object = workspace.addRepositoryObject(entityType, data.attributes, data.tags);

            resp.sendRedirect(buildPathFromElements(WORK_FOLDER, workspace.getSessionID(), entityType, object.getDefinition()));
        }
        catch (IllegalArgumentException e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to add entity of type: " + entityType, e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not add entity of type " + entityType);
        }
    }

    /**
     * Creates a new workspace.
     * 
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void createWorkspace(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        // TODO get data from post body (if no data, assume latest??) -> for now always assume latest
        final String sessionID;
        final Workspace workspace;
        final Component component;

        synchronized (m_workspaces) {
            sessionID = "rest-" + m_sessionID++;
            workspace = new Workspace(sessionID, m_repositoryURL, m_obrURL, m_customerName, m_storeRepositoryName, m_targetRepositoryName, m_deploymentRepositoryName, m_useAuthentication, m_serverUser);
            m_workspaces.put(sessionID, workspace);

            component = m_dm.createComponent().setImplementation(workspace);
            m_workspaceComponents.put(sessionID, component);
        }
        m_sessionFactory.createSession(sessionID);
        m_dm.add(component);

        if (!workspace.login(req)) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            resp.sendRedirect(buildPathFromElements(WORK_FOLDER, sessionID));
        }
    }

    /**
     * Deletes a repository object from the current workspace.
     * 
     * @param workspace the workspace to perform the action for;
     * @param entityType the type of entity to apply the action to;
     * @param entityId the identification of the entity to apply the action to;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void deleteRepositoryObject(Workspace workspace, String entityType, String entityId, HttpServletResponse resp) throws IOException {
        try {
            workspace.deleteRepositoryObject(entityType, entityId);
        }
        catch (IllegalArgumentException e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to delete repository object!", e);
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Repository object of type " + entityType + " and identity " + entityId + " not found.");
        }
    }

    /**
     * Interprets the given request-data as JSON-data and converts it to a {@link RepositoryValueObject} instance.
     * 
     * @param request the servlet request data to interpret;
     * @return a {@link RepositoryValueObject} representation of the given request-data, never <code>null</code>.
     * @throws IOException in case of I/O errors, or in case the JSON parsing failed.
     */
    private RepositoryValueObject getRepositoryValueObject(HttpServletRequest request) throws IOException {
        try {
            return m_gson.fromJson(request.getReader(), RepositoryValueObject.class);
        }
        catch (JsonParseException e) {
            m_logger.log(LogService.LOG_WARNING, "Invalid repository object data!", e);
            throw new IOException("Unable to parse repository object!", e);
        }
    }

    /**
     * Returns a workspace by its identification.
     * 
     * @param id the (session) identifier of the workspace to return.
     * @return the workspace with requested ID, or <code>null</code> if no such workspace exists.
     */
    private Workspace getWorkspace(String id) {
        Workspace workspace;
        synchronized (m_workspaces) {
            workspace = m_workspaces.get(id);
        }
        return workspace;
    }

    /**
     * Performs an idempotent action on an repository object for the given workspace.
     * 
     * @param workspace the workspace to perform the action for;
     * @param entityType the type of entity to apply the action to;
     * @param entityId the identification of the entity to apply the action to;
     * @param action the (name of the) action to apply;
     * @param req the servlet request to read the request data from;
     * @param resp the servlet response to write the response data to.
     * @throws IOException
     */
    private void handleWorkspaceAction(Workspace workspace, String entityType, String entityId, String action, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RepositoryObject repositoryObject = workspace.getRepositoryObject(entityType, entityId);
        if (repositoryObject == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Repository object of type " + entityType + " and identity " + entityId + " not found.");
            return;
        }

        if (Workspace.TARGET.equals(entityType) && ACTION_APPROVE.equals(action)) {
            resp.getWriter().println(m_gson.toJson(((StatefulTargetObject) repositoryObject).getStoreState()));
        }
        else if (Workspace.TARGET.equals(entityType) && ACTION_REGISTER.equals(action)) {
            resp.getWriter().println(m_gson.toJson(((StatefulTargetObject) repositoryObject).getRegistrationState()));
        }
        else if (Workspace.TARGET.equals(entityType) && ACTION_AUDITEVENTS.equals(action)) {
            StatefulTargetObject target = (StatefulTargetObject) repositoryObject;
            List<LogEvent> events = target.getAuditEvents();

            String startValue = req.getParameter("start");
            String maxValue = req.getParameter("max");

            int start = (startValue == null) ? 0 : Integer.parseInt(startValue);
            // ACE-237: ensure the start-value is a correctly bounded positive integer...
            start = Math.max(0, Math.min(events.size() - 1, start));

            int max = (maxValue == null) ? 100 : Integer.parseInt(maxValue);
            // ACE-237: ensure the max- & end-values are correctly bounded...
            max = Math.max(1, max);

            int end = Math.min(events.size(), start + max);

            List<LogEvent> selection = events.subList(start, end);
            resp.getWriter().println(m_gson.toJson(selection));
        }
        else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action for " + entityType);
        }
    }

    /**
     * Returns the identifiers of all repository objects of a given type.
     * 
     * @param workspace the workspace to read the repository objects from;
     * @param entityType the type of repository objects to read;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O problems.
     */
    private void listRepositoryObjects(Workspace workspace, String entityType, HttpServletResponse resp) throws IOException {
        // TODO add a feature to filter the list that is returned (query, paging, ...)
        List<RepositoryObject> objects = workspace.getRepositoryObjects(entityType);

        JsonArray result = new JsonArray();
        for (RepositoryObject ro : objects) {
            String identity = ro.getDefinition();
            if (identity != null) {
                result.add(new JsonPrimitive(urlEncode(identity)));
            }
        }

        resp.getWriter().println(m_gson.toJson(result));
    }

    /**
     * Performs a non-idempotent action on an repository object for the given workspace.
     * 
     * @param workspace the workspace to perform the action for;
     * @param entityType the type of entity to apply the action to;
     * @param entityId the identification of the entity to apply the action to;
     * @param action the (name of the) action to apply;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void performWorkspaceAction(Workspace workspace, String entityType, String entityId, String action, HttpServletResponse resp) throws IOException {
        RepositoryObject repositoryObject = workspace.getRepositoryObject(entityType, entityId);
        if (repositoryObject == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Repository object of type " + entityType + " and identity " + entityId + " not found.");
            return;
        }

        if (Workspace.TARGET.equals(entityType) && ACTION_APPROVE.equals(action)) {
            StatefulTargetObject sto = workspace.approveTarget((StatefulTargetObject) repositoryObject);

            // Respond with the current store state...
            resp.getWriter().println(m_gson.toJson(sto.getStoreState()));
        }
        else if (Workspace.TARGET.equals(entityType) && ACTION_REGISTER.equals(action)) {
            StatefulTargetObject sto = workspace.registerTarget((StatefulTargetObject) repositoryObject);
            if (sto == null) {
                resp.sendError(HttpServletResponse.SC_CONFLICT, "Target already registered: " + entityId);
            }
            else {
                // Respond with the current registration state...
                resp.getWriter().println(m_gson.toJson(sto.getRegistrationState()));
            }
        }
        else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action for " + entityType);
        }
    }

    /**
     * Reads a single repository object and returns a JSON representation of it.
     * 
     * @param workspace the workspace to read the repository object from;
     * @param entityType the type of repository object to read;
     * @param entityId the identifier of the repository object to read;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O problems.
     */
    private void readRepositoryObject(Workspace workspace, String entityType, String entityId, HttpServletResponse resp) throws IOException {
        RepositoryObject repositoryObject = workspace.getRepositoryObject(entityType, entityId);
        if (repositoryObject == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Repository object of type " + entityType + " and identity " + entityId + " not found.");
        }
        else {
            resp.getWriter().println(m_gson.toJson(repositoryObject));
        }
    }

    /**
     * Removes the workspace with the given identifier.
     * 
     * @param id the identifier of the workspace to remove; 
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O problems.
     */
    private void removeWorkspace(final String id) throws IOException {
        final Workspace workspace;
        final Component component;

        synchronized (m_workspaces) {
            workspace = m_workspaces.remove(id);
            component = m_workspaceComponents.remove(id);
        }

        if ((workspace != null) && (component != null)) {
            try {
                workspace.logout();
            }
            finally {
                m_dm.remove(component);
                m_sessionFactory.destroySession(id);
            }
        }
    }

    /**
     * Updates an existing repository object.
     * 
     * @param workspace the workspace to update the repository object in;
     * @param entityType the type of repository object to update;
     * @param entityId the identifier of the repository object to update;
     * @param data the repository value object to use as content for the to-be-updated repository object;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void updateRepositoryObject(Workspace workspace, String entityType, String entityId, RepositoryValueObject data, HttpServletResponse resp) throws IOException {
        try {
            workspace.updateObjectWithData(entityType, entityId, data);

            resp.sendRedirect(buildPathFromElements(WORK_FOLDER, workspace.getSessionID(), entityType, entityId));
        }
        catch (IllegalArgumentException e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to update entity of type: " + entityType, e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not update entity of type " + entityType);
        }
    }

    /**
     * URL decodes a given element.
     * 
     * @param element the element to decode, cannot be <code>null</code>.
     * @return the decoded element, never <code>null</code>.
     */
    private String urlDecode(String element) {
        try {
            return URLDecoder.decode(element.replaceAll("%20", "\\+"), "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // ignored on purpose, any JVM must support UTF-8
            return null; // should never occur
        }
    }

    /**
     * URL encodes a given element.
     * 
     * @param element the element to encode, cannot be <code>null</code>.
     * @return the encoded element, never <code>null</code>.
     */
    private String urlEncode(String element) {
        try {
            return URLEncoder.encode(element, "UTF-8").replaceAll("\\+", "%20");
        }
        catch (UnsupportedEncodingException e) {
            // ignored on purpose, any JVM must support UTF-8
            return null; // should never occur
        }
    }
}
