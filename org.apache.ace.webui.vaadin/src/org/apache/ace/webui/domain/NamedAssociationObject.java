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
package org.apache.ace.webui.domain;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.webui.NamedObject;

/**
 * 
 */
public class NamedAssociationObject implements NamedObject {
    private final Association<?, ?> m_association;
    
    /**
     * Creates a new {@link NamedAssociationObject} instance.
     */
    public NamedAssociationObject(Association<?, ?> association) {
        m_association = association;
    }

    @Override
    public String getDefinition() {
        return m_association.getDefinition();
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public RepositoryObject getObject() {
        return m_association;
    }

    @Override
    public void setDescription(String description) {
        // Ignore...
    }
}
