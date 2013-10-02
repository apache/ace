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
import java.util.Map;

import org.apache.ace.feedback.AuditEvent;
import org.apache.ace.feedback.Event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class LogEventSerializer implements JsonSerializer<Event> {

	public JsonElement serialize(Event e, Type typeOfSrc, JsonSerializationContext context) {
        DateFormat format = SimpleDateFormat.getDateTimeInstance();
        JsonObject event = new JsonObject();
        event.addProperty("logId", e.getStoreID());
        event.addProperty("id", e.getID());
        event.addProperty("time", format.format(new Date(e.getTime())));
        event.addProperty("type", toAuditEventType(e.getType()));
        JsonObject eventProperties = new JsonObject();
        Map<String, String> p = e.getProperties();
        for (String key : p.keySet()) {
            eventProperties.addProperty(key, p.get(key));
        }
        event.add("properties", eventProperties);
        return event;
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
