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
package org.apache.ace.server.action;

import org.osgi.service.event.Event;

/**
 * An Action is a rather general action which can be used to handle some event.
 * Action services are to be published with a service property {@link Action#ACTION_NAME},
 * which states the name of this action, so other services can find it.<br>
 * <br>
 * Implementers of Action should specify in their interface which properties should
 * be available in the event, for the action to be able to do its job.
 */
public interface Action {
    public static final String ACTION_NAME = Action.class.getName() + ".name";

    /**
     * Handles an event, performing the main action of this Action.
     */
    public void handle(Event event);
}
