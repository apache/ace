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
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.RepositoryUtil;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.DeploymentVersionObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.DeploymentVersionRepository;
import org.apache.ace.client.repository.repository.GatewayRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.log.LogDescriptor;
import org.apache.ace.log.LogEvent;
import org.apache.ace.server.log.store.LogStore;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * Implements the StatefulGatewayRepository. If an <code>AuditLogStore</code> is present,
 * it will be used; it is assumed that the auditlog store is up to date.
 */
public class StatefulGatewayRepositoryImpl implements StatefulGatewayRepository, EventHandler {
    private BundleContext m_context; /*Injected by dependency manager*/
    private ArtifactRepository m_artifactRepository; /*Injected by dependency manager*/
    private GatewayRepository m_gatewayRepository; /*Injected by dependency manager*/
    private DeploymentVersionRepository m_deploymentRepository; /*Injected by dependency manager*/
    private LogStore m_auditLogStore; /*Injected by dependency manager*/
    private EventAdmin m_eventAdmin; /*Injected by dependency manager*/
    private LogService m_log; /*Injected by dependency manager*/
    private BundleHelper m_bundleHelper; /*Injected by dependency manager*/
    //TODO: Make the concurrencyLevel of this concurrent hashmap settable?
    private Map<String, StatefulGatewayObjectImpl> m_repository = new ConcurrentHashMap<String, StatefulGatewayObjectImpl>();

    public StatefulGatewayObject create(Map<String, String> attributes, Map<String, String> tags) throws IllegalArgumentException {
        throw new UnsupportedOperationException("Creating StatefulGatewayObjects is not supported.");
    }

    public List<StatefulGatewayObject> get() {
        synchronized(m_repository) {
            List<StatefulGatewayObject> result = new ArrayList<StatefulGatewayObject>();
            for (StatefulGatewayObjectImpl sgoi : m_repository.values()) {
                result.add(sgoi);
            }
            return result;
        }
    }

    public List<StatefulGatewayObject> get(Filter filter) {
        synchronized(m_repository) {
            List<StatefulGatewayObject> result = new ArrayList<StatefulGatewayObject>();
            for (StatefulGatewayObject entry : m_repository.values()) {
                if (filter.match(entry.getDictionary())) {
                    result.add(entry);
                }
            }
            return result;
        }
    }

    public void remove(StatefulGatewayObject entity) {
        throw new UnsupportedOperationException("Removing StatefulGatewayObjects is not supported.");
    }

    public StatefulGatewayObject preregister(Map<String, String> attributes, Map<String, String> tags) {
        synchronized(m_repository) {
            GatewayObject go = m_gatewayRepository.create(attributes, tags);
            return createStateful(go.getID());
        }
    }

    public void unregister(String gatewayID) {
        synchronized(m_repository) {
            GatewayObject go = getGatewayObject(gatewayID);
            if (go == null) {
                throw new IllegalArgumentException(gatewayID + " does not represent a GatewayObject.");
            }
            else {
                m_gatewayRepository.remove(go);
                // No need to inform the stateful representation; this will be done by the event handler.
            }
        }
    }

    public void refresh() {
        populate();
    }

    /**
     * Gets the <code>GatewayObject</code> which is identified by the <code>gatewayID</code>.
     * @param gatewayID A string representing a gateway ID.
     * @return The <code>GatewayObject</code> from the <code>GatewayRepository</code> which has the given
     * ID, or <code>null</code> if none can be found.
     */
    GatewayObject getGatewayObject(String gatewayID) {
//        synchronized(m_repository) {
            try {
                List<GatewayObject> gateways = m_gatewayRepository.get(m_context.createFilter("(" + GatewayObject.KEY_ID + "=" + RepositoryUtil.escapeFilterValue(gatewayID) + ")"));
                if ((gateways != null) && (gateways.size() == 1)) {
                    return gateways.get(0);
                }
                else {
                    return null;
                }
            }
            catch (InvalidSyntaxException e) {
                // The filter syntax is illegal, probably a bad gateway ID.
                return null;
            }
//        }
    }

