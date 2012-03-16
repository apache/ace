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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.DeploymentArtifact;
import org.apache.ace.client.repository.stateful.StatefulTargetObject;
import org.apache.ace.log.AuditEvent;
import org.apache.ace.log.LogEvent;

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
        if (repositoryObject instanceof StatefulTargetObject) {
            StatefulTargetObject stateful = (StatefulTargetObject) repositoryObject;
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
            state.add("auditEvents", getAuditEvents(stateful));
            /* TODO getLicenses/AssocationsWith might not be that helpful since the data is also available in a different way */
            /* TODO some of this tends to show up as attributes as well, so we will need to do some filtering there */
            /* TODO some aspects of the state can be manipulated as well, we need to supply methods for that */
            result.add("state", state);
        }
        return result;
    }

    private JsonArray getAuditEvents(StatefulTargetObject stateful) {
        DateFormat format = SimpleDateFormat.getDateTimeInstance();
        List<LogEvent> auditEvents = stateful.getAuditEvents();
        JsonArray events = new JsonArray();
        for (LogEvent e : auditEvents) {
            JsonObject event = new JsonObject();
            event.addProperty("logId", e.getLogID());
            event.addProperty("id", e.getID());
            event.addProperty("time", format.format(new Date(e.getTime())));
            event.addProperty("type", toAuditEventType(e.getType()));
            JsonObject eventProperties = new JsonObject();
            Dictionary p = e.getProperties();
            Enumeration keyEnumeration = p.keys();
            while (keyEnumeration.hasMoreElements()) {
                Object key = keyEnumeration.nextElement();
                eventProperties.addProperty(key.toString(), p.get(key).toString());
            }
            event.add("properties", eventProperties);
            events.add(event);
        }
        return events;
    }

    private String toAuditEventType(int type) {
        switch (type) {
            case AuditEvent.BUNDLE_INSTALLED: return "bundle installed";
            case AuditEvent.BUNDLE_RESOLVED: return "bundle resolved";
            case AuditEvent.BUNDLE_STARTED: return "bundle started";
            case AuditEvent.BUNDLE_STOPPED: return "bundle stopped";
            case AuditEvent.BUNDLE_UNRESOLVED: return "bundle unresolved";
            case AuditEvent.BUNDLE_UPDATED: return "bundle updated";
            case AuditEvent.BUNDLE_UNINSTALLED: return "bundle uninstalled";
            case AuditEvent.BUNDLE_STARTING: return "bundle starting";
            case AuditEvent.BUNDLE_STOPPING: return "bundle stopping";
            case AuditEvent.FRAMEWORK_INFO: return "framework info";
            case AuditEvent.FRAMEWORK_WARNING: return "framework warning";
            case AuditEvent.FRAMEWORK_ERROR: return "framework error";
            case AuditEvent.FRAMEWORK_REFRESH: return "framework refresh";
            case AuditEvent.FRAMEWORK_STARTED: return "framework started";
            case AuditEvent.FRAMEWORK_STARTLEVEL: return "framework startlevel";
            case AuditEvent.DEPLOYMENTADMIN_INSTALL: return "deployment admin install";
            case AuditEvent.DEPLOYMENTADMIN_UNINSTALL: return "deployment admin uninstall";
            case AuditEvent.DEPLOYMENTADMIN_COMPLETE: return "deployment admin complete";
            case AuditEvent.DEPLOYMENTCONTROL_INSTALL: return "deployment control install";
            default: return Integer.toString(type);
        }
    }
}