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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ace.client.repository.Associatable;
import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Represents a value-object as is part of the repository.<br>
 * It stores the 'member' values of the repository object, and allows putting tags on this object by using
 * <code>put()</code> and <code>remove()</code>. It 'looks' like a dictionary to allow filtering of it, using an ldap
 * filter.
 */
@SuppressWarnings("rawtypes")
public class RepositoryObjectImpl<T extends RepositoryObject> extends Dictionary<String, Object> implements RepositoryObject, EventHandler {
    private final Map<String, String> m_attributes = new HashMap<>();
    private final Map<String, String> m_tags = new HashMap<>();
    /** see ACE-463 */
    private final Set<String> m_mergedAttrTags = new HashSet<>();
    private final Map<Class<?>, List<Association>> m_associations = new HashMap<>();
    private final ChangeNotifier m_notifier;
    private final String m_xmlNode;
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("[a-zA-Z]([a-zA-Z0-9_:.-])*");

    private volatile boolean m_deleted = false;
    private volatile boolean m_busy = false;

    public RepositoryObjectImpl(ChangeNotifier notifier, String xmlNode) {
        this((Map<String, String>) null, null, notifier, xmlNode);
    }

    public RepositoryObjectImpl(Map<String, String> attributes, ChangeNotifier notifier, String xmlNode) {
        this(attributes, null, notifier, xmlNode);
    }

