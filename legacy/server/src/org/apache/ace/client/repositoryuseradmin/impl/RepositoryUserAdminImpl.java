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
package org.apache.ace.client.repositoryuseradmin.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.ace.client.repositoryuseradmin.RepositoryUserAdmin;
import org.apache.ace.repository.Repository;
import org.apache.ace.repository.ext.CachedRepository;
import org.apache.ace.repository.impl.CachedRepositoryImpl;
import org.apache.ace.repository.impl.FilebasedBackupRepository;
import org.apache.ace.repository.impl.RemoteRepository;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.osgi.service.useradmin.Authorization;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * RepositoryUserAdminImpl can checkout, commit and revert a repository
 * containing user data. It uses XStream to read and write the data.
 */
public class RepositoryUserAdminImpl implements RepositoryUserAdmin {

    private static final String REPOSITORY_USER_ADMIN_PREFS = "repositoryUserAdminPrefs";
    private static final String PREFS_LOCAL_FILE_ROOT = "repositoryUserAdmin";
    private static final String PREFS_LOCAL_FILE_LOCATION = "FileLocation";
    private static final String PREFS_LOCAL_FILE_CURRENT = "current";
    private static final String PREFS_LOCAL_FILE_BACKUP = "backup";

    private volatile BundleContext m_context;
    private volatile LogService m_log;
    private volatile PreferencesService m_preferences;

    private final Map<String, RoleImpl> m_roles = new ConcurrentHashMap<String, RoleImpl>();
    private CachedRepository m_repository;
    /**
     * Lock to be used when making changes to m_repository.
     */
    private final Object m_repositoryLock = new Object();
    private Preferences m_repositoryPrefs;

    public void login(User user, URL repositoryLocation, String repositoryCustomer, String repositoryName) throws IOException {
        synchronized(m_repositoryLock) {
            // Create our own backup repository
            RemoteRepository remote = new RemoteRepository(repositoryLocation, repositoryCustomer, repositoryName);
            m_repositoryPrefs = getUserPrefs(user, repositoryLocation, repositoryCustomer, repositoryName);
            m_repository = getCachedRepositoryFromPreferences(user, remote);

            // Fill the store with any data that might be available locally
            try {
                read(m_repository.getLocal(true));
            }
            catch (IOException ioe) {
                // TODO why is this logged as an error when it occurs when there simply is no data?
                m_log.log(LogService.LOG_ERROR, "Error retrieving local data.", ioe);
            }
        }
    }

    public void logout(boolean force) throws IOException {
        // logout stores the data locally, ready for the next run
        synchronized(m_repositoryLock) {
            if (!force) {
                ensureLoggedin();
            }
            try {
                writeLocal();
            }
            catch (IOException ioe) {
                if (!force) {
                    throw ioe;
                }
            }
            catch (RuntimeException re) {
                if (!force) {
                    throw re;
                }
            }
            m_repository = null;
        }
    }

    public void checkout() throws IOException {
        synchronized(m_repositoryLock) {
            ensureLoggedin();
            read(m_repository.checkout(false));
            storeVersion();
        }
    }

    public void commit() throws IOException {
        synchronized(m_repositoryLock) {
            ensureLoggedin();
            // First write to the local store, and then commit it
            writeLocal();
            m_repository.commit();
            storeVersion();
        }
    }

