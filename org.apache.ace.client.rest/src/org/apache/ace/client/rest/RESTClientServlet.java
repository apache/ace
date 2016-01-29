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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.client.workspace.Workspace;
import org.apache.ace.client.workspace.WorkspaceManager;
import org.apache.ace.feedback.Event;
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
public class RESTClientServlet extends HttpServlet implements ManagedService, HttpSessionListener {
	private static final String SESSION_KEY_WORKSPACES = "workspaces";
	/** Timeout in seconds for REST sessions. */
    private static final String KEY_SESSION_TIMEOUT = "session.timeout";
    private static final int DEFAULT_SESSION_TIMEOUT = 300; // in seconds.
    
    /** Alias that redirects to the latest version automatically. */
    private static final String LATEST_FOLDER = "latest";
    /** Name of the folder where working copies are kept. */
    private static final String WORK_FOLDER = "work";

    /** The action name for approving targets. */
    private static final String ACTION_APPROVE = "approve";
    /** The action name for registering targets. */
    private static final String ACTION_REGISTER = "register";
    /** The action name for reading audit events. */
    private static final String ACTION_AUDITEVENTS = "auditEvents";

    private volatile LogService m_logger;

    private volatile WorkspaceManager m_workspaceManager;
    
    private volatile int m_sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    private final Gson m_gson;

    /**
     * Creates a new {@link RESTClientServlet} instance.
     */
    public RESTClientServlet() {
        m_gson = (new GsonBuilder())
            .registerTypeHierarchyAdapter(RepositoryObject.class, new RepositoryObjectSerializer())
            .registerTypeHierarchyAdapter(Event.class, new LogEventSerializer())
            .create();        
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
     * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	HttpSession session = getSession(req);
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length < 1 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        final String id = pathElements[1];

        Workspace workspace = m_workspaceManager.getWorkspace(id);
        if (workspace == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + id);
            return;
        }