    public RepositoryObjectImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, String xmlNode) {
        this(readMap(reader), readMap(reader), notifier, xmlNode);
        readCustom(reader);
    }

    public RepositoryObjectImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, String xmlNode) {
        m_xmlNode = xmlNode;
        if (attributes != null) {
            m_attributes.putAll(attributes);
            m_mergedAttrTags.addAll(attributes.keySet());
        }
        if (tags != null) {
            m_tags.putAll(tags);
            m_mergedAttrTags.addAll(tags.keySet());
        }
        if (notifier == null) {
            throw new IllegalArgumentException();
        }
        m_notifier = notifier;
    }

    @Override
    public void notifyChanged() {
        notifyChanged(null);
    }

    protected void notifyChanged(Properties props) {
        if (props == null) {
            props = new Properties();
        }
        props.put(EVENT_ENTITY, this);
        m_notifier.notifyChanged(TOPIC_CHANGED_SUFFIX, props, m_busy);
    }

    /**
     * Returns an enumeration of the values in this object's dictionary.
     */
    @Override
    public Enumeration<Object> elements() {
        synchronized (m_attributes) {
            return new ValuesEnumeration();
        }
    }

    /**
     * Gets the object associated with this key. Will return null when the key is not used; if the key is available for
     * both the tags and the object's basic information, an array of two Strings will be returned.
     */
    @Override
    public Object get(Object key) {
        synchronized (m_attributes) {
            String manifest = m_attributes.get(key);
            String tag = m_tags.get(key);

            if (manifest == null) {
                return tag;
            }
            else if (tag == null) {
                return manifest;
            }
            else {
                return new String[] { tag, manifest };
            }
        }
    }

    /**
     * Return whether the dictionary is empty.
     */
    @Override
    public boolean isEmpty() {
        synchronized (m_attributes) {
            return m_mergedAttrTags.isEmpty();
        }
    }

    /**
     * Returns an enumeration of the keys in this object.
     */
    @Override
    public Enumeration<String> keys() {
        synchronized (m_attributes) {
            return new KeysEnumeration(m_mergedAttrTags.iterator());
        }
    }

    /**
     * Unsupported operation.
     * 
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Unsupported operation.
     * 
     * @throws UnsupportedOperationException
     *             always
     */
    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the number of keys in both this object's tags, and its 'member' keys.
     */
    @Override
    public int size() {
        synchronized (m_attributes) {
            return m_mergedAttrTags.size();
        }
    }

    public String addAttribute(String key, String value) {
        if (value == null || key == null) {
            throw new IllegalArgumentException("Invalid key/value pair: " + key + "/" + value);
        }
        validateKey(key);
        for (String s : getDefiningKeys()) {
            if (s.equals(key)) {
                throw new UnsupportedOperationException("The defining attribute " + key + " is not allowed to be changed.");
            }
        }
        String result = null;
        synchronized (m_attributes) {
            ensureCurrent();
            result = m_attributes.get(key);
            // ACE-463: keep a cached version of the merged attribute/tag keys...
            m_mergedAttrTags.add(key);
            if (!value.equals(result)) {
                result = m_attributes.put(key, value);
                notifyChanged(null);
            }
        }
        return result;
    }

    @Override
    public String removeAttribute(String key) {
        for (String s : getDefiningKeys()) {
            if (s.equals(key)) {
                throw new UnsupportedOperationException("The defining attribute " + key + " is not allowed to be changed.");
            }
        }
        String result = null;
        synchronized (m_attributes) {
            ensureCurrent();
            result = m_attributes.get(key);
            // ACE-463: keep a cached version of the merged attribute/tag keys...
            if (!m_tags.containsKey(key)) {
                m_mergedAttrTags.remove(key);
            }
            if (result != null) {
                result = m_attributes.remove(key);
                notifyChanged(null);
            }
        }
        return result;
    }

    public String addTag(String key, String value) {
        if (value == null || key == null) {
            throw new IllegalArgumentException("Invalid key/value pair: " + key + "/" + value);
        }
        validateKey(key);
        String result = null;
        synchronized (m_attributes) {
            ensureCurrent();
            result = m_tags.get(key);
            // ACE-463: keep a cached version of the merged attribute/tag keys...
            m_mergedAttrTags.add(key);
            if (!value.equals(result)) {
                result = m_tags.put(key, value);
                notifyChanged(null);
            }
        }
        return result;
    }

    private void validateKey(String key) {
        if (!VALID_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key " + key + " does not match regex pattern " + VALID_KEY_PATTERN.pattern());
        }
    }

    @Override
    public String removeTag(String key) {
        String result = null;
        synchronized (m_attributes) {
            ensureCurrent();
            result = m_tags.get(key);
            // ACE-463: keep a cached version of the merged attribute/tag keys...
            if (!m_attributes.containsKey(key)) {
                m_mergedAttrTags.remove(key);
            }
            if (result != null) {
                result = m_tags.remove(key);
                notifyChanged(null);
            }
        }
        return result;
    }

    public String getAttribute(String key) {
        synchronized (m_attributes) {
            return m_attributes.get(key);
        }
    }

    public String getTag(String key) {
        synchronized (m_attributes) {
            return m_tags.get(key);
        }
    }

    public Enumeration<String> getAttributeKeys() {
        synchronized (m_attributes) {
            return new KeysEnumeration(new HashSet<>(m_attributes.keySet()).iterator());
        }
    }

    public Dictionary<String, Object> getDictionary() {
        return this;
    }

    public Enumeration<String> getTagKeys() {
        synchronized (m_attributes) {
            return new KeysEnumeration(new HashSet<>(m_tags.keySet()).iterator());
        }
    }

    public void add(Association association, Class clazz) {
        synchronized (m_associations) {
            List<Association> associations = m_associations.get(clazz);
            if (associations == null) {
                associations = new ArrayList<>();
                m_associations.put(clazz, associations);
            }
            associations.add(association);
        }
    }

    public void remove(Association association, Class clazz) {
        synchronized (m_associations) {
            List<Association> associations = m_associations.get(clazz);
            if (associations != null) {
                associations.remove(association);
            }
        }
    }

    public <A extends Associatable> List<A> getAssociations(Class<A> clazz) {
        synchronized (m_associations) {
            List<A> result = new ArrayList<>();
            List<Association> associations = m_associations.get(clazz);
            if (associations != null) {
                for (Association association : associations) {
                    List<A> otherSide = association.getTo(this);
                    if (otherSide != null) {
                        // If the other side is null, the association
                        // is not satisfied.
                        result.addAll(otherSide);
                    }
                }
            }
            return result;
        }
    }

    public boolean isAssociated(Object obj, Class clazz) {
        synchronized (m_associations) {
            if (obj == null) {
                return false;
            }
            List<Association> associations = m_associations.get(clazz);
            if (associations != null) {
                for (Association association : associations) {
                    if (association.getTo(this).contains(obj)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public <TA extends Associatable, A extends Association> List<A> getAssociationsWith(Associatable other, Class<TA> clazz, Class<A> associationType) {
        List<A> result = new ArrayList<>();
        synchronized (m_associations) {
            if (other == null) {
                return result;
            }
            List<Association> associations = m_associations.get(clazz);
            if (associations != null) {
                for (Association association : associations) {
                    if (association.getTo(this).contains(other)) {
                        result.add((A) association);
                    }
                }
            }
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        synchronized (m_attributes) {
            if ((o == null) || !(getClass().isInstance(o))) {
                return false;
            }
            if (m_attributes.size() == 0) {
                return this == o;
            }
            for (String s : getDefiningKeys()) {
                String ourAttribute = m_attributes.get(s);
                String otherAttribute = (String) ((RepositoryObjectImpl) o).m_attributes.get(s);
                if ((ourAttribute == null) && (otherAttribute != null)) {
                    return false;
                }
                else if ((ourAttribute != null) && (otherAttribute == null)) {
                    return false;
                }
                else if ((ourAttribute == null) && (otherAttribute == null)) {
                    continue;
                }
                else if (!otherAttribute.equals(ourAttribute)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Returns an array of keys which are considered to be defining for this object's attributes. The basic
     * implementation just uses every key that is used; this function is intended to be overridden by deriving classes,
     * providing a real set.
     *
     * Note that the array returned from this function should be read from only; writing to it will change the state of
     * the object.
     */
    String[] getDefiningKeys() {
        return m_attributes.keySet().toArray(new String[m_attributes.size()]);
    }

    @Override
    public int hashCode() {
        synchronized (m_attributes) {
            return m_attributes.hashCode();
        }
    }

    void marshal(HierarchicalStreamWriter writer) {
        synchronized (m_attributes) {
            writer.startNode(m_xmlNode);
            writeMap(writer, m_attributes, "attributes");
            writeMap(writer, m_tags, "tags");
            writeCustom(writer);
            writer.endNode();
        }
    }

    /**
     * This method is intended to be overridden by deriving classes, to read custom information from the XML
     * representation of this object. This method should end with the writer at the same 'level' as before, that is,
     * using equally many moveDown() and moveUp() calls.
     * 
     * @param reader
     *            A reader to read from the XML stream.
     */
    protected void readCustom(HierarchicalStreamReader reader) {
        // do nothing
    }

    /**
     * This method is intended to be overridden by deriving classes, to write custom information to the XML
     * representation of this object. This method should end with the writer at the same 'level' as before, that is,
     * using equally many moveDown() and moveUp() calls.
     * 
     * @param writer
     *            A writer to write to the XML stream.
     */
    protected void writeCustom(HierarchicalStreamWriter writer) {
        // do nothing
    }

    static void writeMap(HierarchicalStreamWriter writer, Map<String, String> entries, String name) {
        writer.startNode(name);
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                // we only write values that have non null keys and values
                writer.startNode(entry.getKey());
                writer.setValue(entry.getValue());
                writer.endNode();
            }
        }
        writer.endNode();
    }

    static Map<String, String> readMap(HierarchicalStreamReader reader) {
        reader.moveDown();
        Map<String, String> result = new HashMap<>();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            result.put(reader.getNodeName(), reader.getValue());
            reader.moveUp();
        }
        reader.moveUp();
        return result;
    }

    /**
     * Helper function to check the existence of keys in a map. Each attribute is required to be non-empty.
     * 
     * @param attributes
     *            A map of attribute-value combinations.
     * @param mandatory
     *            An array of attributes which have to be present in the map.
     * @return <code>attributes</code> if this map meets the requirements. If not, <code>IllegalArgumentException</code>
     *         will be thrown.
     */
    static Map<String, String> checkAttributes(Map<String, String> attributes, String... mandatory) {
        boolean[] booleans = new boolean[mandatory.length];
        Arrays.fill(booleans, false);
        return checkAttributes(attributes, mandatory, booleans);
    }

    /**
     * Helper function to check the existence of keys in a map.
     * 
     * @param attributes
     *            A map of attribute-value combinations.
     * @param mandatory
     *            An array of attributes which have to be present in the map.
     * @param emptyAttributeAllowed
     *            An array of booleans, indicating which of the attributes is allowed to be equal. Items in this array
     *            are matched by index on the elements in <code>mandatory</code>, so the length should be equal.
     * @return <code>attributes</code> if this map meets the requirements. If not, <code>IllegalArgumentException</code>
     *         will be thrown.
     */
    static Map<String, String> checkAttributes(Map<String, String> attributes, String[] mandatory, boolean[] emptyAttributeAllowed) {
        if (!(mandatory.length == emptyAttributeAllowed.length)) {
            throw new IllegalArgumentException("The length of the mandatory- and the emptyAttributeAllow-array should be equal.");
        }
        for (int i = 0; i < mandatory.length; i++) {
            String attribute = mandatory[i];
            boolean emptyAllowed = emptyAttributeAllowed[i];
            if (!attributes.containsKey(attribute)) {
                throw new IllegalArgumentException(attribute + " is a mandatory attribute.");
            }
            else if ((!emptyAllowed) && (attributes.get(attribute).length() == 0)) {
                throw new IllegalArgumentException(attribute + " is not allowed to be empty.");
            }
        }
        return attributes;
    }

    public String getXmlNode() {
        return m_xmlNode;
    }

    public void handleEvent(Event e) {
    }

    void setDeleted() {
        m_deleted = true;
    }

    public boolean isDeleted() {
        return m_deleted;
    }

    void ensureNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("This object is deleted, and should no longer be used.");
        }
    }

    void setBusy(boolean busy) {
        // setBusy should 'wait' until all altering operations have passed. To do so,
        // it gets the locks for the other 'set' objects. Once it has all these locks,
        // we are sure no thread is performing a set-action.
        synchronized (m_associations) {
            synchronized (m_attributes) {
                if (m_busy && !busy) {
                    m_associations.notifyAll();
                    m_attributes.notifyAll();
                }
                m_busy = busy;
            }
        }
    }

    public boolean getBusy() {
        return m_busy;
    }

    // NEVER CALL WITHOUT m_attributes lock
    private void ensureNotBusy() {
        boolean interrupted = false;
        while (m_busy) {
            try {
                m_attributes.wait();
            }
            catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    void ensureCurrent() {
        ensureNotDeleted();
        ensureNotBusy();
    }

    public String getDefinition() {
        StringBuilder result = new StringBuilder();

        result.append(getXmlNode().replaceAll("\\\\", "\\\\\\\\").replaceAll("-", "\\-"));

        for (String key : getDefiningKeys()) {
            String value = getAttribute(key);
            if (value != null) {
                result.append("-")
                    .append(key.replaceAll("\\\\", "\\\\\\\\").replaceAll("-", "\\-").replaceAll("\\/", "&#47"))
                    .append("-")
                    .append(value.replaceAll("\\\\", "\\\\\\\\").replaceAll("-", "\\-").replaceAll("\\/", "&#47"));
                // About the &#47: the forward slash will be used by the preference admin, but we don't want that.
            }
        }

        return result.toString();
    }

    /**
     * Creates a filter string for use in associations, optionally with some additional properties. The basic
     * implementation will use all <code>getDefiningKeys</code>.
     * 
     * @param properties
     *            Properties indicating specifics of the filter to be created.
     * @return A string representation of a filter, for use in <code>Association</code>s.
     */
    public String getAssociationFilter(Map<String, String> properties) {
        StringBuilder filter = new StringBuilder("(&");

        for (String key : getDefiningKeys()) {
            filter.append("(" + key + "=" + RepositoryUtil.escapeFilterValue(getAttribute(key)) + ")");
        }

        filter.append(")");

        return filter.toString();
    }

    /**
     * Determines the cardinality of this endpoint of an association, given the passed properties.
     * 
     * @param properties
     *            Properties indicating specifics of this endpoint.
     * @return The necessary cardinality.
     */
    public int getCardinality(Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns a <code>Comparator</code> for this type of object, suitable for the endpoint properties that are passed.
     * 
     * @return A <code>Comparator</code> for this type of object
     */
    public Comparator<T> getComparator() {
        return null;
    }

    /**
     * Helper class that implements an enumeration for use in <code>keys()</code>.
     */
    private static class KeysEnumeration implements Enumeration<String> {
        private final Iterator<String> m_iter;

        public KeysEnumeration(Iterator<String> iter) {
            m_iter = iter;
        }

        public boolean hasMoreElements() {
            return m_iter.hasNext();
        }

        public String nextElement() {
            return m_iter.next();
        }
    }

    /**
     * Helper class that implements an enumeration for use in <code>elements()</code>.
     */
    private class ValuesEnumeration implements Enumeration<Object> {
        private final Enumeration<String> m_iter = keys();

        public boolean hasMoreElements() {
            return m_iter.hasMoreElements();
        }

        public Object nextElement() {
            return get(m_iter.nextElement());
        }
    }

    @Override
    public String toString() {
        StringBuilder attrs = new StringBuilder();
        Enumeration<String> enumeration = getAttributeKeys();
        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement();
            if (attrs.length() > 0) {
                attrs.append(',');
            }
            attrs.append(key + "=" + getAttribute(key));
        }
        return getClass().getSimpleName() + "[" + attrs.toString() + "]";
    }
}