    /**
     * Helper method to write out the contents of the RepositoryUserAdminImpl to
     * a repository. This method will create a new thread to do the writing, and
     * wait for the thread to be ready.
     * @throws IOException Thrown when either this thread, or the thread that is
     * started to do the writing, throws an exception.
     */
    private void writeLocal() throws IOException {
        PipedInputStream in = new PipedInputStream();
        final PipedOutputStream out = new PipedOutputStream(in);
        final Semaphore semaphore = new Semaphore(0);
        final Exception[] exceptions = new Exception[1];
        new Thread("RepositoryUserAdmin writer") {
            @Override
            public void run() {
                try {
                    write(out);
                }
                catch (IOException e) {
                    m_log.log(LogService.LOG_ERROR, "Error writing out contents of RepositoryAdminUser", e);
                    exceptions[0] = e;
                }
                catch (IllegalArgumentException iae) {
                    m_log.log(LogService.LOG_ERROR, "Error writing out contents of RepositoryAdminUser", iae);
                    exceptions[0] = iae;
                }
                semaphore.release();
            }
        }.start();
        m_repository.writeLocal(in);
        try {
            if (!semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                throw new IOException("Error writing the contents of RepositoryUserAdmin.");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (exceptions[0] != null) {
            if (exceptions[0] instanceof IOException) {
                throw (IOException) exceptions[0];
            }
            if (exceptions[0] instanceof RuntimeException) {
                throw (RuntimeException) exceptions[0];
            }
        }
    }

    public void revert() throws IOException {
        synchronized(m_repositoryLock) {
            ensureLoggedin();
            m_repository.revert();
            read(m_repository.getLocal(false));
        }
    }

    /**
     * Makes sure a user is logged in before 'stuff' can be done. Make sure the
     * calling thread is holding the m_repositoryLock.
     */
    private void ensureLoggedin() {
        if (m_repository == null) {
            throw new IllegalStateException("This operation requires a user to be logged in.");
        }
    }

    /**
     * Reads the content of the stream, and updates this service's
     * contents accordingly. The caller of this method should hold the
     * m_repositoryLock.
     */
    @SuppressWarnings("unchecked")
    private void read(InputStream input) {
        m_roles.clear();
        // We use DomDriver because the standard XPP driver has issues with attributes.
        XStream xstream = new XStream(/*new DomDriver()*/);
        xstream.registerConverter(ROLEMAPCONVERTER);
        xstream.registerConverter(ROLECONVERTER);
        xstream.registerConverter(DICTCONVERTER);
        xstream.aliasType("roles", Map.class);
        try {
            Map<String, RoleImpl> fromXML = (Map<String, RoleImpl>) xstream.fromXML(input);
            m_roles.putAll(fromXML);
        }
        catch (StreamException e) {
            // no problem: this means that the remote repository is empty.
        }
    }

    /**
     * Writes the current contents of this service.
     * The caller of this method should hold the m_repositoryLock.
     * @param out An output stream to write to. It will be closed by the this method.
     * @throws IOException When there is a problem creating the stream, or
     * the other end of the stream fails.
     */
    private void write(OutputStream out) throws IOException {
        XStream xstream = new XStream(new DomDriver());
        xstream.registerConverter(ROLEMAPCONVERTER);
        xstream.registerConverter(ROLECONVERTER);
        xstream.registerConverter(DICTCONVERTER);
        xstream.aliasType("roles", Map.class);
        xstream.toXML(m_roles, out);
        try {
            out.close();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "Error closing XStream output stream.", e);
            throw e;
        }
    }

    /**
     * Gets the preferences for a user/location/customer/name combination.
     */
    private Preferences getUserPrefs(User user, URL location, String customer, String name) {
        Preferences userPrefs = m_preferences.getUserPreferences(user.getName());
        Preferences userAdminPrefs = userPrefs.node(REPOSITORY_USER_ADMIN_PREFS);
        Preferences repoPref = userAdminPrefs.node(location.getAuthority() + location.getPath());
        Preferences customerPref = repoPref.node(customer);
        return customerPref.node(name);
    }

    /**
     * Creates a cached repository based on preferences.
     */
    private CachedRepository getCachedRepositoryFromPreferences(User user, Repository repository) throws IOException {
        long mostRecentVersion = m_repositoryPrefs.getLong("version", CachedRepositoryImpl.UNCOMMITTED_VERSION);
        File current = getFileFromPreferences(PREFS_LOCAL_FILE_CURRENT);
        File backup = getFileFromPreferences(PREFS_LOCAL_FILE_BACKUP);
        return new CachedRepositoryImpl(user, repository, new FilebasedBackupRepository(current, backup), mostRecentVersion);
    }

    /**
     * Writes the current version of the repository we are working on to the preferences.
     */
    private void storeVersion() {
        m_repositoryPrefs.putLong("version", m_repository.getMostRecentVersion());
    }

    /**
     * Gets a named file in preferences. If the file does not yet exist, it will
     * be created, and its location noted in the preferences.
     */
    private File getFileFromPreferences(String type) throws IOException {
        String directory = m_repositoryPrefs.get(PREFS_LOCAL_FILE_LOCATION, "");

        if ((directory == "") || !m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory()) {
            if (!m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory() && (directory != "")) {
                m_log.log(LogService.LOG_WARNING, "Directory '" + directory + "' should exist according to the preferences, but it does not.");
            }
            // The file did not exist, so create a new one.
            File directoryFile = null;
            File bundleDataDir = m_context.getDataFile(PREFS_LOCAL_FILE_ROOT);
            if (!bundleDataDir.isDirectory()) {
                if (!bundleDataDir.mkdir()) {
                    throw new IOException("Error creating the local repository root directory.");
                }
            }
            directoryFile = File.createTempFile("repo", "", bundleDataDir);

            directoryFile.delete(); // No problem if this goes wrong, it just means it wasn't there yet.
            if (!directoryFile.mkdir()) {
                throw new IOException("Error creating the local repository storage directory.");
            }
            m_repositoryPrefs.put(PREFS_LOCAL_FILE_LOCATION, directoryFile.getName());
            return new File(directoryFile, type);
        }
        else {
            // Get the given file from that location.
            return m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory + "/" + type);
        }
    }

