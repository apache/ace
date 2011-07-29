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
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * 
 */
public class RepositoryObjectSerializer implements JsonSerializer<RepositoryObject> {
    public JsonElement serialize(RepositoryObject repositoryObject, Type featureType, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        // first add all attributes
        Enumeration<String> keys = repositoryObject.getAttributeKeys();
        JsonObject attr = new JsonObject();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            attr.addProperty(key, repositoryObject.getAttribute(key));
        }
        result.add("attributes", attr);
        // then add all tags
        keys = repositoryObject.getTagKeys();
        JsonObject tags = new JsonObject();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            tags.addProperty(key, repositoryObject.getTag(key));
        }
        result.add("tags", tags);
        // finally, if it's a target with state, add that as well
        if (repositoryObject instanceof StatefulGatewayObject) {
            StatefulGatewayObject stateful = (StatefulGatewayObject) repositoryObject;
            JsonObject state = new JsonObject();
            state.addProperty("registrationState", stateful.getRegistrationState().name());
            state.addProperty("provisioningState", stateful.getProvisioningState().name());
            state.addProperty("storeState", stateful.getStoreState().name());
            state.addProperty("currentVersion", stateful.getCurrentVersion());
            state.addProperty("isRegistered", Boolean.toString(stateful.isRegistered()));
            state.addProperty("needsApproval", Boolean.toString(stateful.needsApprove()));
            state.addProperty("autoApprove", Boolean.toString(stateful.getAutoApprove()));
            JsonArray artifactsFromShop = new JsonArray();
            for (ArtifactObject a : stateful.getArtifactsFromShop()) {
                artifactsFromShop.add(new JsonPrimitive(Workspace.getRepositoryObjectIdentity(a)));
            }
            state.add("artifactsFromShop", artifactsFromShop);
            JsonArray artifactsFromDeployment = new JsonArray();
            for (DeploymentArtifact a : stateful.getArtifactsFromDeployment()) {
                artifactsFromDeployment.add(new JsonPrimitive(a.getUrl()));
            }
            state.add("artifactsFromDeployment", artifactsFromDeployment);
            state.addProperty("lastInstallVersion", stateful.getLastInstallVersion());
            state.addProperty("lastInstallSuccess", stateful.getLastInstallSuccess());
            /* TODO auditEvents probably also needs paging and maybe filtering / searching */
            /* TODO getLicenses/AssocationsWith might not be that helpful since the data is also available in a different way */
            /* TODO some of this tends to show up as attributes as well, so we will need to do some filtering there */
            /* TODO some aspects of the state can be manipulated as well, we need to supply methods for that */
            result.add("state", state);
        }
        return result;
    }
}