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

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

public class VaadinResourceHandler {
    private volatile HttpService m_http;
    private HttpContext m_context;
    private BundleContext m_bundleContext;

    public void start() {
        m_context = m_http.createDefaultHttpContext();
        try {
            m_http.registerResources("/VAADIN", "/VAADIN", new HttpContext() {
                public String getMimeType(String name) {
                    return m_context.getMimeType(name);
                }

                /**
                 * ACE uses a slightly modified version of the 'reindeer' theme. To avoid having to copy all resources
                 * in the Vaadin jar, we only override the files we changed and do replace the theme name 'ace' with
                 * 'reindeer' before we go looking for the original files.
                 * 
                 * When updating to a new Vaadin version, usually you need to copy the styles.css file from the original
                 * archive again and append the ACE changes to the end, as this file tends to change considerably
                 * between versions.
                 */
                public URL getResource(String name) {
                    URL resource = null;
                    String prefix = "/VAADIN/";
                    // fix for ACE-156
                    if (!name.startsWith("/")) {
                        name = "/" + name;
                    }
                    if (name.startsWith(prefix)) {
                        String originalName = name.replace("/ace/", "/reindeer/");
                        resource = m_bundleContext.getBundle().getEntry(originalName);
                        if (resource == null) {
                            // try to find the resource in the Vaadin bundle instead
                            resource = com.vaadin.Application.class.getResource(originalName);
                        }
                    }
                    return resource;
                }

                public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    return m_context.handleSecurity(request, response);
                }
            });
        }
        catch (NamespaceException e) {
            e.printStackTrace();
        }
    }
}
