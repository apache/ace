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

import org.apache.ace.client.repository.Associatable;
import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.License2GatewayAssociation;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;

/**
 * A <code>StatefulGatewayObjectImpl</code> uses the interface of a <code>StatefulGatewayObject</code>,
 * but delegates most of its calls to either an embedded <code>GatewayObject</code>, or to its
 * parent <code>StatefulGatewayRepository</code>. Once created, it will handle its own lifecyle
 * and remove itself once is existence is no longer necessary.
 */
public class StatefulGatewayObjectImpl implements StatefulGatewayObject {
    private final StatefulGatewayRepositoryImpl m_repository;
    private final Object m_lock = new Object();
    private GatewayObject m_gatewayObject;
    private List<LogDescriptor> m_processedAuditEvents = new ArrayList<LogDescriptor>();
    private Map<String, String> m_attributes = new HashMap<String, String>();
    /** This boolean is used to suppress STATUS_CHANGED events during the creation of the object.*/
    private boolean m_inConstructor = true;

    /**
     * Creates a new <code>StatefulGatewayObjectImpl</code>. After creation, it will have the
     * most recent data available, and has verified its own reasons for existence.
     * @param repository The parent repository of this object.
     * @param gatewayID A string representing a gateway ID.
     */
    StatefulGatewayObjectImpl(StatefulGatewayRepositoryImpl repository, String gatewayID) {
        m_repository = repository;
        addStatusAttribute(KEY_ID, gatewayID);
        updateGatewayObject(false);
        updateAuditEvents(false);
        updateDeploymentVersions(null);
        verifyExistence();
        m_inConstructor = false;
    }

    public String approve() throws IllegalStateException {
        try {
            String version = m_repository.approve(getID());
            setStoreState(StoreState.Approved);
            return version;
        }
        catch (IOException e) {
            throw new IllegalStateException("Problem generating new deployment version: " + e);
        }
    }

    public List<LogEvent> getAuditEvents() {
        return m_repository.getAuditEvents(getID());
    }

    public String getCurrentVersion() {
        DeploymentVersionObject version = m_repository.getMostRecentDeploymentVersion(getID());
        if (version == null) {
            return StatefulGatewayObject.UNKNOWN_VERSION;
        }
        else {
            return version.getVersion();
        }
    }

    public void register() throws IllegalStateException {
        m_repository.register(getID());
    }

    public boolean isRegistered() {
        synchronized(m_lock) {
            return (m_gatewayObject != null);
        }
    }

