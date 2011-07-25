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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

/**
 * Servlet that offers a REST client API.
 */
public class RESTClientServlet extends HttpServlet {
    private static final long serialVersionUID = 5210711248294238039L;
    /** Alias that redirects to the latest version automatically. */
    private static final String LATEST_FOLDER = "latest";
    /** Name of the folder where working copies are kept. */
    private static final String WORK_FOLDER = "work";
    
    private static long m_sessionID = 1;
    
    private volatile DependencyManager m_dm;
    private volatile SessionFactory m_sessionFactory;
    
    private final Map<String, Workspace> m_workspaces = new HashMap<String, Workspace>();
    private final Map<String, Component> m_workspaceComponents = new HashMap<String, Component>();
    private Gson m_gson;
    
    public RESTClientServlet() {
        m_gson = (new GsonBuilder())
            .registerTypeHierarchyAdapter(RepositoryObject.class, new RepositoryObjectSerializer())
            .create();
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements == null || pathElements.length == 0) {
            // TODO return a list of versions
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not implemented: list of versions");
            return;
        }
        else {
            if (pathElements.length == 1) {
                if (LATEST_FOLDER.equals(pathElements[0])) {
                    // TODO redirect to latest version
                    // resp.sendRedirect("notImplemented" /* to latest version */);
                    resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Not implemented: redirect to latest version");
                    return;
                }
            }
            else if (pathElements.length == 3) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    Workspace workspace = getWorkspace(pathElements[1]);
                    if (workspace != null) {
                        // TODO add a feature to filter the list that is returned (query, paging, ...)
                        List<RepositoryObject> objects = workspace.getRepositoryObjects(pathElements[2]);
                        JsonArray result = new JsonArray();
                        for (RepositoryObject ro : objects) {
                            result.add(new JsonPrimitive(URLEncoder.encode(ro.getAssociationFilter(null), "UTF-8")));
                        }
                        resp.getWriter().println(m_gson.toJson(result));
                        return;
                    }
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + pathElements[1]);
                    return;
                }
            }
            else if (pathElements.length == 4) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    Workspace workspace = getWorkspace(pathElements[1]);
                    if (workspace != null) {
                        String entityType = pathElements[2];
                        String entityId = pathElements[3];
                        RepositoryObject repositoryObject = workspace.getRepositoryObject(entityType, entityId);
                        if (repositoryObject == null) {
                            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Repository object of type " + entityType + " and identity " + entityId + " not found.");
                            return;
                        }
                        
                        resp.getWriter().println(m_gson.toJson(repositoryObject));
                        return;
                    }
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Could not find workspace: " + pathElements[1]);
                    return;
                }
            }
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements != null) {
            if (pathElements.length == 1) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    // TODO get data from post body (if no data, assume latest??) -> for now always assume latest
                    String sessionID;
                    Workspace workspace;
                    Component component;
                    synchronized (m_workspaces) {
                        sessionID = "rest-" + m_sessionID++;
                        // TODO OBR with or without trailing slash?
                        // TODO this needs to come from configuration
                        workspace = new Workspace(sessionID, "http://localhost:8080/repository", "http://localhost:8080/obr", "apache", "shop", "gateway", "deployment", "d");
                        m_workspaces.put(sessionID, workspace);
                        component = m_dm.createComponent().setImplementation(workspace);
                        m_workspaceComponents.put(sessionID, component);
                    }
                    m_sessionFactory.createSession(sessionID);
                    m_dm.add(component);
                    resp.sendRedirect(buildPathFromElements(WORK_FOLDER, sessionID));
                    return;
                }
            }
            else if (pathElements.length == 2) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    Workspace workspace = getWorkspace(pathElements[1]);
                    if (workspace != null) {
                        try {
                            workspace.commit();
                            return;
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            resp.sendError(HttpServletResponse.SC_CONFLICT, "Commit failed: " + e.getMessage());
                            return;
                        }
                    }
                    else {
                        // return error
                        System.out.println("Failed...");
                    }
                }
            }
            else if (pathElements.length == 3) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    Workspace workspace = getWorkspace(pathElements[1]);
                    if (workspace != null) {
                        try {
                            RepositoryValueObject data = m_gson.fromJson(req.getReader(), RepositoryValueObject.class);
                            RepositoryObject object = workspace.addRepositoryObject(pathElements[2], data.attributes, data.tags);
                            resp.sendRedirect(buildPathFromElements(WORK_FOLDER, pathElements[1], pathElements[2], object.getAssociationFilter(null)));
                            return;
                        }
                        catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                    }
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not add entity of type " + pathElements[2]);
                    return;
                }
            }
        }
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements != null) {
            if (pathElements.length == 4) {
                if (pathElements[0].equals(WORK_FOLDER)) {
                    Workspace workspace = getWorkspace(pathElements[1]);
                    if (workspace != null) {
                        try {
                            RepositoryValueObject data = m_gson.fromJson(req.getReader(), RepositoryValueObject.class);
                            RepositoryObject object = workspace.getRepositoryObject(pathElements[2], pathElements[3]);
                            updateObjectWithData(object, data);
                            resp.sendRedirect(buildPathFromElements(WORK_FOLDER, pathElements[1], pathElements[2], pathElements[3]));
                            return;
                        }
                        catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                    }
                    resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not add entity of type " + pathElements[2]);
                    return;
                }
            }
        }
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String[] pathElements = getPathElements(req);
        if (pathElements != null) {
            if (pathElements.length == 2) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    String id = pathElements[1];
                    Workspace workspace;
                    Component component;
                    synchronized (m_workspaces) {
                        workspace = m_workspaces.remove(id);
                        component = m_workspaceComponents.remove(id);
                    }
                    if (workspace != null && component != null) {
                        // TODO delete the work area
                        m_dm.remove(component);
                        m_sessionFactory.destroySession(id);
                        return;
                    }
                    else {
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not delete work area.");
                        return;
                    }
                }
            }
            else if (pathElements.length == 4) {
                if (WORK_FOLDER.equals(pathElements[0])) {
                    String id = pathElements[1];
                    String entityType = pathElements[2];
                    String entityId = pathElements[3];
                    
                    Workspace workspace;
                    Component component;
                    synchronized (m_workspaces) {
                        workspace = m_workspaces.get(id);
                    }
                    if (workspace != null) {
                        workspace.deleteRepositoryObject(entityType, entityId);
                        return;
                    }
                    else {
                        resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not find work area.");
                        return;
                    }
                    
                }
            }
        }
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        return;
    }

    private Workspace getWorkspace(String id) {
        Workspace workspace;
        synchronized (m_workspaces) {
            workspace = m_workspaces.get(id);
        }
        return workspace;
    }
    
    /**
     * Builds a URL path from the supplied elements. Each individual element is URL encoded.
     * 
     * @param elements the elements
     * @return the URL path
     */
    private String buildPathFromElements(String... elements) {
        StringBuilder result = new StringBuilder();
        for (String element : elements) {
            if (result.length() > 0) {
                result.append('/');
            }
            try {
                result.append(URLEncoder.encode(element, "UTF-8"));
            }
            catch (UnsupportedEncodingException e) {} // ignored on purpose, any JVM must support UTF-8
        }
        return result.toString();
    }

    /**
     * Returns the separate path parts from the request, and URL decodes them.
     * 
     * @param req the request
     * @return the separate path parts
     */
    private String[] getPathElements(HttpServletRequest req) {
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
        try {
            for (int i = 0; i < pathElements.length; i++) {
                pathElements[i] = URLDecoder.decode(pathElements[i], "UTF-8");
            }
        }
        catch (UnsupportedEncodingException e) {}
        return pathElements;
    }

    private void updateObjectWithData(RepositoryObject repositoryObject, RepositoryValueObject valueObject) {
        // first handle the attributes
        for (Entry<String, String> attribute : valueObject.attributes.entrySet()) {
            String key = attribute.getKey();
            String value = attribute.getValue();
            // only add/update the attribute if it actually changed
            if (!value.equals(repositoryObject.getAttribute(key))) {
                repositoryObject.addAttribute(key, value);
            }
        }
        Enumeration<String> keys = repositoryObject.getAttributeKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!valueObject.attributes.containsKey(key)) {
                // TODO since we cannot remove keys right now, we null them
                repositoryObject.addAttribute(key, null);
            }
        }
        // now handle the tags in a similar way
        for (Entry<String, String> attribute : valueObject.tags.entrySet()) {
            String key = attribute.getKey();
            String value = attribute.getValue();
            // only add/update the tag if it actually changed
            if (!value.equals(repositoryObject.getTag(key))) {
                repositoryObject.addTag(key, value);
            }
        }
        keys = repositoryObject.getTagKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (!valueObject.tags.containsKey(key)) {
                // TODO since we cannot remove keys right now, we null them
                repositoryObject.addTag(key, null);
            }
        }
    }
}
