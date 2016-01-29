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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.client.repository.Associatable;
import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.RepositoryObject;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.event.Event;

import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/**
 * A basic implementation of the Association interface. Implements 'common' behavior for all associations.
 *
 * The association allows up to m-to-n associations. Each end of the association gets
 * a filter to match objects, possibly a cardinality (if no cardinality is specified,
 * 1 is assumed), and, if there can be more matches to the filter than the given cardinality,
 * a comparator should be provided in the constructor.
 *
 * @param <L> The type of the RepositoryObject on the left side of this association
 * @param <R> The type of the RepositoryObject on the right side of this association
 * @param <T> The non-generic Association interface this object should use.
 */
public class AssociationImpl<L extends RepositoryObject, R extends RepositoryObject, T extends Association<L, R>> extends RepositoryObjectImpl<T> implements Association<L, R> {

    /* These lists are volatile, since we use copy-on-write semantics for
     * updating them.
     */
    private volatile List<L> m_left = new ArrayList<>();
    private volatile List<R> m_right = new ArrayList<>();
    private final Object m_lock = new Object();

    private final Filter m_filterLeft;
    private final Filter m_filterRight;

    private final ObjectRepositoryImpl<?, L> m_leftRepository;
    private final ObjectRepositoryImpl<?, R> m_rightRepository;
    private final Class<L> m_leftClass;
    private final Class<R> m_rightClass;

    /**
     * Constructor intended for deserialization. For most parameters, see below.
     * @param reader a stream reader which contains an XML representation of this object's contents.
     */
    public AssociationImpl(HierarchicalStreamReader reader, ChangeNotifier notifier, Class<L> leftClass, Class<R> rightClass, Comparator<L> leftComparator, Comparator<R> rightComparator, ObjectRepositoryImpl<?, L> leftRepository, ObjectRepositoryImpl<?, R> rightRepository, String xmlNode) throws InvalidSyntaxException {
        this(readMap(reader), notifier, leftClass, rightClass, leftRepository, rightRepository, xmlNode);
    }

    /**
     * Basic constructor for AssociationImpl.
     * @param attributes A map of attributes. This should at least contain <code>Association.LEFT_ENDPOINT</code> and <code>Association.RIGHT_ENDPOINT</code>,
     * and optionally <code>Association.LEFT_CARDINALITY</code> and <code>Association.RIGHT_CARDINALITY</code>.
     * @param notifier An instance of the event admin
     * @param leftClass The class on the left side of this association.
     * @param rightClass The class on the right side of this association.
     * @param leftRepository The repository which holds object of <code>leftClass</code>.
     * @param rightRepository The repository which holds object of <code>rightClass</code>.
     * @param xmlNode The tag by which this object is known in the XML representation.
     * @throws InvalidSyntaxException Thrown when the attributes contain an invalidly constructed filter string.
     */
    public AssociationImpl(Map<String, String> attributes, ChangeNotifier notifier, Class<L> leftClass, Class<R> rightClass, ObjectRepositoryImpl<?, L> leftRepository, ObjectRepositoryImpl<?, R> rightRepository, String xmlNode) throws InvalidSyntaxException {
        super(attributes, notifier, xmlNode);

        if ((getAttribute(LEFT_CARDINALITY) != null) && (Integer.parseInt(getAttribute(LEFT_CARDINALITY)) < 1)) {
            throw new IllegalArgumentException("The left cardinality should be 1 or greater.");
        }
        if ((getAttribute(RIGHT_CARDINALITY) != null) && (Integer.parseInt(getAttribute(RIGHT_CARDINALITY)) < 1)) {
            throw new IllegalArgumentException("The right cardinality should be 1 or greater.");
        }

        m_leftClass = leftClass;
        m_rightClass = rightClass;
        m_leftRepository = leftRepository;
        m_rightRepository = rightRepository;

        m_filterLeft = m_leftRepository.createFilter(getAttribute(Association.LEFT_ENDPOINT));
        m_filterRight = m_rightRepository.createFilter(getAttribute(Association.RIGHT_ENDPOINT));

        locateLeftEndpoint(false);
        locateRightEndpoint(false);
    }

    public AssociationImpl(Map<String, String> attributes, Map<String, String> tags, ChangeNotifier notifier, Class<L> leftClass, Class<R> rightClass, ObjectRepositoryImpl<?, L> leftRepository, ObjectRepositoryImpl<?, R> rightRepository, String xmlNode) throws InvalidSyntaxException {
        super(attributes, tags, notifier, xmlNode);

        if ((getAttribute(LEFT_CARDINALITY) != null) && (Integer.parseInt(getAttribute(LEFT_CARDINALITY)) < 1)) {
            throw new IllegalArgumentException("The left cardinality should be 1 or greater.");
        }
        if ((getAttribute(RIGHT_CARDINALITY) != null) && (Integer.parseInt(getAttribute(RIGHT_CARDINALITY)) < 1)) {
            throw new IllegalArgumentException("The right cardinality should be 1 or greater.");
        }

        m_leftClass = leftClass;
        m_rightClass = rightClass;
        m_leftRepository = leftRepository;
        m_rightRepository = rightRepository;

        m_filterLeft = m_leftRepository.createFilter(getAttribute(Association.LEFT_ENDPOINT));
        m_filterRight = m_rightRepository.createFilter(getAttribute(Association.RIGHT_ENDPOINT));

        locateLeftEndpoint(false);
        locateRightEndpoint(false);
    }

