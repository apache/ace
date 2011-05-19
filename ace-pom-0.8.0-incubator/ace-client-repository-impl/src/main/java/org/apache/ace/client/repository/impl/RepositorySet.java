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
package org.apache.ace.client.repository.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.SessionFactory;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.apache.ace.repository.ext.CachedRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.useradmin.User;

/**
 * This class encapsulates a set of <code>ObjectRepositoryImpl</code>s, and manages
 * auxiliary information and functionality that is linked to that set.
 */
class RepositorySet {
    private final static String PREFS_LOCAL_WORKING_STATE = "workingState";
    private final static String PREFS_LOCAL_WORKING_STATE_VALUE = "workingStateValue";
    private final static String PREFS_LOCAL_FILE_VERSION = "version";

    private final User m_user;
    private final Preferences m_prefs;
    @SuppressWarnings("unchecked")
    private final ObjectRepositoryImpl[] m_repos;
    private final CachedRepository m_repository;
    private final String m_name;
    private final boolean m_writeAccess;

    private final Map<RepositoryObject, WorkingState> m_workingState = new HashMap<RepositoryObject, WorkingState>();
    private ServiceRegistration m_modifiedHandler;
    private final ChangeNotifier m_notifier;
    private final LogService m_log;

    /* ********
     * Basics
     * ********/

    @SuppressWarnings("unchecked")
    /**
     * Creates a new <code>RepositorySet</code>. Notes:
     * <ul>
     * <li>When storing association repositories in <code>repos</code>, it is wise to
     * put these in last. This has to do with the method of deserialization, which assumes
     * that endpoints of an association are available at the time of deserialization.</li>
     * </ul>
     */
    RepositorySet(ChangeNotifier notifier, LogService log, User user, Preferences prefs, ObjectRepositoryImpl[] repos, CachedRepository repository, String name, boolean writeAccess) {
        m_notifier = notifier;
        m_log = log;
        m_user = user;
        m_prefs = prefs;
        m_repos = repos;
        m_repository = repository;
        m_name = name;
        m_writeAccess = writeAccess;
    }

    boolean isModified() {
        boolean modified = false;
        for (Map.Entry<RepositoryObject, WorkingState> entry : m_workingState.entrySet()) {
            modified |= !(entry.getValue().equals(WorkingState.Unchanged));
        }
        return modified;
    }

    User getUser() {
        return m_user;
    }

    @SuppressWarnings("unchecked")
    ObjectRepositoryImpl[] getRepos() {
        return m_repos;
    }

    String getName() {
        return m_name;
    }

    /* ********
     * Preferences
     * ********/

    void savePreferences() {
        Preferences workingNode = m_prefs.node(PREFS_LOCAL_WORKING_STATE);
        try {
            workingNode.clear();
        }
        catch (BackingStoreException e) {
            // Something went wrong clearing the node... Too bad, this means we
            // cannot store the properties.
            m_log.log(LogService.LOG_WARNING, "Could not store all preferences for " + workingNode.absolutePath());
            e.printStackTrace();
        }
        for (Map.Entry<RepositoryObject, WorkingState> entry : m_workingState.entrySet()) {
            workingNode.node(entry.getKey().getDefinition()).put(PREFS_LOCAL_WORKING_STATE_VALUE, entry.getValue().toString());
        }

        m_prefs.putLong(PREFS_LOCAL_FILE_VERSION, m_repository.getMostRecentVersion());
    }

    /**
     * Only call this after the repository has been deserialized.
     */
    void loadPreferences() {
        Preferences workingNode = m_prefs.node(PREFS_LOCAL_WORKING_STATE);
        Map<String, WorkingState> entries = new HashMap<String, WorkingState>();
        // First, get all nodes and their workingstate.
        try {
            for (String node : workingNode.childrenNames()) {
                String state = workingNode.node(node).get(PREFS_LOCAL_WORKING_STATE_VALUE, WorkingState.Unchanged.toString());
                entries.put(node, WorkingState.valueOf(state));
            }
        }
        catch (BackingStoreException e) {
            // Something went wrong reading from the store, just work with whatever we have in the map.
            e.printStackTrace();
        }
        // Then, go through all objects and check whether they match a definition we know.
        // This prevents calling getDefinition more than once per object.
        for (ObjectRepository<RepositoryObject> repo : m_repos) {
            for (RepositoryObject o : repo.get()) {
                WorkingState state = entries.get(o.getDefinition());
                if (state != null) {
                    m_workingState.put(o, state);
                }
            }
        }
    }

    /* ********
     * Persistence
     * ********/

    boolean readLocal() throws IOException {
        InputStream input = m_repository.getLocal(false);
        if (input.available() > 0) {
            read(input);
            return true;
        }
        else {
            try {
                input.close();
            }
            catch (IOException e) {
                // This does not matter now.
            }
            return false;
        }
    }