    /* ******************************
     * The UserAdmin implementation *
     * ******************************/

    public Role createRole(String name, int type) {
        if ((type != Role.USER) && (type != Role.GROUP)) {
            throw new IllegalArgumentException("Type " + type + " is unknown.");
        }

        // event tough we have a ConcurrentHashMap, we still should make the checking for existence
        // and actual creation an atomic operation.
        synchronized (m_roles) {
            if (m_roles.containsKey(name)) {
                return null;
            }

            RoleImpl result = new RoleImpl(name, type);
            m_roles.put(name, result);
            return result;
        }
    }

    public Authorization getAuthorization(User user) {
        throw new UnsupportedOperationException("getAuthorization is not supported by RepositoryUserAdmin.");
    }

    public Role getRole(String name) {
        return m_roles.get(name);
    }

    public Role[] getRoles(String filter) throws InvalidSyntaxException {
        if (filter == null) {
            return m_roles.values().toArray(new Role[m_roles.size()]);
        }

        Filter f = m_context.createFilter(filter);

        List<Role> result = new ArrayList<Role>();
        for (RoleImpl impl : m_roles.values()) {
            if (f.match(impl.getProperties())) {
                result.add(impl);
            }
        }

        // The spec requires us to return null when we have no results.
        return result.size() > 0 ? result.toArray(new Role[result.size()]) : null;
    }

    public User getUser(String key, String value) {
        List<User> result = new ArrayList<User>();
        for (Role role : m_roles.values()) {
            if ((role.getType() == Role.USER) && value.equals(role.getProperties().get(key))) {
                result.add((User) role);
            }
        }

        return result.size() == 1 ? result.get(0) : null;
    }

    public boolean removeRole(String name) {
        RoleImpl role = m_roles.remove(name);
        if (role == null) {
            return false;
        }
        for (String groupName : role.getMemberships(this)) {
            RoleImpl group = m_roles.get(groupName);
            if (group != null) {
                group.removeMember(role);
            }
        }
        return true;
    }

    /* ***********************
     * Serialization helpers *
     * ***********************/

    /**
     * XStream Converter for a Dictionary, with support for both Strings and
     * byte[]'s as values. Resulting format:
     * <pre>
     * &lt;keyname1 type = "String"&gt;value1&lt;/keyname1&gt;
     * &lt;keyname1 type = "byte[]"&gt;value1&lt;/keyname1&gt;
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private static final Converter DICTCONVERTER = new Converter() {
        public void marshal(Object object, HierarchicalStreamWriter writer, MarshallingContext context) {
            Dictionary dict = (Dictionary) object;
            Enumeration e = dict.keys();
            while (e.hasMoreElements()) {
                String key = (String) e.nextElement();
                Object value = dict.get(key);
                writer.startNode(key);
                if (value instanceof String) {
                    writer.addAttribute("type", "String");
                    writer.setValue((String) value);
                }
                else if (value instanceof byte[]) {
                    writer.addAttribute("type", "byte[]");
                    writer.setValue(new String((byte[]) value));
                }
                else if (value == null) {
                    throw new IllegalArgumentException("Encountered a null value in the dictionary for key " + key);
                }
                else {
                    throw new IllegalArgumentException("The dictionary contains a non-recognized value " + value.getClass().getName() + " for key " + key);
                }
                writer.endNode();
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext converter) {
            Dictionary result = new Hashtable<String, Object>();
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                Object value;
                if ((reader.getAttribute("type") == null) || reader.getAttribute("type").equals("String")) {
                    value = reader.getValue();
                }
                else if (reader.getAttribute("type").equals("byte[]")) {
                    value = reader.getValue().getBytes();
                }
                else {
                    throw new IllegalArgumentException("Encountered an unknown type tag: " + reader.getAttribute("type"));
                }
                result.put(reader.getNodeName(), value);
                reader.moveUp();
            }
            return result;
        }

        public boolean canConvert(Class clazz) {
            return Dictionary.class.isAssignableFrom(clazz);
        }
    };

    /**
     * XStream convertor for RoleImpl objects. Resulting format:
     * <pre>
     * &lt;user name="me"&gt;
     *     &lt;properties&gt;
     *     ...up to DICTCONVERTER...
     *     &lt;/properties&gt;
     *     &lt;credentials&gt;
     *     ...up to DICTCONVERTER...
     *     &lt;/credentials&gt;
     *     &lt;memberof&gt;group1&lt;/memberof&gt;
     *     &lt;memberof&gt;group2&lt;/memberof&gt;
     * &lt;/user>
     * </pre>
     * This converter will use the context property 'deserialized' to find
     * groups that the currently deserialized entry should be a member of.
     */
    @SuppressWarnings("unchecked")
    private final Converter ROLECONVERTER = new Converter() {
        public void marshal(Object object, HierarchicalStreamWriter writer, MarshallingContext context) {
            RoleImpl role = (RoleImpl) object;

            if (role.getType() == Role.USER) {
                writer.startNode("user");
            }
            else {
                writer.startNode("group");
            }
            writer.addAttribute("name", role.getName());

            writer.startNode("properties");
            context.convertAnother(role.getProperties());
            writer.endNode();

            writer.startNode("credentials");
            context.convertAnother(role.getCredentials());
            writer.endNode();

            for (String s : role.getMemberships(RepositoryUserAdminImpl.this)) {
                writer.startNode("memberof");
                writer.setValue(s);
                writer.endNode();
            }

            writer.endNode();
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            int type;
            if (reader.getNodeName().equals("user")) {
                type = Role.USER;
            }
            else if (reader.getNodeName().equals("group")) {
                type = Role.GROUP;
            }
            else {
                throw new IllegalArgumentException("Encountered an unknown node name: " + reader.getNodeName());
            }

            RoleImpl result = new RoleImpl(reader.getAttribute("name"), type);

            while (reader.hasMoreChildren()) {
                reader.moveDown();
                if (reader.getNodeName().equals("properties")) {
                    copyDict(result.getProperties(), (Dictionary<String, Object>) context.convertAnother(reader, Dictionary.class));
                }
                else if (reader.getNodeName().equals("credentials")) {
                    copyDict(result.getCredentials(), (Dictionary<String, Object>) context.convertAnother(reader, Dictionary.class));
                }
                else if (reader.getNodeName().equals("memberof")) {
                    ((Map<String, RoleImpl>) context.get("deserialized")).get(reader.getValue()).addMember(result);
                }
                reader.moveUp();
            }

            return result;
        }

        /**
         * Helper method that copies the contents of one dictionary to another.
         */
        private void copyDict(Dictionary to, Dictionary from) {
            Enumeration<String> e = from.keys();
            while (e.hasMoreElements()) {
                String key = e.nextElement();
                to.put(key, from.get(key));
            }
        }

        public boolean canConvert(Class clazz) {
            return RoleImpl.class.isAssignableFrom(clazz);
        }
    };

