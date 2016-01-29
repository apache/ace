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
package org.apache.ace.client.repository.stateful.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.ace.client.repository.Associatable;
import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Descriptor;
import org.apache.ace.feedback.Event;

/**
 * A <code>StatefulTargetObjectImpl</code> uses the interface of a <code>StatefulTargetObject</code>,
 * but delegates most of its calls to either an embedded <code>TargetObject</code>, or to its
 * parent <code>StatefulTargetRepository</code>. Once created, it will handle its own life cycle
 * and remove itself once is existence is no longer necessary.
 */
public class StatefulTargetObjectImpl implements StatefulTargetObject {
    private final StatefulTargetRepositoryImpl m_repository;
    private final Object m_lock = new Object();
    private TargetObject m_targetObject;
    private List<Descriptor> m_processedAuditEvents = new ArrayList<>();
    private Map<String, String> m_processedTargetProperties;
    private Map<String, String> m_attributes = new HashMap<>();
    /** This boolean is used to suppress STATUS_CHANGED events during the creation of the object. */
    private boolean m_inConstructor = true;
    /** Boolean to ensure we don't recursively enter the determineProvisioningState() method. */
    private boolean m_determiningProvisioningState = false;

    /**
     * Creates a new <code>StatefulTargetObjectImpl</code>. After creation, it will have the
     * most recent data available, and has verified its own reasons for existence.
     * @param repository The parent repository of this object.
     * @param targetID A string representing a target ID.
     */
    StatefulTargetObjectImpl(StatefulTargetRepositoryImpl repository, String targetID) {
        m_repository = repository;
        addStatusAttribute(KEY_ID, targetID);
        updateTargetObject(false);
        updateAuditEvents(false);
        updateDeploymentVersions(null);
        verifyExistence();
        m_inConstructor = false;
    }

    public String approve() throws IllegalStateException {
        try {
            String version = m_repository.approve(getID());
            setApprovalState(ApprovalState.Approved);
            return version;
        }
        catch (IOException e) {
            throw new IllegalStateException("Problem generating new deployment version: " + e.getMessage(), e);
        }
    }

    public List<Event> getAuditEvents() {
        return m_repository.getAuditEvents(getID());
    }

    public String getCurrentVersion() {
        DeploymentVersionObject version = m_repository.getMostRecentDeploymentVersion(getID());
        if (version == null) {
            return StatefulTargetObject.UNKNOWN_VERSION;
        }
        else {
            return version.getVersion();
        }
    }

    public void register() throws IllegalStateException {
        m_repository.register(getID());
    }

    public boolean isRegistered() {
        synchronized (m_lock) {
            return (m_targetObject != null);
        }
    }

