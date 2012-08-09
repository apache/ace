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

import java.lang.reflect.Type;
import java.util.Enumeration;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * Provides an object serializer for the entire type hierarchy of {@link RepositoryObject}s.
 */
public class RepositoryObjectSerializer implements JsonSerializer<RepositoryObject> {

    /** used in all repository objects. */
    private static final String DEFINITION = "definition";
    /** used in all repository objects. */
    private static final String TAGS = "tags";
    /** used in all repository objects. */
    private static final String ATTRIBUTES = "attributes";
    /** used in stateful target objects only. */
    private static final String STATE = "state";
    
    private static final String REGISTRATION_STATE = "registrationState";
    private static final String CURRENT_VERSION = "currentVersion";
    private static final String STORE_STATE = "storeState";
    private static final String PROVISIONING_STATE = "provisioningState";
    private static final String IS_REGISTERED = "isRegistered";
    private static final String NEEDS_APPROVAL = "needsApproval";
    private static final String AUTO_APPROVE = "autoApprove";
    private static final String ARTIFACTS_FROM_SHOP = "artifactsFromShop";
    private static final String ARTIFACTS_FROM_DEPLOYMENT = "artifactsFromDeployment";
    private static final String LAST_INSTALL_VERSION = "lastInstallVersion";
    private static final String LAST_INSTALL_SUCCESS = "lastInstallSuccess";

    /**
     * @see com.google.gson.JsonSerializer#serialize(java.lang.Object, java.lang.reflect.Type, com.google.gson.JsonSerializationContext)
     */
    public JsonElement serialize(RepositoryObject repositoryObject, Type featureType, JsonSerializationContext context) {
        // ACE-164: for stateful target objects we need some special measures to serialize it...
        if (repositoryObject instanceof StatefulTargetObject) {
            return serializeStatefulTargetObject((StatefulTargetObject) repositoryObject);
        }

        // All other repository objects can be simply serialized...
        return serializeRepositoryObject(repositoryObject);
    }

    /**
     * Custom serializer method for {@link StatefulTargetObject}s, as they have state and cannot be accessed
     * always in the same way as other repository objects. For example, when dealing with unregistered targets,
     * we cannot ask for the attributes and/or tags of a target.
     * 
     * @param targetObject the target object to serialize, cannot be <code>null</code>.
     * @return a JSON representation of the given target object, never <code>null</code>.
     */
    private JsonElement serializeStatefulTargetObject(StatefulTargetObject targetObject) {
        JsonObject result = new JsonObject();
        // ACE-243: first all the definition...
        result.addProperty(DEFINITION, targetObject.getDefinition());

        // then add all attributes
        JsonObject attr = new JsonObject();

        if (targetObject.isRegistered()) {
            Enumeration<String> keys = targetObject.getAttributeKeys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                attr.addProperty(key, targetObject.getAttribute(key));
            }
        }
        else {
            // Ensure that the ID of the target is always present as attribute...
            attr.addProperty(StatefulTargetObject.KEY_ID, targetObject.getID());
        }

        result.add(ATTRIBUTES, attr);

        // then add all tags
        JsonObject tags = new JsonObject();

        if (targetObject.isRegistered()) {
            Enumeration<String> keys = targetObject.getTagKeys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                tags.addProperty(key, targetObject.getTag(key));
            }
        }

        result.add(TAGS, tags);

        // finally, if it's a target with state, add that as well
        JsonObject state = new JsonObject();
        state.addProperty(REGISTRATION_STATE, targetObject.getRegistrationState().name());
        state.addProperty(PROVISIONING_STATE, targetObject.getProvisioningState().name());
        state.addProperty(STORE_STATE, targetObject.getStoreState().name());
        state.addProperty(CURRENT_VERSION, targetObject.getCurrentVersion());
        state.addProperty(IS_REGISTERED, Boolean.toString(targetObject.isRegistered()));
        state.addProperty(NEEDS_APPROVAL, Boolean.toString(targetObject.needsApprove()));
        state.addProperty(AUTO_APPROVE, Boolean.toString(targetObject.getAutoApprove()));

        JsonArray artifactsFromShop = new JsonArray();
        ArtifactObject[] artifactObjects = targetObject.getArtifactsFromShop();
        if (artifactObjects != null) {
            for (ArtifactObject a : artifactObjects) {
                artifactsFromShop.add(new JsonPrimitive(a.getDefinition()));
            }
        }
        state.add(ARTIFACTS_FROM_SHOP, artifactsFromShop);

        JsonArray artifactsFromDeployment = new JsonArray();
        DeploymentArtifact[] deploymentArtifacts = targetObject.getArtifactsFromDeployment();
        if (deploymentArtifacts != null) {
            for (DeploymentArtifact a : deploymentArtifacts) {
                artifactsFromDeployment.add(new JsonPrimitive(a.getUrl()));
            }
        }
        state.add(ARTIFACTS_FROM_DEPLOYMENT, artifactsFromDeployment);

        state.addProperty(LAST_INSTALL_VERSION, targetObject.getLastInstallVersion());
        state.addProperty(LAST_INSTALL_SUCCESS, targetObject.getLastInstallSuccess());

        /* TODO getLicenses/AssocationsWith might not be that helpful since the data is also available in a different way */
        /* TODO some of this tends to show up as attributes as well, so we will need to do some filtering there */
        /* TODO some aspects of the state can be manipulated as well, we need to supply methods for that */
        result.add(STATE, state);

        return result;
    }

    /**
     * Serializes a (non stateful target object) repository object to a JSON representation.
     * 
     * @param repositoryObject the repository object to serialize, cannot be <code>null</code>.
     * @return a JSON representation of the given repository object, never <code>null</code>.
     */
    private JsonElement serializeRepositoryObject(RepositoryObject repositoryObject) {
        JsonObject result = new JsonObject();
        // ACE-243: first all the definition...
        result.addProperty(DEFINITION, repositoryObject.getDefinition());
        
        // then add all attributes
        JsonObject attr = new JsonObject();
        
        Enumeration<String> keys = repositoryObject.getAttributeKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            attr.addProperty(key, repositoryObject.getAttribute(key));
        }
        result.add(ATTRIBUTES, attr);
        
        // then add all tags
        JsonObject tags = new JsonObject();

        keys = repositoryObject.getTagKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            tags.addProperty(key, repositoryObject.getTag(key));
        }
        result.add(TAGS, tags);
        
        return result;
    }
}