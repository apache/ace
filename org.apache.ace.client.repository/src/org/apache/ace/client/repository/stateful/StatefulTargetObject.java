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
package org.apache.ace.client.repository.stateful;

import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.feedback.Event;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Represents the information that a <code>TargetObject</code>
 * has, plus added functionality for gathering information from a deployment repository and,
 * optionally, from an AuditLog.
 */
@ProviderType
public interface StatefulTargetObject extends RepositoryObject {

    public static final String TOPIC_ADDED = StatefulTargetObject.class.getName().replace('.', '/') + "/ADDED";
    public static final String TOPIC_REMOVED = StatefulTargetObject.class.getName().replace('.', '/') + "/REMOVED";
    public static final String TOPIC_CHANGED = StatefulTargetObject.class.getName().replace('.', '/') + "/CHANGED";
    public static final String TOPIC_STATUS_CHANGED = StatefulTargetObject.class.getName().replace('.', '/') + "/STATUS_CHANGED";
    /** Indicates a change to the audit events for the StatefulTargetObject in "entity".*/
    public static final String TOPIC_AUDITEVENTS_CHANGED = StatefulTargetObject.class.getName().replace('.', '/') + "/AUDITEVENTS_CHANGED";
    /** Key used in the event with topic <code>TOPIC_AUDITEVENTS_CHANGED</code>. Contains a List<LogDescriptor> containing all
     *  events we have not seen yet. NOTE: The first auditevent "change" causing the <code>StatefulTargetObject</code> to
     *  be instantiated will trigger a <code>TOPIC_AUDITEVENTS_CHANGED</code> event *before* a <code>TOPIC_ADDED</code> event. */
    public static final String KEY_AUDITEVENTS = "auditevents";
    public static final String TOPIC_ALL = StatefulTargetObject.class.getName().replace('.', '/') + "/*";

    public final static String KEY_ID = TargetObject.KEY_ID;
    public final static String KEY_REGISTRATION_STATE = "KEY_REGISTRATION_STATE";
    public final static String KEY_STORE_STATE = "KEY_STORE_STATE";
    public final static String KEY_PROVISIONING_STATE = "KEY_PROVISIONING_STATE";
    public final static String KEY_APPROVAL_STATE = "KEY_APPROVAL_STATE";
    public final static String KEY_LAST_INSTALL_VERSION = "KEY_LAST_INSTALL_VERSION";
    public final static String KEY_LAST_INSTALL_SUCCESS = "KEY_LAST_INSTALL_SUCCESS";
    public final static String KEY_ACKNOWLEDGED_INSTALL_VERSION = "KEY_ACKNOWLEDGED_INSTALL_VERSION";
    public final static String[] KEYS_ALL = new String[] {KEY_ID, KEY_REGISTRATION_STATE, KEY_STORE_STATE, KEY_PROVISIONING_STATE, KEY_LAST_INSTALL_VERSION, KEY_LAST_INSTALL_SUCCESS, KEY_ACKNOWLEDGED_INSTALL_VERSION};
    public static final String TARGETPROPERTIES_PREFIX = "target.";

    /**
     * Represents a current deployment package version which cannot be found, i.e.,
     * either there is no information about deployment packages in the AuditLog,
     * or no AuditLog is available.
     */
    public final static String UNKNOWN_VERSION = "(unknown)";

    /**
     * Gets the current registration status of the target.
     */
    public RegistrationState getRegistrationState();

    /**
     * Gets the current store status of the target.
     */
    public StoreState getStoreState();

    /**
     * Gets the current provisioning status of the target.
     */
    public ProvisioningState getProvisioningState();

    /**
     * Gets the most recent deployment package version on the target, according
     * to the deployment repository. If no version can be determined,
     * <code>UNKNOWN_VERSION</code> will be returned.
     */
    public String getCurrentVersion();

    /**
     * Gets the list of AuditLog Events for this target. If no auditlog events
     * can be found, and empty list will be returned. The events are ordered ascending by timestamp.
     */
    public List<Event> getAuditEvents();