    /**
     * Gets the stateful representation of the given gateway ID.
     * @param gatewayID A string representing a gateway ID.
     * @return The <code>StatefulGatewayObjectImpl</code> which handles the given ID,
     * or <code>null</code> if none can be found.
     */
    StatefulGatewayObjectImpl getStatefulGatewayObject(String gatewayID) {
        synchronized(m_repository) {
            return m_repository.get(gatewayID);
        }
    }

    /**
     * Creates and registers a new stateful gateway object based on the given ID.
     * @param gatewayID A string representing a gateway ID.
     * @return The newly created and registered <code>StatefulGatewayObjectImpl</code>.
     */
    private StatefulGatewayObjectImpl createStateful(String gatewayID) {
        synchronized(m_repository) {
            StatefulGatewayObjectImpl result = new StatefulGatewayObjectImpl(this, gatewayID);
            if (add(result)) {
                return result;
            }
            else {
                throw new IllegalArgumentException("The StateGatewayObject " + gatewayID + " already exists.");
            }
        }
    }

    /**
     * Removes the given entity from this object's repository, and notifies
     * interested parties of this.
     * @param entity The StatefulGatewayObjectImpl to be removed.
     */
    void removeStateful(StatefulGatewayObjectImpl entity) {
        synchronized(m_repository) {
            m_repository.remove(entity.getID());
            notifyChanged(entity, StatefulGatewayObject.TOPIC_REMOVED);
        }
    }

    /**
     * Adds the given stateful object to this object's repository, and notifies
     * interested parties of this change.
     * @param sgoi A <code>StatefulGatewayObjectImpl</code> to be registered.
     * @return <code>true</code> when this object has been added to the repository
     * and listeners have been notified, <code>false</code> otherwise.
     */
    boolean add(StatefulGatewayObjectImpl sgoi) {
        if (!m_repository.containsKey(sgoi)) {
            m_repository.put(sgoi.getID(), sgoi);
            notifyChanged(sgoi, StatefulGatewayObject.TOPIC_ADDED);
            return true;
        }
        return false;
    }

    private Comparator<LogEvent> m_auditEventComparator = new Comparator<LogEvent>() {
        public int compare(LogEvent left, LogEvent right) {
            if (left.getLogID() == right.getLogID()) {
                return (int) (left.getTime() - right.getTime());
            }
            else {
                return (int) (left.getLogID() - right.getLogID());
            }
        }
    };

    /**
     * Gets all auditlog events which are related to a given gateway ID.
     * @param gatewayID A string representing a gateway ID.
     * @return a list of <code>AuditEvent</code>s related to this gateway ID,
     * ordered in the order they happened. If no events can be found, and empty list will be returned.
     */
    List<LogEvent> getAuditEvents(String gatewayID) {
        return getAuditEvents(getAllDescriptors(gatewayID));
    }

    /**
     * Gets all auditlog descriptors which are related to a given gateway.
     * @param gatewayID The gateway ID
     * @return A list of LogDescriptors, in no particular order.
     */
    List<LogDescriptor> getAllDescriptors(String gatewayID) {
        List<LogDescriptor> result = new ArrayList<LogDescriptor>();
        try {
            List<LogDescriptor> descriptors = m_auditLogStore.getDescriptors(gatewayID);
            if (descriptors != null) {
                result = descriptors;
            }
        }
        catch (IOException e) {
            // Too bad, but not much we can do.
            m_log.log(LogService.LOG_INFO, "Error getting descriptors from auditlog store: ", e);
        }
        return result;
    }

