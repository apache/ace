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
package org.apache.ace.server.action.popupmessage;

import javax.swing.JOptionPane;

import org.apache.ace.server.action.MessageAction;
import org.osgi.service.event.Event;
import org.osgi.service.useradmin.User;

/**
 * Shows a message in a popup dialog. Does not wait for the user to respond, so multiple popups can
 * be shown at the same time (each one in its own thread). This action is mainly for demonstration
 * purposes, to replace actions like a mail action, that might be hard to configure in a simple
 * demo scenario.
 */
public class PopupMessageAction implements MessageAction {
    public static final String NAME = "PopupMessageAction";

    public void handle(Event event) {
        final User user = (User) event.getProperty(USER);
        final String description = (String) event.getProperty(DESCRIPTION);
        final String shortDescription = (String) event.getProperty(SHORT_DESCRIPTION);

        Thread t = new Thread("Notification") {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null,
                    "<html><table><tr><td>To: </td><td>" + user.getName() + " " + (String) user.getProperties().get("email") + "</td></tr>" +
                    "<tr><td>Subject: </td><td>" + shortDescription + "</td></tr>" +
                    "<tr><td valign='top'>Message: </td><td>" + description.replaceAll("\n", "<p>")
                );
            }
        };
        t.start();
    }
}
