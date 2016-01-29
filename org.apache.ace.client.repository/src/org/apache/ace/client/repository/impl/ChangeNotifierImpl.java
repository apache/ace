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
package org.apache.ace.client.repository.impl;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.ace.client.repository.SessionFactory;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

/**
 * ChangeNotifierImpl provides a basic implementation of a ChangeNotifier, intended to be used by classes related to the
 * RepositoryAdmin.<br>
 * <br>
 * Topics are built up in the following fashion:
 * <ul>
 * <li><b>...TopicRoot</b> All topics start with a TopicRoot, which is the same for all related classes, and ends with a
 * "/". There can be internal and external topics, hence two TopicRoot parameters in the constructor.</li>
 * <li><b>entityRoot</b> This is followed by a class-specific root, usually consisting of the classname with an added
 * "/".</li>
 * <li>Finally, for each call to <code>notifyChanged</code>, a topic can be specified, which is something like "CHANGED"
 * or "ADDED".</li>
 * </ul>
 */
public class ChangeNotifierImpl implements ChangeNotifier {

    private final EventAdmin m_eventAdmin;
    private final String m_privateTopicRoot;
    private final String m_publicTopicRoot;
    private final String m_entityRoot;
    private final String m_sessionID;

    /**
     * Creates a new ChangeNotifierImpl.
     * 
     * @param eventAdmin
     *            An EventAdmin to send events to.
     * @param privateTopicRoot
     *            The root of all private topics; see TopicRoot in the description of this class.
     * @param publicTopicRoot
     *            The root of all public topics; see TopicRoot in the description of this class.
     * @param entityRoot
     *            A class-specific root for the class which will use this ChangeNotifierImpl.
     */
    ChangeNotifierImpl(EventAdmin eventAdmin, String privateTopicRoot, String publicTopicRoot, String entityRoot, String sessionID) {
        m_eventAdmin = eventAdmin;
        m_privateTopicRoot = privateTopicRoot;
        m_publicTopicRoot = publicTopicRoot;
        m_entityRoot = entityRoot;
        m_sessionID = sessionID;
    }

    private Properties addSession(Properties props) {
        if (props == null) {
            props = new Properties();
        }
        props.put(SessionFactory.SERVICE_SID, m_sessionID);
        return props;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void notifyChanged(String topic, Properties props, boolean internalOnly) {
        props = addSession(props);
        m_eventAdmin.sendEvent(new Event(m_privateTopicRoot + m_entityRoot + topic, (Dictionary) props));
        if (!internalOnly) {
            m_eventAdmin.postEvent(new Event(m_publicTopicRoot + m_entityRoot + topic, (Dictionary) props));
        }
    }

    public void notifyChanged(String topic, Properties props) {
        notifyChanged(topic, props, false);
    }

    public String getTopicAll(boolean publicTopic) {
        if (publicTopic) {
            return m_publicTopicRoot + m_entityRoot + "*";
        }
        else {
            return m_privateTopicRoot + m_entityRoot + "*";
        }
    }

}