        if (pathElements.length == 2) {
        	try {
            	Set<String> workspaces = (Set<String>) session.getAttribute(SESSION_KEY_WORKSPACES);
            	if (workspaces != null) {
            		workspaces.remove(workspace.getSessionID());
                	session.setAttribute(SESSION_KEY_WORKSPACES, workspaces);
            	}
        		m_workspaceManager.removeWorkspace(id);
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

	private HttpSession getSession(HttpServletRequest req) {
		HttpSession session = req.getSession(false);
		if (session == null) {
			session = req.getSession(true);
			session.setMaxInactiveInterval(m_sessionTimeout); // seconds
		}
		return session;
	}

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	HttpSession session = getSession(req);
        if (session == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
    	
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

            Workspace workspace = m_workspaceManager.getWorkspace(id);
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
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    	HttpSession session = getSession(req);
        String[] pathElements = getPathElements(req);
        
        if (pathElements == null || pathElements.length < 1 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (pathElements.length == 1) {
            Workspace workspace = m_workspaceManager.createWorkspace(req.getParameterMap(), req);
            if (workspace == null) {
                resp.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
            else {
            	Set<String> workspaces = (Set<String>) session.getAttribute(SESSION_KEY_WORKSPACES);
            	if (workspaces == null) {
            		workspaces = new HashSet<>();
            	}
            	workspaces.add(workspace.getSessionID());
            	session.setAttribute(SESSION_KEY_WORKSPACES, workspaces);
                resp.sendRedirect(req.getServletPath() + "/" + buildPathFromElements(WORK_FOLDER, workspace.getSessionID()));
            }
        }
        else {
            // more than one path elements...
            Workspace workspace = m_workspaceManager.getWorkspace(pathElements[1]);
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
                createRepositoryObject(workspace, pathElements[2], data, req, resp);
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
    	HttpSession session = getSession(req);
        if (session == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length != 4 || !WORK_FOLDER.equals(pathElements[0])) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Workspace workspace = m_workspaceManager.getWorkspace(pathElements[1]);
        if (workspace == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + pathElements[1]);
            return;
        }

        RepositoryValueObject data = getRepositoryValueObject(req);
        updateRepositoryObject(workspace, pathElements[2], pathElements[3], data, req, resp);
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
    private void createRepositoryObject(Workspace workspace, String entityType, RepositoryValueObject data, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            RepositoryObject object = workspace.createRepositoryObject(entityType, data.attributes, data.tags);

            resp.sendRedirect(req.getServletPath() + "/" + buildPathFromElements(WORK_FOLDER, workspace.getSessionID(), entityType, object.getDefinition()));
        }
        catch (IllegalArgumentException e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to add entity of type: " + entityType + " with data: " + data);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not add entity of type " + entityType + " with data: " + data);
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
            List<Event> events = target.getAuditEvents();

            String startValue = req.getParameter("start");
            String maxValue = req.getParameter("max");

            int start = (startValue == null) ? 0 : Integer.parseInt(startValue);
            // ACE-237: ensure the start-value is a correctly bounded positive integer...
            start = Math.max(0, Math.min(events.size() - 1, start));

            int max = (maxValue == null) ? 100 : Integer.parseInt(maxValue);
            // ACE-237: ensure the max- & end-values are correctly bounded...
            max = Math.max(1, max);

            int end = Math.min(events.size(), start + max);

            List<Event> selection = events.subList(start, end);
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
     * Updates an existing repository object.
     * 
     * @param workspace the workspace to update the repository object in;
     * @param entityType the type of repository object to update;
     * @param entityId the identifier of the repository object to update;
     * @param data the repository value object to use as content for the to-be-updated repository object;
     * @param resp the servlet response to write the response data to.
     * @throws IOException in case of I/O errors.
     */
    private void updateRepositoryObject(Workspace workspace, String entityType, String entityId, RepositoryValueObject data, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            workspace.updateRepositoryObject(entityType, entityId, data.attributes, data.tags);

            resp.sendRedirect(req.getServletPath() + "/" + buildPathFromElements(WORK_FOLDER, workspace.getSessionID(), entityType, entityId));
        }
        catch (IllegalArgumentException e) {
            m_logger.log(LogService.LOG_WARNING, "Failed to update entity of type: " + entityType + " with data: " + data, e);
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not update entity of type " + entityType + " with data: " + data);
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

    @Override
    public void updated(Dictionary<String, ?> properties) throws ConfigurationException {
    	if (properties == null) {
    		// defaults
    		m_sessionTimeout = DEFAULT_SESSION_TIMEOUT;
    	}
    	else {
    		try {
    			Object timeoutObject = properties.get(KEY_SESSION_TIMEOUT);
    			if (timeoutObject != null) {
	    			if (timeoutObject instanceof Integer) {
	    				m_sessionTimeout = (Integer) timeoutObject;
	    			}
	    			else {
	    				m_sessionTimeout = Integer.parseInt(timeoutObject.toString());
	    			}
	    			if (m_sessionTimeout < 1) {
	    				m_sessionTimeout = DEFAULT_SESSION_TIMEOUT;
	    				throw new ConfigurationException(KEY_SESSION_TIMEOUT, "Session timeout should be at least 1 second");
	    			}
    			}
    		}
    		catch (Exception e) {
    			throw new ConfigurationException(KEY_SESSION_TIMEOUT, "Could not parse timeout, it should either be a string or integer");
    		}
    	}
    }

	@Override
	public void sessionCreated(HttpSessionEvent e) {
	}

	@Override
    @SuppressWarnings("unchecked")
	public void sessionDestroyed(HttpSessionEvent e) {
		HttpSession session = e.getSession();
		if (session != null) {
			Set<String> workspaces = (Set<String>) session.getAttribute(SESSION_KEY_WORKSPACES);
			if (workspaces != null) {
				for (String id : workspaces) {
					try {
						m_workspaceManager.removeWorkspace(id);
					}
					catch (IOException ioe) {
						m_logger.log(LogService.LOG_WARNING, "Error while removing workspace after session timeout", ioe);
					}
				}
			}
		}
	}
}
