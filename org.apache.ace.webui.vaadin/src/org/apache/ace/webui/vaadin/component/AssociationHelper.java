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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.vaadin.ui.Table;

public class AssociationHelper {
    private Set<String> m_associatedItems = new HashSet<>();
    private Set<String> m_relatedItems = new HashSet<>();
    private Table m_activeTable;

    public void addAssociated(Collection<String> associated) {
        m_associatedItems.addAll(associated);
    }

    public void addRelated(Collection<String> related) {
        m_relatedItems.addAll(related);
    }

    public void clear() {
        m_associatedItems.clear();
        m_relatedItems.clear();
    }

    public Set<?> getActiveSelection() {
        return (m_activeTable != null) ? (Set<?>) m_activeTable.getValue() : Collections.emptySet();
    }

    public boolean isActiveTable(Table table) {
        return (m_activeTable != null) ? m_activeTable.equals(table) : false;
    }

    public boolean isAssociated(Object definition) {
        return m_associatedItems.contains(definition);
    }

    public boolean isRelated(Object definition) {
        return m_relatedItems.contains(definition);
    }

    public void removeAssociatedItem(String definition) {
        m_associatedItems.remove(definition);
    }

    public void updateRelations(Set<String> associated, Set<String> related) {
        m_associatedItems.addAll(associated);
        m_relatedItems.addAll(related);
    }

    public void updateActiveTable(Table source) {
        if (m_activeTable != null) {
            if (!m_activeTable.equals(source)) {
                m_activeTable.setValue(Collections.emptySet());
            }
        }
        m_activeTable = source;
        m_activeTable.requestRepaint();
    }
}
