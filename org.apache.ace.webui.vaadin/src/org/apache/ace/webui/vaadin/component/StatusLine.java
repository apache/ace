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

package org.apache.ace.webui.vaadin.component;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.Artifact2FeatureAssociation;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.Distribution2TargetAssociation;
import org.apache.ace.client.repository.object.DistributionObject;
import org.apache.ace.client.repository.object.Feature2DistributionAssociation;
import org.apache.ace.client.repository.object.FeatureObject;
import org.apache.ace.client.repository.object.TargetObject;
import org.apache.ace.webui.NamedObject;
import org.apache.ace.webui.domain.NamedObjectFactory;
import org.osgi.service.event.EventHandler;

import com.vaadin.ui.Label;

/**
 * Denotes a status line in which a short summary of the latest actions in the UI are displayed.
 */
public class StatusLine extends Label implements EventHandler {

    /**
     * Creates a new {@link StatusLine} instance.
     */
    public StatusLine() {
        setImmediate(true);
    }

    @Override
    public void handleEvent(org.osgi.service.event.Event event) {
        String topic = event.getTopic();
        RepositoryObject entity = (RepositoryObject) event.getProperty(RepositoryObject.EVENT_ENTITY);

        String type = getType(topic);
        if (type == null) {
            // Nothing to do...
            return;
        }

        String action = getAction(topic);
        String name = getName(entity);

        if (name != null && action != null) {
            setStatus("%s '%s' %s...", type, name, action);
        }
        else if (action != null) {
            setStatus("%s %s...", type, action);
        }
    }

    /**
     * Sets the status to the given message.
     *
     * @param msg
     *            the message;
     * @param args
     *            the (optional) arguments.
     */
    public void setStatus(String msg, Object... args) {
        setValue(String.format(msg, args));
    }

    /**
     * @param topic
     * @return
     */
    private String getAction(String topic) {
        if (topic.endsWith("/REMOVED")) {
            return "removed";
        }
        else if (topic.endsWith("/ADDED")) {
            return "added";
        }
        else if (topic.endsWith("/CHANGED")) {
            return "changed";
        }
        else if (topic.endsWith("/STATUS_CHANGED")) {
            // for stateful target objects only...
            return "status updated";
        }
        else if (topic.endsWith("/AUDITEVENTS_CHANGED")) {
            // for stateful target objects only...
            return "audit log updated";
        }
        return null;
    }

    private String getName(RepositoryObject entity) {
        NamedObject obj = NamedObjectFactory.getNamedObject(entity);
        if (obj == null) {
            return null;
        }
        String name = obj.getName();
        return name == null || "".equals(name.trim()) ? null : name;
    }

    private String getType(String topic) {
        if (topic.contains(ArtifactObject.TOPIC_ENTITY_ROOT)) {
            return "Artifact";
        }
        else if (topic.contains(FeatureObject.TOPIC_ENTITY_ROOT)) {
            return "Feature";
        }
        else if (topic.contains(DistributionObject.TOPIC_ENTITY_ROOT)) {
            return "Distribution";
        }
        else if (topic.contains(TargetObject.TOPIC_ENTITY_ROOT)) {
            return "Target";
        }
        else if (topic.contains(Artifact2FeatureAssociation.TOPIC_ENTITY_ROOT)
            || topic.contains(Feature2DistributionAssociation.TOPIC_ENTITY_ROOT)
            || topic.contains(Distribution2TargetAssociation.TOPIC_ENTITY_ROOT)) {
            return "Association";
        }
        return null;
    }
}