    /**
     * Gets all audit log events for a gateway is has not yet 'seen'.
     * @param all A list of all <code>LogDescriptor</code> from which to filter
     * the new ones.
     * @param seen A list of <code>LogDescriptor</code> objects, which indicate
     * the items the gateway has already processed.
     * @return All AuditLog events that are in the audit store, but are not identified
     * by <code>oldDescriptors</code>, ordered by 'happened-before'.
     */
    List<LogEvent> getAuditEvents(List<LogDescriptor> events) {
        // Get all events from the audit log store, if possible.
        List<LogEvent> result = new ArrayList<LogEvent>();
        for (LogDescriptor l : events) {
            try {
                result.addAll(m_auditLogStore.get(l));
            }
            catch (IOException e) {
                // too bad, but not much to do.
                m_log.log(LogService.LOG_INFO, "Error getting contents from auditlog store: ", e);
            }
        }

        Collections.sort(result, m_auditEventComparator);
        return result;
    }

    List<LogDescriptor> diffLogDescriptorLists(List<LogDescriptor> all, List<LogDescriptor> seen) {
        List<LogDescriptor> descriptors = new ArrayList<LogDescriptor>();

        // Find out what events should be returned
        for (LogDescriptor s : all) {
            LogDescriptor diffs = s;
            for (LogDescriptor d : seen) {
                if ((s.getLogID() == d.getLogID()) && (s.getGatewayID().equals(d.getGatewayID()))) {
                    diffs = new LogDescriptor(s.getGatewayID(), s.getLogID(), d.getRangeSet().diffDest(s.getRangeSet()));
                }
            }
            descriptors.add(diffs);
        }
        return descriptors;
    }

    /**
     * See {@link DeploymentRepository#getDeploymentVersion(java.lang.String)}.
     */
    DeploymentVersionObject getMostRecentDeploymentVersion(String gatewayID) {
        return m_deploymentRepository.getMostRecentDeploymentVersion(gatewayID);
    }

    /**
     * Based on the information in this stateful object, creates a <code>GatewayObject</code>
     * in the <code>GatewayRepository</code>.
     * This function is intended to be used for gateways which are not yet represented
     * in the <code>GatewayRepository</code>; if they already are, an <code>IllegalArgumentException</code>
     * will be thrown.
     * @param gatewayID A string representing the ID of the new gateway.
     */
    void register(String gatewayID) {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(GatewayObject.KEY_ID, gatewayID);
        Map<String, String> tags = new HashMap<String, String>();
        m_gatewayRepository.create(attr, tags);
        getStatefulGatewayObject(gatewayID).updateGatewayObject(false);
    }

    /**
     * Notifies interested parties of a change to a <code>StatefulGatewayObject</code>.
     * @param sgoi The <code>StatefulGatewayObject</code> which has changed.
     * @param topic A topic string for posting the event.
     * @param additionalProperties A Properties event, already containing some extra properties. If
     * RepositoryObject.EVENT_ENTITY is used, it will be overwritten.
     */
    void notifyChanged(StatefulGatewayObject sgoi, String topic, Properties additionalProperties) {
        additionalProperties.put(RepositoryObject.EVENT_ENTITY, sgoi);
        m_eventAdmin.postEvent(new Event(topic, (Dictionary) additionalProperties));
    }

    /**
     * Notifies interested parties of a change to a <code>StatefulGatewayObject</code>.
     * @param sgoi The <code>StatefulGatewayObject</code> which has changed.
     * @param topic A topic string for posting the event.
     */
    void notifyChanged(StatefulGatewayObject sgoi, String topic) {
        notifyChanged(sgoi, topic, new Properties());
    }

    /**
     * Reads the information sources to generate the stateful objects.
     */
    private void populate() {
        synchronized(m_repository) {
            List<StatefulGatewayObjectImpl> touched = new ArrayList<StatefulGatewayObjectImpl>();
            touched.addAll(parseGatewayRepository());
            touched.addAll(parseAuditLog());

            // Now, it is possible we have not touched all objects. Find out which these are, and make
            // them check whether they should still exist.
            List<StatefulGatewayObjectImpl> all = new ArrayList<StatefulGatewayObjectImpl>(m_repository.values());
            all.removeAll(touched);
            for (StatefulGatewayObjectImpl sgoi : all) {
                sgoi.updateGatewayObject(false);
                sgoi.updateDeploymentVersions(null);
                sgoi.updateAuditEvents(true);
            }
            // Furthermore, for all those we _did_ see, we need to make sure their deployment versions
            // are up to date.
            for (StatefulGatewayObjectImpl sgoi : touched) {
                sgoi.updateDeploymentVersions(null);
                sgoi.updateGatewayObject(true);
            }
        }
    }