    /**
     * XStream converter for a Map which contains Roles. Resulting format:
     * <pre>
     * &lt;roles&gt;
     *     ...up to ROLECONVERTER...
     *     ...up to ROLECONVERTER...
     * &lt;/roles&gt;
     * </pre>
     * This converter will use the 'deserialized' context property to store the map
     * of already deserialized roles, so ROLECONVERTER can use that.<br>
     * Furthermore, it uses a simple form of cycle detection when serializing.
     */
    private final Converter ROLEMAPCONVERTER = new Converter() {

        @SuppressWarnings("unchecked")
        public void marshal(Object object, HierarchicalStreamWriter writer, MarshallingContext context) {
            Map<String, RoleImpl> todo = new HashMap<String, RoleImpl>();
            todo.putAll(((Map) object));

            /*
             * We only serialize roles that have no dependencies on roles that have not yet been
             * serialized. To do so, we check all dependencies of a role, and see whether any of these
             * still has to be serialized. If so, we skip that role for now, and try to serialize it
             * in a later run. We go over the list a number of times, until it stops shrinking.
             */
            int removed = 1;
            while (removed != 0) {
                // We need to store the elements we have handled separately: we cannot remove them from todo directly.
                Set<String> done = new HashSet<String>();
                for (RoleImpl role : todo.values()) {
                    String[] memberships = role.getMemberships(RepositoryUserAdminImpl.this);
                    if (!contains(memberships, todo.keySet())) {
                        context.convertAnother(role);
                        done.add(role.getName());
                    }
                }
                for (String s : done) {
                    todo.remove(s);
                }
                removed = done.size();
            }
            if (!todo.isEmpty()) {
                // removed has to be 0, so no elements have been removed from todo in the previous run. However,
                // if todo now is not empty, we know we have a circular dependency.
                throw new IllegalArgumentException("The role tree contains a circular dependency, and cannot be serialized.");
            }
        }

        /**
         * @return <code>false</code> if none of the elements from subset appear in
         * set, <code>true</code> otherwise.
         */
        private boolean contains(String[] subset, Set<String> set) {
            for (String s : subset) {
                if (set.contains(s)) {
                    return true;
                }
            }
            return false;
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Map<String, RoleImpl> result = new HashMap<String, RoleImpl>();
            context.put("deserialized", result);
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                RoleImpl role = (RoleImpl) context.convertAnother(reader, RoleImpl.class);
                result.put(role.getName(), role);
                reader.moveUp();
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        public boolean canConvert(Class clazz) {
            return Map.class.isAssignableFrom(clazz);
        }
    };

}
