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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.ace.client.repository.ObjectRepository;
import org.apache.ace.client.repository.RepositoryObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * A basic Object Repository, having most of the functionality that the object repositories share.
 * The creation of new inhabitants, and the deserialization of inhabitants is delegated to
 * derived classes.
 *
 * @param <I> An implementation type of the repository object that this repository will store.
 * @param <T> The non-generic interface that <code>I</code> implements.
 */
abstract class ObjectRepositoryImpl<I extends RepositoryObjectImpl<T>, T extends RepositoryObject> implements ObjectRepository<T>, EventHandler, ChangeNotifier {
    protected BundleContext m_context; /* injected by dependency manager */

    private final List<T> m_repo = new CopyOnWriteArrayList<T>();
    private final ChangeNotifier m_notifier;

    private final String m_xmlNode;

    private volatile boolean m_busy = false;

    /**
     * The main constructor for this repository.
     * @param xmlNode The tag that represents this repository (not its objects) in an XML representation.
     */
    public ObjectRepositoryImpl(ChangeNotifier notifier, String xmlNode) {
        m_notifier = notifier;
        m_xmlNode = xmlNode;
    }

    /**
     * Creates a new inhabitant of this repository with the given attributes. The actual creation of the
     * object is delagated to a derived class; this function will make sure the correct events get fired
     * and the object gets stored.
     */
    // About this SuppressWarnings: for some reason, the compiler cannot see that I is a proper subtype of T.
    @SuppressWarnings("unchecked")
    public T create(Map<String, String> attributes, Map <String, String> tags) throws IllegalArgumentException {
        if (m_busy) {
            throw new IllegalStateException("The repository is currently busy, so no new objects can be created.");
        }
        T result =  (T) createNewInhabitant(attributes, tags);
        if (add(result)) {
            return result;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Helper method that stores an object in the repository, taking care of the right events.
     * @param entity the object to be stored.
     * @return true only when the object (or at least one identical to it) did not yet exist in the repository.
     */
    boolean add(T entity) {
        boolean result = false;

        synchronized (m_repo) {
            if (!m_repo.contains(entity)) {
                m_repo.add(entity);
                result = true;
            }
        }
        if (result) {
            notifyChanged(entity, RepositoryObject.TOPIC_ADDED_SUFFIX);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public void remove(T entity) {
        if (m_busy) {
            throw new IllegalStateException("The repository is currently busy, so no objects can be removed.");
        }
        boolean result = false;
        synchronized (m_repo) {
            if (m_repo.remove(entity)) {
                ((I) entity).setDeleted();
                result = true;
            }
        }
        if (result) {
            notifyChanged(entity, RepositoryObject.TOPIC_REMOVED_SUFFIX);
        }
    }

    /**
     * Removes all objects in this repository, without caring for the consistency and
     * correct event firing.
     */
    @SuppressWarnings("unchecked")
    void removeAll() {
        synchronized(m_repo) {
            for (T object : m_repo) {
                ((I) object).setDeleted();
            }
            m_repo.clear();
        }
    }

    /**
     * Notifies listeners of a change to a given object. It will also notify
     * listeners of any changes to the status of this repository.
     * @param entity The object that has changed.
     * @param topic The topic to use.
     */
    private void notifyChanged(T entity, String topic) {
        Properties props = new Properties();
        props.put(RepositoryObject.EVENT_ENTITY, entity);
        notifyChanged(topic, props, m_busy);
    }

    public void notifyChanged(String topic, Properties props, boolean internalOnly) {
        m_notifier.notifyChanged(topic, props, internalOnly);
    }

    public void notifyChanged(String topic, Properties props) {
        notifyChanged(topic, props, false);
    }

    public String getTopicAll(boolean publicTopic) {
        return m_notifier.getTopicAll(publicTopic);
    }

    public List<T> get() {
        return new ArrayList<T>(m_repo);
    }

    public List<T> get(Filter filter) {
        List<T> result = new ArrayList<T>();
        for (T entry : m_repo) {
            if (filter.match(entry.getDictionary())) {
                result.add(entry);
            }
        }
        return result;
    }

    Filter createFilter(String filter) throws InvalidSyntaxException {
        return m_context.createFilter(filter);
    }

    /**
     * Writes this repository and its inhabitants to an XML stream. The serialization of
     * the inhabitants will be delegated to the inhabitants themselves.
     * @param writer The writer to write the XML representation to.
     */
    @SuppressWarnings("unchecked")
    public void marshal(HierarchicalStreamWriter writer) {
        writer.startNode(m_xmlNode);
        for (T inhabitant : m_repo) {
            ((I) inhabitant).marshal(writer);
        }
        writer.endNode();
    }

    /**
     * Reads the inhabitants of this repository from an XML stream.
     * @param reader A reader of the XML representation.
     */
    @SuppressWarnings("unchecked")
    public void unmarshal(HierarchicalStreamReader reader) {
        try {
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                I newInhabitant = createNewInhabitant(reader);
                newInhabitant.setBusy(m_busy);
                add((T) newInhabitant);
                reader.moveUp();
            }
        }
        catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public void handleEvent(Event e) {
        for (T inhabitant : m_repo) {
            ((I) inhabitant).handleEvent(e);
        }
    }

    /**
     * Creates a new inhabitant of the repository based on a map of attributes.
     * @param attributes A map of attributes
     * @param tags A map of tags
     * @return The new inhabitant.
     */
    abstract I createNewInhabitant(Map<String, String> attributes, Map<String, String> tags);
    /**
     * Creates a new inhabitant of the repository based on a map of attributes.
     * @param attributes A map of attributes
     * @return The new inhabitant.
     */
    abstract I createNewInhabitant(Map<String, String> attributes);
    /**
     * Creates a new inhabitant of the repository based on an XML representation.
     * @param reader A reader for the XML representation.
     * @return The new inhabitant.
     */
    abstract I createNewInhabitant(HierarchicalStreamReader reader);

    public String getXmlNode() {
        return m_xmlNode;
    }

    /**
     * Sets this repository to busy: this will be delegated to all inhabitants.
     */
    @SuppressWarnings("unchecked")
    public void setBusy(boolean busy) {
        synchronized(m_repo) {
            m_busy = busy;
            for (RepositoryObject o : m_repo) {
                ((I) o).setBusy(busy);
            }
        }
    }
}
