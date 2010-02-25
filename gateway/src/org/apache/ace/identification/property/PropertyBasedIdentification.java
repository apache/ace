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
package org.apache.ace.identification.property;

import java.util.Dictionary;

import org.apache.ace.identification.Identification;
import org.apache.ace.identification.property.constants.IdentificationConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Simple implementation of the <code>Identification</code> interface. Because
 * a gateway identification should not change during it's lifetime the user of this
 * implementation should set the ID only once.
 */
public class PropertyBasedIdentification implements ManagedService, Identification {
    private volatile LogService m_log;
    private String m_gatewayID;

    public synchronized String getID() {
        return m_gatewayID;
    }

    public void updated(Dictionary dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String id = (String) dictionary.get(IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY);
            if ((id == null) || (id.length() == 0)) {
                // illegal config
                throw new ConfigurationException(IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, "Illegal gateway ID supplied");
            }
            if (m_gatewayID != null) {
                m_log.log(LogService.LOG_WARNING, "Gateway ID is being changed from " + m_gatewayID + " to " + id);
            }
            // legal config, set configuration
            synchronized (this) {
                m_gatewayID = id;
            }
        }
    }
}