    /**
     * Checks all inhabitants of the <code>GatewayRepository</code> to see
     * whether we already have a stateful representation of them.
     * @param needsVerify states whether the objects which are 'touched' by this
     * actions should verify their existence.
     * @return A list of all the gateway objects that have been touched by this action.
     */
    private List<StatefulGatewayObjectImpl> parseGatewayRepository() {
        List<StatefulGatewayObjectImpl> result = new ArrayList<StatefulGatewayObjectImpl>();
        for (GatewayObject go : m_gatewayRepository.get()) {
            StatefulGatewayObjectImpl sgoi = getStatefulGatewayObject(go.getID());
            if (sgoi == null) {
                result.add(createStateful(go.getID()));
            }
            else {
                result.add(sgoi);
                sgoi.updateGatewayObject(false);
            }
        }
        return result;
    }

    /**
     * Checks the audit log to see whether we already have a
     * stateful object for all gateways mentioned there.
     * @param needsVerify states whether the objects which are 'touched' by this
     * actions should verify their existence.
     */
    private List<StatefulGatewayObjectImpl> parseAuditLog() {
        List<StatefulGatewayObjectImpl> result = new ArrayList<StatefulGatewayObjectImpl>();
        List<LogDescriptor> descriptors = null;
        try {
            descriptors = m_auditLogStore.getDescriptors();
        }
        catch (IOException e) {
            // Not much to do.
        }
        if (descriptors == null) {
            // There is no audit log available, or it failed getting the logdescriptors.
            return result;
        }

        Set<String> gatewayIDs = new HashSet<String>();
        for (LogDescriptor l : descriptors) {
            gatewayIDs.add(l.getGatewayID());
        }

        /* Note: the parsing of the audit log and the creation/notification of the
         * stateful objects has been separated, to prevent calling updateAuditEvents()
         * multiple times on gateways which have more than one log.
         */
        synchronized(m_repository) {
            for (String gatewayID : gatewayIDs) {
                StatefulGatewayObjectImpl sgoi = getStatefulGatewayObject(gatewayID);
                if (sgoi == null) {
                    result.add(createStateful(gatewayID));
                }
                else {
                    result.add(sgoi);
                    sgoi.updateAuditEvents(false);
                }
            }
        }
        return result;
    }

    /**
     * Approves the changes that will happen to the gateway based on the
     * changes in the shop by generating a new deployment version.
     * @param gatewayID A string representing a gateway ID.
     * @return The version identifier of the new deployment package.
     * @throws IOException When there is a problem generating the deployment version.
     */
    String approve(String gatewayID) throws IOException {
        return generateDeploymentVersion(gatewayID).getVersion();
    }

