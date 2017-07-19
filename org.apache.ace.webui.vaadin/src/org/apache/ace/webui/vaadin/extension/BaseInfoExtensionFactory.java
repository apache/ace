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
package org.apache.ace.webui.vaadin.extension;

import org.apache.ace.client.repository.RepositoryObject;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;

/**
 * Provides a UI extension for the target details window showing some details on a (stateful) target.
 */
abstract class BaseInfoExtensionFactory<OBJ extends RepositoryObject> extends BaseUIExtensionFactory<OBJ> {

    protected BaseInfoExtensionFactory() {
        super("Info");
    }

    @Override
    public final Component create(OBJ obj) {
        Table table = new Table();
        table.setEditable(false);
        table.setWidth("100%");

        table.addContainerProperty("Name", Label.class, null);
        table.addContainerProperty("Value", Label.class, null);

        table.setColumnExpandRatio("Name", 0.22f);
        table.setColumnExpandRatio("Value", 1.0f);

        addTableRows(table, obj);

        return table;
    }

    protected final void addItem(Table table, String label, Enum<?> value) {
        if (value != null) {
            table.addItem(new Object[] { label, value.name() }, null);
        }
    }

    protected final void addItem(Table table, String label, Long value) {
        if (value != null && value.longValue() >= 0) {
            table.addItem(new Object[] { label, value }, null);
        }
    }

    protected final void addItem(Table table, String label, String value) {
        if (value != null && !"".equals(value)) {
            table.addItem(new Object[] { label, value }, null);
        }
    }

    protected abstract void addTableRows(Table table, OBJ obj);
}
