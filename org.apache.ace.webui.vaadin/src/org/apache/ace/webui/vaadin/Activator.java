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
package org.apache.ace.webui.vaadin;

import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_KEY;
import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_VALUE_ARTIFACT;
import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_VALUE_DISTRIBUTION;
import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_VALUE_FEATURE;
import static org.apache.ace.webui.UIExtensionFactory.EXTENSION_POINT_VALUE_TARGET;
import static org.apache.ace.webui.vaadin.VaadinServlet.DEFAULT_SERVLET_ENDPOINT;
import static org.osgi.framework.Constants.SERVICE_RANKING;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PATTERN;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_RESOURCE_PREFIX;
import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

import java.util.Properties;

import javax.servlet.Servlet;

import org.apache.ace.webui.UIExtensionFactory;
import org.apache.ace.webui.vaadin.extension.ArtifactInfoExtensionFactory;
import org.apache.ace.webui.vaadin.extension.ArtifactToFeatureAssocExtensionFactory;
import org.apache.ace.webui.vaadin.extension.DistributionToFeatureAssocExtensionFactory;
import org.apache.ace.webui.vaadin.extension.DistributionToTargetAssocExtensionFactory;
import org.apache.ace.webui.vaadin.extension.FeatureToArtifactAssocExtensionFactory;
import org.apache.ace.webui.vaadin.extension.FeatureToDistributionAssocExtensionFactory;
import org.apache.ace.webui.vaadin.extension.TargetInfoExtensionFactory;
import org.apache.ace.webui.vaadin.extension.TargetToDistributionAssocExtensionFactory;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.log.LogService;

public class Activator extends DependencyActivatorBase {
    private static final String PID = "org.apache.ace.webui.vaadin";

    private static final String ACE_WEBUI_WHITEBOARD_CONTEXT_NAME = "org.apache.ace.webui";
    private static final String ACE_WEBUI_WHITEBOARD_CONTEXT_SELECT_FILTER = "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=" + ACE_WEBUI_WHITEBOARD_CONTEXT_NAME + ")";

    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        Properties contextProps = new Properties();
        contextProps.put(HTTP_WHITEBOARD_CONTEXT_NAME, ACE_WEBUI_WHITEBOARD_CONTEXT_NAME);
        contextProps.put(HTTP_WHITEBOARD_CONTEXT_PATH, "/");

        manager.add(createComponent()
            .setInterface(ServletContextHelper.class.getName(), contextProps)
            .setImplementation(AceWebuiServletContextHelper.class)
            .add(createConfigurationDependency().setPid(PID))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));

        Properties resourceRegistrationProps = new Properties();
        resourceRegistrationProps.put(HTTP_WHITEBOARD_RESOURCE_PREFIX, "/VAADIN");
        resourceRegistrationProps.put(HTTP_WHITEBOARD_RESOURCE_PATTERN, "/VAADIN/*");
        resourceRegistrationProps.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WEBUI_WHITEBOARD_CONTEXT_SELECT_FILTER);

        manager.add(createComponent()
            .setInterface(Object.class.getName(), resourceRegistrationProps)
            .setImplementation(new Object()));

        Properties props = new Properties();
        props.put(HTTP_WHITEBOARD_SERVLET_PATTERN, DEFAULT_SERVLET_ENDPOINT.concat("/*"));
        props.put(HTTP_WHITEBOARD_CONTEXT_SELECT, ACE_WEBUI_WHITEBOARD_CONTEXT_SELECT_FILTER);

        // register the main application for the ACE UI client
        manager.add(createComponent()
            .setInterface(Servlet.class.getName(), props)
            .setImplementation(VaadinServlet.class)
            .add(createConfigurationDependency().setPid(PID)));

        addArtifactExtensions(manager);
        addFeatureExtensions(manager);
        addDistributionExtensions(manager);
        addTargetExtensions(manager);
    }

    private void addArtifactExtensions(DependencyManager manager) {
        Properties props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_ARTIFACT);
        props.put(SERVICE_RANKING, Integer.valueOf(110));

        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(ArtifactInfoExtensionFactory.class));

        props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_ARTIFACT);
        props.put(SERVICE_RANKING, Integer.valueOf(100));

        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(ArtifactToFeatureAssocExtensionFactory.class));
    }

    private void addFeatureExtensions(DependencyManager manager) {
        Properties props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_FEATURE);
        props.put(SERVICE_RANKING, Integer.valueOf(110));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(FeatureToArtifactAssocExtensionFactory.class));

        props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_FEATURE);
        props.put(SERVICE_RANKING, Integer.valueOf(100));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(FeatureToDistributionAssocExtensionFactory.class));
    }

    private void addDistributionExtensions(DependencyManager manager) {
        Properties props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_DISTRIBUTION);
        props.put(SERVICE_RANKING, Integer.valueOf(110));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(DistributionToFeatureAssocExtensionFactory.class));

        props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_DISTRIBUTION);
        props.put(SERVICE_RANKING, Integer.valueOf(100));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(DistributionToTargetAssocExtensionFactory.class));
    }

    private void addTargetExtensions(DependencyManager manager) {
        Properties props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_TARGET);
        props.put(SERVICE_RANKING, Integer.valueOf(110));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(TargetInfoExtensionFactory.class));

        props = new Properties();
        props.put(EXTENSION_POINT_KEY, EXTENSION_POINT_VALUE_TARGET);
        props.put(SERVICE_RANKING, Integer.valueOf(100));

        // shows off components that are contributed by extensions
        manager.add(createComponent()
            .setInterface(UIExtensionFactory.class.getName(), props)
            .setImplementation(TargetToDistributionAssocExtensionFactory.class));
    }
}
