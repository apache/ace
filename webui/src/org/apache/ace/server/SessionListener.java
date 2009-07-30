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
package org.apache.ace.server;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Listener that destroys all session specific services that were published for that session.
 */
public class SessionListener implements HttpSessionListener {
    public void sessionCreated(HttpSessionEvent se) {
        // nothing to do when the session was created, the SessionFactory already 
        // created the appropriate services when we arrive at this point
    }

    public void sessionDestroyed(HttpSessionEvent se) {
        // get the session ID from the session
        String sessionID = se.getSession().getId();
        // destroy the session related services
        Activator.destroySession(sessionID);
    }
}