    public GatewayObject getGatewayObject() {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject;
        }
    }

    public DeploymentArtifact[] getArtifactsFromDeployment() {
        synchronized(m_lock) {
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
        synchronized(m_lock) {
            return Boolean.parseBoolean(getStatusAttribute(KEY_LAST_INSTALL_SUCCESS));
        }
    }

    public String getLastInstallVersion() {
        synchronized(m_lock) {
            return getStatusAttribute(KEY_LAST_INSTALL_VERSION);
        }
    }

    public void acknowledgeInstallVersion(String version) {
        synchronized(m_lock) {
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

    public StoreState getStoreState() {
        String statusAttribute = getStatusAttribute(KEY_STORE_STATE);
        if (statusAttribute != null) {
            return StoreState.valueOf(statusAttribute);
        }
        return StoreState.New;
    }

    /**
     * Signals this object that there has been a change to the <code>GatewayObject</code> it represents.
     * @param needsVerify States whether this update should make the object check for its
     * reasons for existence.
     */
    void updateGatewayObject(boolean needsVerify) {
        synchronized(m_lock) {
            m_gatewayObject = m_repository.getGatewayObject(getID());
            determineRegistrationState();
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
        synchronized(m_lock) {
            determineProvisioningState();
            if (needsVerify) {
                verifyExistence();
            }
        }
    }

    /**
     * Signals this object that a new deployment version has been created in relation
     * to the gatewayID this object manages.
     */
    void updateDeploymentVersions(DeploymentVersionObject deploymentVersionObject) {
        synchronized(m_lock) {
            determineProvisioningState();
            determineStoreState(deploymentVersionObject);
        }
    }

    /**
     * Based on the information about a <code>GatewayObject</code>, the
     * <code>AuditEvent</code>s available, and the deployment information that
     * the parent repository can give, determines the status of this gateway.
     */
    void determineStatus() {
        determineRegistrationState();
        determineProvisioningState();
        determineStoreState(null);
        verifyExistence();
    }

    private void determineRegistrationState() {
        synchronized(m_lock) {
            if (!isRegistered()) {
                setRegistrationState(RegistrationState.Unregistered);
            }
            else {
                setRegistrationState(RegistrationState.Registered);
            }
        }
    }

    private void determineStoreState(DeploymentVersionObject deploymentVersionObject) {
        synchronized(m_lock) {
            List<String> fromShop = new ArrayList<String>();
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

            List<String> fromDeployment = new ArrayList<String>();
            for (DeploymentArtifact da : getArtifactsFromDeployment()) {
                fromDeployment.add(da.getDirective(DeploymentArtifact.DIRECTIVE_KEY_BASEURL));
            }

            if ((mostRecentVersion == null) && fromShop.isEmpty()) {
                setStoreState(StoreState.New);
            }
            else if (fromShop.containsAll(fromDeployment) && fromDeployment.containsAll(fromShop)) {
                // great, we have the same artifacts. But... do they need to be reprocessed?
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
         * in time, to find either and INSTALL or a COMPLETE event. A INSTALL event gives us a version,
         * and tells us we're in InProgress. A COMPLETE tells gives us a version, and a success. The success
         * will be stored, and also sets the state to OK or Failed, unless the version we found has already been
         * acknowledged, the the state is set to Idle. Also, if there is no information whatsoever, we assume Idle.
         */
        synchronized(m_lock) {
            List<LogDescriptor> allDescriptors = m_repository.getAllDescriptors(getID());
            List<LogDescriptor> newDescriptors = m_repository.diffLogDescriptorLists(allDescriptors, m_processedAuditEvents);

            List<LogEvent> newEvents = m_repository.getAuditEvents(newDescriptors);
            for (int position = newEvents.size() - 1; position >= 0; position--) {
                String currentVersion = (String) newEvents.get(position).getProperties().get(AuditEvent.KEY_VERSION);
                if (newEvents.get(position).getType() == AuditEvent.DEPLOYMENTCONTROL_INSTALL) {
                    addStatusAttribute(KEY_LAST_INSTALL_VERSION, currentVersion);
                    setProvisioningState(ProvisioningState.InProgress);
                    sendNewAuditlog(newDescriptors);
                    m_processedAuditEvents = allDescriptors;
                    return;
                }
                else if (newEvents.get(position).getType() == AuditEvent.DEPLOYMENTADMIN_COMPLETE) {
                    addStatusAttribute(KEY_LAST_INSTALL_VERSION, currentVersion);
                    if ((currentVersion != null) && currentVersion.equals(getStatusAttribute(KEY_ACKNOWLEDGED_INSTALL_VERSION))) {
                        setProvisioningState(ProvisioningState.Idle);
                        sendNewAuditlog(newDescriptors);
                        m_processedAuditEvents = allDescriptors;
                        return;
                    }
                    else {
                        String value = (String) newEvents.get(position).getProperties().get(AuditEvent.KEY_SUCCESS);
                        addStatusAttribute(KEY_LAST_INSTALL_SUCCESS, value);
                        if (Boolean.parseBoolean(value)) {
                            setProvisioningState(ProvisioningState.OK);
                            sendNewAuditlog(newDescriptors);
                            m_processedAuditEvents = allDescriptors;
                            return;
                        }
                        else {
                            setProvisioningState(ProvisioningState.Failed);
                            sendNewAuditlog(newDescriptors);
                            m_processedAuditEvents = allDescriptors;
                            return;
                        }
                    }
                }
            }

            if (m_processedAuditEvents.isEmpty()) {
                setProvisioningState(ProvisioningState.Idle);
            }
            sendNewAuditlog(newDescriptors);
            m_processedAuditEvents = allDescriptors;
        }
    }

    private void sendNewAuditlog(List<LogDescriptor> events) {
        // Check whether there are actually events in the list.
        boolean containsData = false;
        for (LogDescriptor l : events) {
            containsData |= (l.getRangeSet().getHigh() != 0);
        }

        if (containsData) {
            Properties props = new Properties();
            props.put(StatefulGatewayObject.KEY_AUDITEVENTS, events);
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
            approve();
        }
    }

    /**
     * Verifies that this object should still be around. If the gateway is represents
     * shows up in at least the gateway repository or the auditlog, it has a reason
     * to exists; if not, it doesn't. When it is no longer necessary, it will remove itself
     * from the parent repository.
     * @return Whether or not this object should still exist.
     */
    boolean verifyExistence() {
        synchronized(m_lock) {
            if ((m_gatewayObject == null) && ((m_processedAuditEvents == null) || m_processedAuditEvents.isEmpty())) {
                m_repository.removeStateful(this);
                return false;
            }
            return true;
        }
    }

    /**
     * Helper method for the delegate methods below: most of these delegate their calls to a
     * <code>GatewayObject</code>, but in order to do so, one must be present.
     */
    private void ensureGatewayPresent() {
        if ((m_gatewayObject == null)) {
            throw new IllegalStateException("This StatefulGatewayObject is not backed by a GatewayObject.");
            // NOTE: we do not check the isDeleted state; the GatewayObject itself will notify the user of this.
        }
    }

    @Override
    public boolean equals(Object o) {
        if ((o == null) || !(o instanceof StatefulGatewayObject)) {
            return false;
        }
        return getID() == ((StatefulGatewayObject) o).getID();
    }

    private void addStatusAttribute(String key, String value) {
        m_attributes.put(key, value);
    }

    private String getStatusAttribute(String key) {
        return m_attributes.get(key);
    }

    /* ******************
     * Delegates to GatewayObject
     */

    public String getID() {
        return getStatusAttribute(KEY_ID);
    }

    public boolean isDeleted() {
        return !verifyExistence();
    }

    public List<License2GatewayAssociation> getAssociationsWith(LicenseObject license) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getAssociationsWith(license);
        }
    }

    public List<LicenseObject> getLicenses() {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getLicenses();
        }
    }

    public String addAttribute(String key, String value) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.addAttribute(key, value);
        }
    }

    public String addTag(String key, String value) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.addTag(key, value);
        }
    }

    public String getAttribute(String key) {
        // retrieve from both
        synchronized(m_lock) {
            if (Arrays.binarySearch(KEYS_ALL, key) >= 0) {
                return getStatusAttribute(key);
            }
            ensureGatewayPresent();
            return m_gatewayObject.getAttribute(key);
        }
    }

    public Enumeration<String> getAttributeKeys() {
        synchronized(m_lock) {
            List<String> statusKeys = new ArrayList<String>();
            for (String s : KEYS_ALL) {
                statusKeys.add(s);
            }
            Enumeration<String> attributeKeys = null;
            if (m_gatewayObject != null) {
                attributeKeys = m_gatewayObject.getAttributeKeys();
            }
            return new ExtendedEnumeration<String>(attributeKeys, statusKeys, true);
        }
    }

    public Dictionary<String, Object> getDictionary() {
        // build our own dictionary
        synchronized(m_lock) {
            return new StatefulGatewayObjectDictionary();
        }
    }

    public String getTag(String key) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getTag(key);
        }
    }

    public Enumeration<String> getTagKeys() {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getTagKeys();
        }
    }

    public boolean getAutoApprove() {
        synchronized(m_lock) {
            if (m_gatewayObject != null) {
                return m_gatewayObject.getAutoApprove();
            }
            else {
                return false;
            }

        }
    }

    public void setAutoApprove(boolean approve) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            m_gatewayObject.setAutoApprove(approve);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Associatable> void add(Association association, Class<T> clazz) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            m_gatewayObject.add(association, clazz);
        }
    }

    public <T extends Associatable> List<T> getAssociations(Class<T> clazz) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getAssociations(clazz);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Associatable, A extends Association> List<A> getAssociationsWith(Associatable other, Class<T> clazz, Class<A> associationType) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getAssociationsWith(other, clazz, associationType);
        }
    }

    public <T extends Associatable> boolean isAssociated(Object obj, Class<T> clazz) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.isAssociated(obj, clazz);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Associatable> void remove(Association association, Class<T> clazz) {
        synchronized(m_lock) {
            ensureGatewayPresent();
            m_gatewayObject.remove(association, clazz);
        }
    }

    public String getDefinition() {
        synchronized(m_lock) {
            ensureGatewayPresent();
            return m_gatewayObject.getDefinition();
        }
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

    private class StatefulGatewayObjectDictionary extends Dictionary<String, Object> {
        private final Dictionary<String, Object> m_dict;

        StatefulGatewayObjectDictionary() {
            if (m_gatewayObject != null) {
                m_dict = m_gatewayObject.getDictionary();
            }
            else {
                m_dict = null;
            }
        }

        @Override
        public Enumeration<Object> elements() {
            List<Object> statusVals = new ArrayList<Object>();
            for (String key : KEYS_ALL) {
                statusVals.add(getStatusAttribute(key));
            }
            Enumeration<Object> attributeVals = null;
            if (m_dict != null) {
                attributeVals = m_dict.elements();
            }
            return new ExtendedEnumeration<Object>(attributeVals, statusVals, true);
        }

        @Override
        public Object get(Object key) {
            for (String s : KEYS_ALL) {
                if (s.equals(key)) {
                    return getStatusAttribute((String) key);
                }
            }
            String tag = m_gatewayObject.getTag((String)key);
            String attr = m_gatewayObject.getAttribute((String)key);
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
            List<String> statusKeys = new ArrayList<String>();
            for (String key : KEYS_ALL) {
                statusKeys.add(key);
            }
            Enumeration<String> attributeKeys = null;
            if (m_dict != null) {
                attributeKeys = m_dict.keys();
            }
            return new ExtendedEnumeration<String>(attributeKeys, statusKeys, false);
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
        throw new UnsupportedOperationException("A StatefulGatewayObject cannot return a filter; use the underlying GatewayObject instead.");
    }

    public int getCardinality(Map<String, String> properties) {
        throw new UnsupportedOperationException("A StatefulGatewayObject cannot return a cardinality; use the underlying GatewayObject instead.");
    }

    @SuppressWarnings("unchecked")
    public Comparator getComparator() {
        throw new UnsupportedOperationException("A StatefulGatewayObject cannot return a comparator; use the underlying GatewayObject instead.");
    }
}