    public TargetObject getTargetObject() {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject;
        }
    }

    public DeploymentArtifact[] getArtifactsFromDeployment() {
        synchronized (m_lock) {
            DeploymentVersionObject mostRecentDeploymentVersion = m_repository.getMostRecentDeploymentVersion(getID());
            if (mostRecentDeploymentVersion != null) {
                return mostRecentDeploymentVersion.getDeploymentArtifacts();
            }
            return new DeploymentArtifact[0];
        }
    }

    public ArtifactObject[] getArtifactsFromShop() {
        return m_repository.getNecessaryArtifacts(getID());
    }

    public boolean getLastInstallSuccess() {
        synchronized (m_lock) {
            return Boolean.parseBoolean(getStatusAttribute(KEY_LAST_INSTALL_SUCCESS));
        }
    }

    public String getLastInstallVersion() {
        synchronized (m_lock) {
            return getStatusAttribute(KEY_LAST_INSTALL_VERSION);
        }
    }

    public void acknowledgeInstallVersion(String version) {
        synchronized (m_lock) {
            addStatusAttribute(KEY_ACKNOWLEDGED_INSTALL_VERSION, version);
            if (version.equals(getStatusAttribute(KEY_LAST_INSTALL_VERSION))) {
                setProvisioningState(ProvisioningState.Idle);
            }
        }
    }

    public boolean needsApprove() {
        return getStoreState() == StoreState.Unapproved;
    }

    public ProvisioningState getProvisioningState() {
        return ProvisioningState.valueOf(getStatusAttribute(KEY_PROVISIONING_STATE));
    }

    public RegistrationState getRegistrationState() {
        return RegistrationState.valueOf(getStatusAttribute(KEY_REGISTRATION_STATE));
    }
    
    public ApprovalState getApprovalState() {
        String state = getStatusAttribute(KEY_APPROVAL_STATE);
        return state == null ? ApprovalState.Unapproved : ApprovalState.valueOf(state);
    }

    public StoreState getStoreState() {
        String statusAttribute = getStatusAttribute(KEY_STORE_STATE);
        if (statusAttribute != null) {
            return StoreState.valueOf(statusAttribute);
        }
        return StoreState.New;
    }

    /**
     * Signals this object that there has been a change to the <code>TargetObject</code> it represents.
     * @param needsVerify States whether this update should make the object check for its
     * reasons for existence.
     */
    void updateTargetObject(boolean needsVerify) {
        synchronized (m_lock) {
            m_targetObject = m_repository.getTargetObject(getID());
            determineRegistrationState();
            determineTargetPropertiesState();
            if (needsVerify) {
                verifyExistence();
            }
        }
    }

    /**
     * Signals this object that there has been a change to the auditlog which may interest
     * this object.
     * @param needsVerify States whether this update should make the object check for its
     * reasons for existence.
     */
    void updateAuditEvents(boolean needsVerify) {
        synchronized (m_lock) {
            determineProvisioningState();
            if (needsVerify) {
                verifyExistence();
            }
        }
    }

    /**
     * Signals this object that a new deployment version has been created in relation
     * to the targetID this object manages.
     */
    void updateDeploymentVersions(DeploymentVersionObject deploymentVersionObject) {
        synchronized (m_lock) {
            determineProvisioningState();
            determineStoreState(deploymentVersionObject);
        }
    }

    /**
     * Based on the information about a <code>TargetObject</code>, the
     * <code>AuditEvent</code>s available, and the deployment information that
     * the parent repository can give, determines the status of this target.
     */
    void determineStatus() {
        determineRegistrationState();
        determineProvisioningState();
        determineStoreState(null);
        verifyExistence();
    }

    private void determineRegistrationState() {
        synchronized (m_lock) {
            if (!isRegistered()) {
                setRegistrationState(RegistrationState.Unregistered);
            }
            else {
                setRegistrationState(RegistrationState.Registered);
            }
        }
    }

    private void determineStoreState(DeploymentVersionObject deploymentVersionObject) {
        synchronized (m_lock) {
            SortedSet<String> fromShop = new TreeSet<>();
            ArtifactObject[] artifactsFromShop = m_repository.getNecessaryArtifacts(getID());
            DeploymentVersionObject mostRecentVersion;
            if (deploymentVersionObject == null) {
                mostRecentVersion = m_repository.getMostRecentDeploymentVersion(getID());
            }
            else {
                mostRecentVersion = deploymentVersionObject;
            }
            if (artifactsFromShop == null) {
                if (mostRecentVersion == null) {
                    setStoreState(StoreState.New);
                }
                else {
                    setStoreState(StoreState.Unapproved);
                }
                return;
            }

            for (ArtifactObject ao : artifactsFromShop) {
                fromShop.add(ao.getURL());
            }

            SortedSet<String> fromDeployment = new TreeSet<>();
            for (DeploymentArtifact da : getArtifactsFromDeployment()) {
                fromDeployment.add(da.getDirective(DeploymentArtifact.DIRECTIVE_KEY_BASEURL));
            }

            if ((mostRecentVersion == null) && fromShop.isEmpty()) {
                setStoreState(StoreState.New);
            }
            else if (fromShop.equals(fromDeployment)) {
                // great, we have the same artifacts. But... do they need to be reprocessed?
                // this might be the case when the target has new tags that affect templates
                for (ArtifactObject ao : artifactsFromShop) {
                    if (m_repository.needsNewVersion(ao, getID(), mostRecentVersion.getVersion())) {
                        setStoreState(StoreState.Unapproved);
                        return;
                    }
                }
                setStoreState(StoreState.Approved);
            }
            else {
                setStoreState(StoreState.Unapproved);
            }
        }
    }

    private void determineProvisioningState() {
        /*
         * This method gets all audit events it has not yet seen, and goes through them, backward
         * in time, to find either an INSTALL or a COMPLETE and a TARGETPROPERTIES event.
         * An INSTALL event gives us a version, and tells us we're in InProgress.
         * A COMPLETE event tells gives us a version, and a success. The success
         * will be stored, and also sets the state to OK or Failed, unless the version we found has already been
         * acknowledged, the the state is set to Idle. Also, if there is no information whatsoever, we assume Idle.
         * A TARGETPROPERTIES event will set the target properties accordingly, with the right prefix, overwriting
         * any old target properties.
         */
        synchronized (m_lock) {
            // make sure we don't recursively execute, which can happen when target properties
            // are being set or removed (which triggers a notification, which in turn triggers
            // a call to determineStatus).
            if (m_determiningProvisioningState) {
                return;
            }
            m_determiningProvisioningState = true;
            List<Descriptor> allDescriptors = m_repository.getAllDescriptors(getID());
            List<Descriptor> newDescriptors = m_repository.diffLogDescriptorLists(allDescriptors, m_processedAuditEvents);

            List<Event> newEvents = m_repository.getAuditEvents(newDescriptors);
            boolean foundDeploymentEvent = false;
            boolean foundPropertiesEvent = false;
            for (int position = newEvents.size() - 1; position >= 0; position--) {
                Event event = newEvents.get(position);
                
                if (!foundDeploymentEvent) {
                    // TODO we need to check here if the deployment package is actually the right one
                    String currentVersion = (String) event.getProperties().get(AuditEvent.KEY_VERSION);
                    if (event.getType() == AuditEvent.DEPLOYMENTCONTROL_INSTALL) {
                        addStatusAttribute(KEY_LAST_INSTALL_VERSION, currentVersion);
                        setProvisioningState(ProvisioningState.InProgress);
                        sendNewAuditlog(newDescriptors);
                        m_processedAuditEvents = allDescriptors;
                        foundDeploymentEvent = true;
                    }
                    if (event.getType() == AuditEvent.DEPLOYMENTADMIN_COMPLETE) {
                        addStatusAttribute(KEY_LAST_INSTALL_VERSION, currentVersion);
                        if ((currentVersion != null) && currentVersion.equals(getStatusAttribute(KEY_ACKNOWLEDGED_INSTALL_VERSION))) {
                            setProvisioningState(ProvisioningState.Idle);
                            sendNewAuditlog(newDescriptors);
                            m_processedAuditEvents = allDescriptors;
                            foundDeploymentEvent = true;
                        }
                        else {
                            String value = (String) event.getProperties().get(AuditEvent.KEY_SUCCESS);
                            addStatusAttribute(KEY_LAST_INSTALL_SUCCESS, value);
                            if (Boolean.parseBoolean(value)) {
                                setProvisioningState(ProvisioningState.OK);
                                sendNewAuditlog(newDescriptors);
                                m_processedAuditEvents = allDescriptors;
                                foundDeploymentEvent = true;
                            }
                            else {
                                setProvisioningState(ProvisioningState.Failed);
                                sendNewAuditlog(newDescriptors);
                                m_processedAuditEvents = allDescriptors;
                                foundDeploymentEvent = true;
                            }
                        }
                    }
                }
                if (!foundPropertiesEvent) {
                    if (event.getType() == AuditEvent.TARGETPROPERTIES_SET) {
                        m_processedTargetProperties = event.getProperties();
                        foundPropertiesEvent = true;
                        determineTargetPropertiesState();
                    }
                }
                // as soon as we've found the latest of both types of events, we're done
                if (foundDeploymentEvent && foundPropertiesEvent) {
                    m_determiningProvisioningState = false;
                    return;
                }
            }

            if (m_processedAuditEvents.isEmpty()) {
                setProvisioningState(ProvisioningState.Idle);
            }
            sendNewAuditlog(newDescriptors);
            m_processedAuditEvents = allDescriptors;
            m_determiningProvisioningState = false;
        }
    }

    private void determineTargetPropertiesState() {
        // only process them if the target is already registered
        if (isRegistered() && m_processedTargetProperties != null) {
            Map<String, String> tags = m_processedTargetProperties;
            m_processedTargetProperties = null;
            // clear "old" tags starting with the prefix
            Enumeration<String> keys = m_targetObject.getTagKeys();
            ArrayList<String> keysToDelete = new ArrayList<>();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                if (key.startsWith(TARGETPROPERTIES_PREFIX)) {
                    keysToDelete.add(key);
                }
            }
            for (String keyToDelete : keysToDelete) {
                m_targetObject.removeTag(keyToDelete);
            }
            // add new tags and prefix them
            for (String newKey : tags.keySet()) {
                m_targetObject.addTag(TARGETPROPERTIES_PREFIX + newKey, tags.get(newKey));
            }
        }
    }
    
    private void sendNewAuditlog(List<Descriptor> events) {
        // Check whether there are actually events in the list.
        boolean containsData = false;
        for (Descriptor l : events) {
            containsData |= (l.getRangeSet().getHigh() != 0);
        }

        if (containsData) {
            Properties props = new Properties();
            props.put(StatefulTargetObject.KEY_AUDITEVENTS, events);
            m_repository.notifyChanged(this, TOPIC_AUDITEVENTS_CHANGED, props);
        }
    }

    private void setRegistrationState(RegistrationState state) {
        setStatus(KEY_REGISTRATION_STATE, state.toString());
    }

    private void setStoreState(StoreState state) {
        setStatus(KEY_STORE_STATE, state.toString());
    }

    private void setProvisioningState(ProvisioningState state) {
        setStatus(KEY_PROVISIONING_STATE, state.toString());
    }
    
    private void setApprovalState(ApprovalState state) {
        setStatus(KEY_APPROVAL_STATE, state.toString());
        if (isRegistered() && state == ApprovalState.Approved && needsApprove()) {
            // trigger a change here, because we know the target will change as part of the
            // pre-commit phase
            getTargetObject().notifyChanged();
        }
    }

    private void setStatus(String key, String status) {
        if (!status.equals(getStatusAttribute(key))) {
            addStatusAttribute(key, status);
            handleStatechangeAutomation();
            if (!m_inConstructor) {
                m_repository.notifyChanged(this, TOPIC_STATUS_CHANGED);
            }
        }
    }

    private void handleStatechangeAutomation() {
        if (getStoreState().equals(StoreState.Unapproved) && isRegistered() && getAutoApprove()) {
            if (getApprovalState().equals(ApprovalState.Unapproved)) {
                approve();
            }
        }
    }

    /**
     * Verifies that this object should still be around. If the target is represents
     * shows up in at least the target repository or the auditlog, it has a reason
     * to exists; if not, it doesn't. When it is no longer necessary, it will remove itself
     * from the parent repository.
     * @return Whether or not this object should still exist.
     */
    boolean verifyExistence() {
        synchronized(m_lock) {
            if ((m_targetObject == null) && ((m_processedAuditEvents == null) || m_processedAuditEvents.isEmpty())) {
                m_repository.removeStateful(this);
                return false;
            }
            return true;
        }
    }

    /**
     * Helper method for the delegate methods below: most of these delegate their calls to a
     * <code>TargetObject</code>, but in order to do so, one must be present.
     */
    private void ensureTargetPresent() {
        if ((m_targetObject == null)) {
            throw new IllegalStateException("This StatefulTargetObject is not backed by a TargetObject.");
            // NOTE: we do not check the isDeleted state; the TargetObject itself will notify the user of this.
        }
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || !(o instanceof StatefulTargetObject)) {
            return false;
        }
        return getID().equals(((StatefulTargetObject) o).getID());
    }

    private void addStatusAttribute(String key, String value) {
        m_attributes.put(key, value);
    }

    private String getStatusAttribute(String key) {
        return m_attributes.get(key);
    }

    public String getID() {
        return getStatusAttribute(KEY_ID);
    }

    public boolean isDeleted() {
        return !verifyExistence();
    }

    public List<Distribution2TargetAssociation> getAssociationsWith(DistributionObject distribution) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getAssociationsWith(distribution);
        }
    }

    public List<DistributionObject> getDistributions() {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getDistributions();
        }
    }

    public String addAttribute(String key, String value) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.addAttribute(key, value);
        }
    }
    
    public String removeAttribute(String key) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.removeAttribute(key);
        }
    }

    public String addTag(String key, String value) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.addTag(key, value);
        }
    }
    
    public String removeTag(String key) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.removeTag(key);
        }
    }

    public String getAttribute(String key) {
        // retrieve from both
        synchronized (m_lock) {
            if (Arrays.binarySearch(KEYS_ALL, key) >= 0) {
                return getStatusAttribute(key);
            }
            ensureTargetPresent();
            return m_targetObject.getAttribute(key);
        }
    }

    public Enumeration<String> getAttributeKeys() {
        synchronized (m_lock) {
            List<String> statusKeys = new ArrayList<>();
            for (String s : KEYS_ALL) {
                statusKeys.add(s);
            }
            Enumeration<String> attributeKeys = null;
            if (m_targetObject != null) {
                attributeKeys = m_targetObject.getAttributeKeys();
            }
            return new ExtendedEnumeration<>(attributeKeys, statusKeys, true);
        }
    }

    public Dictionary<String, Object> getDictionary() {
        // build our own dictionary
        synchronized (m_lock) {
            return new StatefulTargetObjectDictionary();
        }
    }

    public String getTag(String key) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getTag(key);
        }
    }

    public Enumeration<String> getTagKeys() {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getTagKeys();
        }
    }

    public boolean getAutoApprove() {
        synchronized (m_lock) {
            if (m_targetObject != null) {
                return m_targetObject.getAutoApprove();
            }
            else {
                return false;
            }

        }
    }

    public void setAutoApprove(boolean approve) {
        synchronized (m_lock) {
            ensureTargetPresent();
            m_targetObject.setAutoApprove(approve);
        }
    }

    public <T extends Associatable> void add(Association association, Class<T> clazz) {
        synchronized (m_lock) {
            ensureTargetPresent();
            m_targetObject.add(association, clazz);
        }
    }

    public <T extends Associatable> List<T> getAssociations(Class<T> clazz) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getAssociations(clazz);
        }
    }

    @SuppressWarnings("rawtypes")
    public <T extends Associatable, A extends Association> List<A> getAssociationsWith(Associatable other, Class<T> clazz, Class<A> associationType) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.getAssociationsWith(other, clazz, associationType);
        }
    }

    public <T extends Associatable> boolean isAssociated(Object obj, Class<T> clazz) {
        synchronized (m_lock) {
            ensureTargetPresent();
            return m_targetObject.isAssociated(obj, clazz);
        }
    }

    public <T extends Associatable> void remove(Association association, Class<T> clazz) {
        synchronized (m_lock) {
            ensureTargetPresent();
            m_targetObject.remove(association, clazz);
        }
    }

    public String getDefinition() {
        return "target-" + KEY_ID + "-" + getID();
    }

    private class ExtendedEnumeration<T> implements Enumeration<T> {
        private Enumeration<T> m_source;
        private List<T> m_extra;
        private final boolean m_allowDuplicates;

        ExtendedEnumeration(Enumeration<T> source, List<T> extra, boolean allowDuplicates) {
            m_source = source;
            m_extra = extra;
            m_allowDuplicates = allowDuplicates;
        }

        public boolean hasMoreElements() {
            boolean inSource = (m_source != null);
            boolean inExtra = false;
            if (m_extra != null) {
                inExtra = !m_extra.isEmpty();
            }
            return inSource || inExtra;
        }

        public T nextElement() {
            if (m_source != null) {
                T result = m_source.nextElement();
                if (!m_source.hasMoreElements()) {
                    m_source = null;
                }
                if (!m_allowDuplicates) {
                    m_extra.remove(result);
                }
                return result;
            }
            else if (!m_extra.isEmpty()) {
                return m_extra.remove(0);
            }
            throw new NoSuchElementException();
        }
    }

    private class StatefulTargetObjectDictionary extends Dictionary<String, Object> {
        private final Dictionary<String, Object> m_dict;

        StatefulTargetObjectDictionary() {
            if (m_targetObject != null) {
                m_dict = m_targetObject.getDictionary();
            }
            else {
                m_dict = null;
            }
        }

        @Override
        public Enumeration<Object> elements() {
            List<Object> statusVals = new ArrayList<>();
            for (String key : KEYS_ALL) {
                statusVals.add(getStatusAttribute(key));
            }
            Enumeration<Object> attributeVals = null;
            if (m_dict != null) {
                attributeVals = m_dict.elements();
            }
            return new ExtendedEnumeration<>(attributeVals, statusVals, true);
        }

        @Override
        public Object get(Object key) {
            for (String s : KEYS_ALL) {
                if (s.equals(key)) {
                    return getStatusAttribute((String) key);
                }
            }
            // ACE-509 - make sure we've got a proper target object to work on...
            if (m_targetObject == null) {
                return null;
            }
            String tag = m_targetObject.getTag((String)key);
            String attr = m_targetObject.getAttribute((String)key);
            if (tag == null) {
                return attr;
            }
            else if (attr == null) {
                return tag;
            }
            else {
                return new String[] {attr, tag};
            }
        }

        @Override
        public boolean isEmpty() {
            // This is always false, since we always have the status attributes.
            return false;
        }

        @Override
        public Enumeration<String> keys() {
            List<String> statusKeys = new ArrayList<>();
            for (String key : KEYS_ALL) {
                statusKeys.add(key);
            }
            Enumeration<String> attributeKeys = null;
            if (m_dict != null) {
                attributeKeys = m_dict.keys();
            }
            return new ExtendedEnumeration<>(attributeKeys, statusKeys, false);
        }

        @Override
        public Object put(String key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object remove(Object key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            int result = 0;
            Enumeration<String> keys = keys();
            while (keys.hasMoreElements()) {
                result++;
                keys.nextElement();
            }
            return result;
        }
    }

    public String getAssociationFilter(Map<String, String> properties) {
        throw new UnsupportedOperationException("A StatefulTargetObject cannot return a filter; use the underlying TargetObject instead.");
    }

    public int getCardinality(Map<String, String> properties) {
        return Integer.MAX_VALUE;
    }

    public Comparator getComparator() {
        return null;
    }
    
    @Override
    public String toString() {
    	return "StatefulTargetObjectImpl[" + getStatusAttribute(KEY_ID) + " R: " + getRegistrationState() + " A: " + getApprovalState() + " S: " + getStoreState() + " P: " + getProvisioningState() + "]";
    }

    public void resetApprovalState() {
        setApprovalState(ApprovalState.Unapproved);
    }

    @Override
    public void notifyChanged() {
    }
}