    /**
     * Generates an array of bundle URLs which have to be deployed on
     * the gateway, given the current state of the shop.
     * TODO: In the future, we want to add support for multiple shops.
     * TODO: Is this prone to concurrency issues with changes license- and
     * group objects?
     * @param gatewayID A string representing a gateway.
     * @return An array of artifact URLs.
     * @throws IOException When there is a problem processing an artifact for deployment.
     */
    DeploymentArtifact[] getNecessaryDeploymentArtifacts(String gatewayID, String version) throws IOException {
        GatewayObject go = getGatewayObject(gatewayID);

        Map<ArtifactObject, String> bundles = new HashMap<ArtifactObject, String>();
        Map<ArtifactObject, String> artifacts = new HashMap<ArtifactObject, String>();

        // First, find all basic bundles and artifacts. An while we're traversing the
        // tree of objects, build the tree of properties.
        if (go != null) {
            for (LicenseObject license : go.getLicenses()) {
                for (GroupObject group : license.getGroups()) {
                    for (ArtifactObject artifact : group.getArtifacts()) {
                        if (m_bundleHelper.canUse(artifact)) {
                            bundles.put(artifact, m_bundleHelper.getResourceProcessorPIDs(artifact));
                        }
                        else {
                            artifacts.put(artifact, artifact.getProcessorPID());
                        }
                    }
                }
            }
        }

        // Find all processors
        Map<String, ArtifactObject> allProcessors = new HashMap<String, ArtifactObject>();
        for (ArtifactObject bundle : m_artifactRepository.getResourceProcessors()) {
            allProcessors.put(m_bundleHelper.getResourceProcessorPIDs(bundle), bundle);
        }

        // Determine all resource processors we need
        for (String processor : artifacts.values()) {
            if (!bundles.containsValue(processor)) {
                ArtifactObject bundle = allProcessors.get(processor);
                if (bundle == null) {
                    m_log.log(LogService.LOG_ERROR, "Unable to create deployment version: there is no resource processing bundle available that publishes " + processor);
                    throw new IllegalStateException("Unable to create deployment version: there is no resource processing bundle available that publishes " + processor);
                }
                bundles.put(bundle, processor);
            }
        }

        List<DeploymentArtifact> result = new ArrayList<DeploymentArtifact>();

        for (ArtifactObject bundle : bundles.keySet()) {
            Map<String, String> directives = new HashMap<String, String>();
            if (m_bundleHelper.isResourceProcessor(bundle)) {
                // it's a resource processor, mark it as such.
                directives.put(DeploymentArtifact.DIRECTIVE_ISCUSTOMIZER, "true");
            }
            directives.put(BundleHelper.KEY_SYMBOLICNAME, m_bundleHelper.getSymbolicName(bundle));
            String bundleVersion = m_bundleHelper.getVersion(bundle);
            if (bundleVersion != null) {
                directives.put(BundleHelper.KEY_VERSION, bundleVersion);
            }
            directives.put(DeploymentArtifact.DIRECTIVE_KEY_BASEURL, bundle.getURL());
            result.add(m_deploymentRepository.createDeploymentArtifact(bundle.getURL(), directives));
        }

        for (ArtifactObject artifact : artifacts.keySet()) {
            Map<String, String> directives = new HashMap<String, String>();
            directives.put(DeploymentArtifact.DIRECTIVE_KEY_PROCESSORID, artifact.getProcessorPID());
            directives.put(DeploymentArtifact.DIRECTIVE_KEY_BASEURL, artifact.getURL());
            result.add(m_deploymentRepository.createDeploymentArtifact(m_artifactRepository.preprocessArtifact(artifact, go, gatewayID, version), directives));
        }

        return result.toArray(new DeploymentArtifact[result.size()]);
    }

    /**
     * Quick method to find all artifacts that need to be deployed to a gateway.
    */
    ArtifactObject[] getNecessaryArtifacts(String gatewayID) {
        List<ArtifactObject> result = new ArrayList<ArtifactObject>();
        GatewayObject go = getGatewayObject(gatewayID);

        Map<String, ArtifactObject> allProcessors = new HashMap<String, ArtifactObject>();
        for (ArtifactObject bundle : m_artifactRepository.getResourceProcessors()) {
            allProcessors.put(m_bundleHelper.getResourceProcessorPIDs(bundle), bundle);
        }

        if (go != null) {
            for (LicenseObject license : go.getLicenses()) {
                for (GroupObject group : license.getGroups()) {
                    for (ArtifactObject artifact : group.getArtifacts()) {
                        result.add(artifact);
                        if (!m_bundleHelper.canUse(artifact)) {
                            ArtifactObject processor = allProcessors.get(artifact.getProcessorPID());
                            if (processor == null) {
                                // this means we cannot create a useful version; return null.
                                return null;
                            }
                            result.add(processor);
                        }
                    }
                }
            }
        }

        return result.toArray(new ArtifactObject[result.size()]);
    }