    /**
     * Registers this target, which for now only exists in the AuditLog, into the
     * <code>TargetRepository</code>.
     * @throws IllegalStateException when the precondition is not met, i.e., the
     * target is not known only in the AuditLog, but also in the <code>TargetRepository</code>.
     */
    public void register() throws IllegalStateException;

    /**
     * Indicates whether this <code>StatefulTargetObject</code> is backed by a <code>TargetObject</code>.
     * @return whether this <code>StatefulTargetObject</code> is backed by a <code>TargetObject</code>.
     */
    public boolean isRegistered();

    /**
     * Approves all differences between what is currently in the shop and target operator
     * repository, and the deployment repository. This will generate a new version in the
     * deployment repository.
     * @return The number of the new version.
     * @throws IllegalStateException when it is currently not possible to create a deployment version.
     */
    public String approve();

    /**
     * Indicates whether an <code>approve()</code> is necessary, i.e., there is a difference between
     * the set of artifacts for this target according to the shop, and according to the deployment
     * repository.
     * @return <code>true</code> if there is a difference between the shop and deployment repository;
     * <code>false</code> otherwise.
     */
    public boolean needsApprove();

    /**
     * Returns the auto-approval flag for this target.
     * @return <code>true</code> if auto approve has been set;
     * <code>false</code> otherwise.
     */
    public boolean getAutoApprove();

    /**
     * Set the auto approve value for this target, the property is stored within the target
     * @param approve <code>true</code> to enable auto approve;
     * <code>false</code> otherwise.
     */
    public void setAutoApprove(boolean approve);

    /**
     * Gets the list of artifact objects that should be on the target, according to the shop.
     * @return the list of artifact objects that should be on the target, according to the shop, can only be <code>null</code> in case something went wrong gathering artifacts.
     */
    public ArtifactObject[] getArtifactsFromShop();

    /**
     * Gets the list of deployment artifacts that should be on the target, according to the deployment repository.
     * @return the list of artifact objects that should be on the target, according to the deployment repository.
     */
    public DeploymentArtifact[] getArtifactsFromDeployment();

    /**
     * Returns the latest available installed version in the auditlog.
     * @return The latest version statement from the auditlog for an install if
     * one is available; otherwise, an empty string.
     */
    public String getLastInstallVersion();

    /**
     * Returns whether the last install on the target was successful.
     * @return <code>true</code> if there information about a last install and
     * that was successful, <code>false</code> otherwise.
     */
    public boolean getLastInstallSuccess();

    /**
     * Signals to the object that the outcome of a given install on the target
     * is 'seen', and that the <code>ProvisioningState</code> can now return to <code>Idle</code>.
     * @param version A string representing a version.
     */
    public void acknowledgeInstallVersion(String version);

    /**
     * Gets the underlying <code>TargetObject</code> of this <code>StatefulTargetObject</code>.
     * @return The <code>TargetObject</code> linked to this <code>StatefulTargetObject</code>; if none
     * is available, an <code>IllegalStateException</code> will be thrown.
     */
    public TargetObject getTargetObject();

    /**
     * Returns all <code>DistributionObject</code>s this object is associated with. If there
     * are none, an empty list will be returned.
     */
    public List<DistributionObject> getDistributions();

    /**
     * Returns all associations this target has with a given distribution.
     */
    public List<Distribution2TargetAssociation> getAssociationsWith(DistributionObject distribution);

    /**
     * Gets the ID of this TargetObject.
     */
    public String getID();

    public enum RegistrationState {
        Unregistered, Registered;
    }

    public enum StoreState {
        New, Unapproved, Approved;
    }

    public enum ProvisioningState {
        Idle, InProgress, OK, Failed;
    }
    
    /** Indicates if the user has approved changes for this target or not. */
    public enum ApprovalState {
        Unapproved, Approved;
    }

    public ApprovalState getApprovalState();
}