    public List<Associatable> getTo(Associatable from) {
        if (m_left.contains(from)) {
            return new ArrayList<Associatable>(m_right);
        }
        if (m_right.contains(from)) {
            return new ArrayList<Associatable>(m_left);
        }
        return null;
    }

    public List<L> getLeft() {
        return new ArrayList<>(m_left);
    }

    public List<R> getRight() {
        return new ArrayList<>(m_right);
    }

    public void remove() {
        for (L l : m_left) {
            l.remove(this, m_rightClass);
        }
        for (R r : m_right) {
            r.remove(this, m_leftClass);
        }
    }

    /**
     * Locates the most suited endpoint of one side of this association. If the corresponding filter
     * matches multiple objects, the <code>comparator</code> will be used to find the most suited one.
     * The association will register itself with a new endpoint, and remove itself from the old one.
     * @param <TYPE> (only used for type matching).
     * @param objectRepositoryImpl The repository where the endpoint should come from.
     * @param filter A filter string, used to get candidate-endpoints from <code>objectRepositoryImpl</code>.
     * @param endpoint The current endpoint.
     * @param comparator A comparator, used when there are multiple potential endpoints.
     * @param clazz The class of the 'other side' of this association.
     * @return The most suited endpoint; this could be equal to <code>endpoint</code>.
     */
    private <TYPE extends RepositoryObject> List<TYPE> locateEndpoint(ObjectRepositoryImpl<?, TYPE> objectRepositoryImpl, Filter filter, List<TYPE> endpoints, int cardinality, Class<? extends RepositoryObject> clazz, boolean notify) {

        List<TYPE> candidates = objectRepositoryImpl.get(filter);
        if (candidates.size() > cardinality) {
            Comparator<TYPE> comparator = candidates.get(0).getComparator();
            if (comparator != null) {
                Collections.sort(candidates, comparator);
            }
            else {
                throw new NullPointerException("Filter '" + filter.toString() + "' in '" + this + "' has resulted in multiple candidates, so the RepositoryObject descendents should have provide a comparator, which they do not.");
            }
        }
        
        List<TYPE> oldEndpoints = new ArrayList<>(endpoints);
        List<TYPE> newEndpoints = new ArrayList<>();
        for (int i = 0; (i < cardinality) && !candidates.isEmpty(); i++) {
            TYPE current = candidates.remove(0);
            newEndpoints.add(current);
            if (!oldEndpoints.remove(current)) {
                current.add(this, clazz);
            }
        }
        for (TYPE e : oldEndpoints) {
            e.remove(this, clazz);
        }
        return newEndpoints;
    }

    /**
     * Locates the left endpoint by using the generic locateEndpoint and notifies
     * listeners of changes, if any.
     * @param notify Indicates whether notifications should be sent out.
     */
    private void locateLeftEndpoint(boolean notify) {
        synchronized (m_lock) {
            List<L> newEndpoints = locateEndpoint(m_leftRepository, m_filterLeft, m_left, (getAttribute(LEFT_CARDINALITY) == null ? 1 : Integer.parseInt(getAttribute(LEFT_CARDINALITY))), m_rightClass, notify);
            if (!newEndpoints.equals(m_left)) {
                if (notify) {
                    List<L> oldEndpoints = new ArrayList<>(m_left);
                    m_left = new ArrayList<>(newEndpoints);
                    Properties props = new Properties();
                    props.put(EVENT_OLD, oldEndpoints);
                    props.put(EVENT_NEW, newEndpoints);
                    notifyChanged(props);
                }
                else {
                    m_left = newEndpoints;
                }
            }
        }
    }

    /**
     * Locates the right endpoint by using the generic locateEndpoint and notifies
     * listeners of changes, if any.
     * @param notify Indicates whether notifications should be sent out.
     */
    private void locateRightEndpoint(boolean notify) {
        synchronized (m_lock) {
            List<R> newEndpoints = locateEndpoint(m_rightRepository, m_filterRight, m_right, (getAttribute(RIGHT_CARDINALITY) == null ? 1 : Integer.parseInt(getAttribute(RIGHT_CARDINALITY))), m_leftClass, notify);
            if (!newEndpoints.equals(m_right)) {
                if (notify) {
                    List<R> oldEndpoints = new ArrayList<>(m_right);
                    m_right = new ArrayList<>(newEndpoints);
                    Properties props = new Properties();
                    props.put(EVENT_OLD, oldEndpoints);
                    props.put(EVENT_NEW, newEndpoints);
                    notifyChanged(props);
                }
                else {
                    m_right = newEndpoints;
                }
            }
        }
    }

    public boolean isSatisfied() {
        return (!m_left.isEmpty()) && (!m_right.isEmpty());
    }

    @Override
    public void handleEvent(Event event) {
        // We get a topic which ends in '/*', but the event contains a specialized topic.
        // for now, we chop of the star, and check whether the topic starts with that.
        RepositoryObject entity = (RepositoryObject) event.getProperty(RepositoryObject.EVENT_ENTITY);
        if ((event.getTopic().endsWith("ADDED")) || event.getTopic().endsWith("REMOVED")) {
            if (m_leftClass.isInstance(entity) && m_filterLeft.matchCase(entity.getDictionary())) {
                locateLeftEndpoint(true);
            }
            if (m_rightClass.isInstance(entity) && m_filterRight.matchCase(entity.getDictionary())) {
                locateRightEndpoint(true);
            }
        }
    }
}