    void read(InputStream input) {
        new RepositorySerializer(this).fromXML(input);
        try {
            input.close();
        }
        catch (IOException e) {
            // Not much we can do...
            e.printStackTrace();
        }
    }

    void writeLocal() throws IOException {
        PipedInputStream input = new PipedInputStream();
        final PipedOutputStream output = new PipedOutputStream(input);
        new Thread(new Runnable() {
            public void run() {
                new RepositorySerializer(RepositorySet.this).toXML(output);
                try {
                    output.flush();
                    output.close();
                }
                catch (IOException e) {
                    // There is no way to tell this to the user, but the other side will
                    // notice that the pipe is broken.
                }
            }
        }, "write(" + m_name + ")").start();
        m_repository.writeLocal(input);
        input.close();
    }

    void commit() throws IOException {
        if (!isCurrent()) {
            throw new IllegalStateException("When committing the " + m_name + ", it should be current.");
        }
        writeLocal();
        if (m_writeAccess) {
            m_repository.commit();
        }
        resetModified(false);
    }

    void checkout() throws IOException {
        read(m_repository.checkout(false));
        resetModified(true);
    }

    void revert() throws IOException {
        m_repository.revert();
        read(m_repository.getLocal(false));
        resetModified(false);
    }

    boolean isCurrent() throws IOException {
        return m_repository.isCurrent();
    }

    @SuppressWarnings("unchecked")
    void clearRepositories() {
        for (ObjectRepositoryImpl repo : getRepos()) {
            repo.setBusy(true);
        }
        for (ObjectRepositoryImpl repo : getRepos()) {
            repo.removeAll();
        }
        for (ObjectRepositoryImpl repo : getRepos()) {
            repo.setBusy(false);
        }
    }

    /* ********
     * Event handling
     * ********/

    void registerHandler(BundleContext context, String sessionID, String... topics) {
        if (m_modifiedHandler != null) {
            throw new IllegalStateException("A handler is already registered; only one can be used at a time.");
        }
        Dictionary topic = new Hashtable();
        topic.put(EventConstants.EVENT_TOPIC, topics);
        topic.put(EventConstants.EVENT_FILTER, "(" + SessionFactory.SERVICE_SID + "=" + sessionID + ")");
        m_modifiedHandler = context.registerService(EventHandler.class.getName(), new ModifiedHandler(), topic);
    }

    public void unregisterHandler() {
        m_modifiedHandler.unregister();
        m_modifiedHandler = null;
    }

    WorkingState getWorkingState(RepositoryObject object) {
        if (m_workingState.containsKey(object)) {
            return m_workingState.get(object);
        }
        return null;
    }

    int getNumberWithWorkingState(Class<? extends RepositoryObject> clazz, WorkingState state) {
        int result = 0;
        for (Map.Entry<RepositoryObject, WorkingState> entry : m_workingState.entrySet()) {
            if (clazz.isInstance(entry.getKey()) && state.equals(entry.getValue())) {
                result ++;
            }
        }
        return result;
    }

    private void resetModified(boolean fill) {
        m_workingState.clear();
        if (fill) {
            for (ObjectRepository<? extends RepositoryObject> repo : m_repos) {
                for (RepositoryObject object : repo.get()) {
                    m_workingState.put(object, WorkingState.Unchanged);
                }
            }
        }
        m_notifier.notifyChanged(RepositoryAdmin.TOPIC_STATUSCHANGED_SUFFIX, null);
    }

    class ModifiedHandler implements EventHandler {

        public void handleEvent(Event event) {
            /*
             * NOTE: if recalculating the state for every event turns out to be
             * too expensive, we can cache the 'modified' state and not recalculate
             * it every time.
             */

            boolean wasModified = isModified();
            RepositoryObject object = (RepositoryObject) event.getProperty(RepositoryObject.EVENT_ENTITY);

            WorkingState newState = WorkingState.Unchanged;
            if (event.getTopic().endsWith("/ADDED")) {
                newState = WorkingState.New;
            }
            else if (event.getTopic().endsWith("/CHANGED")) {
                newState = WorkingState.Changed;
            }
            else if (event.getTopic().endsWith("/REMOVED")) {
                newState = WorkingState.Removed;
            }

            if (!newState.equals(m_workingState.get(object))) {
                m_workingState.put(object, newState);
                Properties props = new Properties();
                props.put(RepositoryObject.EVENT_ENTITY, object);
                m_notifier.notifyChanged(RepositoryAdmin.TOPIC_STATUSCHANGED_SUFFIX, props);
            }

            if (!wasModified) {
                m_notifier.notifyChanged(RepositoryAdmin.TOPIC_STATUSCHANGED_SUFFIX, null);
            }
        }
    }

    public boolean writeAccess() {
        return m_writeAccess;
    }
}
