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

import java.util.Map;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.webui.UIExtensionFactory;

import com.vaadin.ui.Component;
import com.vaadin.ui.VerticalLayout;

abstract class BaseUIExtensionFactory<OBJ extends RepositoryObject> implements UIExtensionFactory {
    private final String m_caption;

    protected BaseUIExtensionFactory(String caption) {
        m_caption = caption;
    }

    @Override
    public final Component create(Map<String, Object> context) {
        VerticalLayout vl = new VerticalLayout();
        vl.setCaption(m_caption);
        vl.setSpacing(false);
        vl.setSizeFull();

        OBJ obj = getObjectFromContext(context);
        if (obj == null) {
            return null;
        }

        vl.addComponent(create(obj));

        return vl;
    }

    abstract Component create(OBJ obj);

    @SuppressWarnings("unchecked")
    private OBJ getObjectFromContext(Map<String, Object> context) {
        return (OBJ) context.get("object");
    }

}
