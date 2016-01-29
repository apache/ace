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
import org.apache.ace.identification.IdentificationConstants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * Simple implementation of the <code>Identification</code> interface. Because
 * a target identification should not change during it's lifetime the user of this
 * implementation should set the ID only once.
 */
public class PropertyBasedIdentification implements ManagedService, Identification {
    private final Object LOCK = new Object();
    private volatile LogService m_log;
    private String m_targetID;
    
    public PropertyBasedIdentification() {
    }
    
    public PropertyBasedIdentification(String id) {
        setID(id);
    }

    public String getID() {
        synchronized (LOCK) {
            return m_targetID;
        }
    }
    
    public void setID(String id) {
        synchronized (LOCK) {
            if (m_targetID != null) {
                m_log.log(LogService.LOG_WARNING, "Target ID is being changed from " + m_targetID + " to " + id);
            }
            m_targetID = id;
        }
    }

    public void updated(Dictionary<String, ?> dictionary) throws ConfigurationException {
        if (dictionary != null) {
            String id = (String) dictionary.get(IdentificationConstants.IDENTIFICATION_TARGETID_KEY);
            if ((id == null) || (id.length() == 0)) {
                // illegal config
                throw new ConfigurationException(IdentificationConstants.IDENTIFICATION_TARGETID_KEY, "Illegal target ID supplied");
            }
            // legal config, set configuration
            setID(id);
        }
    }
}