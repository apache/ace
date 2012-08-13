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

import java.util.Properties;

/**
 * This interface defines a mechanism for notifying both internally interested parties, and
 * external listeners, of changes to the a given 'inhabitant' of the model.
 */
public interface ChangeNotifier {
    /**
     * Notifies both internal and external listeners of a change to some object.
     * @param topic A topic, as defined in the interface definitions of the various objects.
     * Note that this is not a <i>full</i> topic, but merely the 'last part', such as "ADDED";
     * this allows the ChangeNotifier to generate internal or external topics.
     * @param props Properties to pack with the event. May be null.
     */
    public void notifyChanged(String topic, Properties props);

    /**
     * Notifies both internal and external listeners of a change to some object.
     * @param topic A topic, as defined in the interface definitions of the various objects.
     * Note that this is not a <i>full</i> topic, but merely the 'last part', such as "ADDED";
     * this allows the ChangeNotifier to generate internal or external topics.
     * @param props Properties to pack with the event. May be null.
     * @param internalOnly Indicates this event is only for internal use, and the external
     * events should not be sent.
     */
    public void notifyChanged(String topic, Properties props, boolean internalOnly);

    /**
     * Gets a topic name which allows subscription to all topics that this ChangeNotifier can send.
     * @param publicTopic Indicates whether we are interested in the public (<code>true</code>) or the
     * private topic (<code>false</code>).
     * @return A topic name.
     */
    String getTopicAll(boolean publicTopic);
}
