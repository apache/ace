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

import static org.apache.ace.client.repository.Association.LEFT_CARDINALITY;
import static org.apache.ace.client.repository.Association.LEFT_ENDPOINT;
import static org.apache.ace.client.repository.Association.RIGHT_CARDINALITY;
import static org.apache.ace.client.repository.Association.RIGHT_ENDPOINT;

import java.util.stream.Stream;

import org.apache.ace.client.repository.Association;
import org.apache.ace.client.repository.RepositoryObject;

import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;

/**
 * Provides a UI extension for the target details window showing some details on a (stateful) target.
 */
abstract class BaseAssociationExtensionFactory<OBJ extends RepositoryObject, ASSOC extends Association<?, ?>> extends BaseUIExtensionFactory<OBJ> {

    protected BaseAssociationExtensionFactory(String caption) {
        super(caption);
    }

    @Override
    public final Component create(OBJ obj) {
        Table table = new Table();
        table.setEditable(false);
        table.setWidth("100%");

        table.addContainerProperty("Left endpoint", Label.class, null);
        table.addContainerProperty("Left cardinality", Label.class, null);
        table.addContainerProperty("Right endpoint", Label.class, null);
        table.addContainerProperty("Right cardinality", Label.class, null);

        table.setColumnExpandRatio("Left endpoint", 1.0f);
        table.setColumnExpandRatio("Left cardinality", 0.12f);
        table.setColumnExpandRatio("Right endpoint", 1.0f);
        table.setColumnExpandRatio("Right cardinality", 0.12f);

        getAssocations(obj)
            .forEach(assoc -> table.addItem(new Object[] {
                assoc.getAttribute(LEFT_ENDPOINT),
                getCardinality(assoc.getAttribute(LEFT_CARDINALITY)),
                assoc.getAttribute(RIGHT_ENDPOINT),
                getCardinality(assoc.getAttribute(RIGHT_CARDINALITY)) }, null));

        return table;
    }

    protected abstract Stream<ASSOC> getAssocations(OBJ obj);

    private String getCardinality(String cardinality) {
        if (cardinality.equals(Integer.toString(Integer.MAX_VALUE))) {
            cardinality = "*";
        }
        return cardinality;
    }
}