    /**
     * Generates a new deployment version for the the given gateway,
     * based on the bundles it is linked to by the licenses it is
     * associated to.
     * @param gatewayID A string representing a gateway.
     * @return A new DeploymentVersionObject, representing this new version for the gateway.
     * @throws IOException When there is a problem determining the artifacts to be deployed.
     */
    DeploymentVersionObject generateDeploymentVersion(String gatewayID) throws IOException {
        Map<String, String> attr = new HashMap<String, String>();
        attr.put(DeploymentVersionObject.KEY_GATEWAYID, gatewayID);
        Map<String, String> tags = new HashMap<String, String>();

        DeploymentVersionObject mostRecentDeploymentVersion = getMostRecentDeploymentVersion(gatewayID);
        String nextVersion;
        if (mostRecentDeploymentVersion == null) {
            nextVersion = nextVersion(null);
        }
        else {
            nextVersion = nextVersion(mostRecentDeploymentVersion.getVersion());
        }
        attr.put(DeploymentVersionObject.KEY_VERSION, nextVersion);

        synchronized(m_repository) {
            DeploymentVersionObject result = m_deploymentRepository.create(attr, tags, getNecessaryDeploymentArtifacts(gatewayID, nextVersion));

            StatefulGatewayObjectImpl sgoi = getStatefulGatewayObject(gatewayID);
            if (sgoi == null) {
                createStateful(gatewayID);
            }
            else {
                sgoi.updateDeploymentVersions(result);
            }

            return result;
        }
    }

    /**
     * Generates the next version, based on the version passed in.
     * The version is assumed to be an OSGi-version; for now, the next
     * 'major' version is generated. In the future, we might want to do
     * 'smarter' things here, like checking the impact of a new version
     * and use the minor and micro versions, or attach some qualifier.
     * @param version A string representing a deployment version's version.
     * @return A string representing the next version.
     */
    private static String nextVersion(String version) {
        try {
            Version v = new Version(version);
            Version result = new Version(v.getMajor() + 1, 0, 0);
            return result.toString();
        }
        catch (Exception iae) {
            // Basically, if anything goes wrong, we assume we want to start a new version at 1.
            return "1.0.0";
        }
    }

    public void handleEvent(Event event) {
        if (event.getTopic().equals(GatewayObject.TOPIC_ADDED) || event.getTopic().equals(GatewayObject.TOPIC_REMOVED)) {
            synchronized(m_repository) {
                String id = ((GatewayObject) event.getProperty(RepositoryObject.EVENT_ENTITY)).getID();
                StatefulGatewayObjectImpl sgoi = getStatefulGatewayObject(id);
                if (sgoi == null) {
                    createStateful(id);
                }
                else {
                    sgoi.updateGatewayObject(true);
                }
            }
        }
        else if (event.getTopic().equals(DeploymentVersionObject.TOPIC_ADDED) || event.getTopic().equals(DeploymentVersionObject.TOPIC_REMOVED)) {
            synchronized(m_repository) {
                DeploymentVersionObject deploymentVersionObject = ((DeploymentVersionObject) event.getProperty(RepositoryObject.EVENT_ENTITY));
                String id = deploymentVersionObject.getGatewayID();
                StatefulGatewayObjectImpl sgoi = getStatefulGatewayObject(id);
                if (sgoi == null) {
                    createStateful(id);
                }
                else {
                    sgoi.updateDeploymentVersions(deploymentVersionObject);
                }
            }
        }
        else if (event.getTopic().equals(RepositoryAdmin.TOPIC_LOGIN)) {
            synchronized(m_repository) {
                populate();
            }
        }
        else if (event.getTopic().equals(RepositoryAdmin.TOPIC_REFRESH)) {
            synchronized(m_repository) {
                populate();
            }
        }
        else {
            // Something else has changed; however, the entire shop may have an influence on
            // any gateway, so recheck everything.
            synchronized(m_repository) {
                for (StatefulGatewayObjectImpl sgoi : m_repository.values()) {
                    sgoi.determineStatus();
                }
            }
        }
    }

    boolean needsNewVersion(ArtifactObject artifact, String gatewayID, String version) {
        return m_artifactRepository.needsNewVersion(artifact, getGatewayObject(gatewayID), gatewayID, version);
    }
}
