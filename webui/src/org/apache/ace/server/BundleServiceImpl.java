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
package org.apache.ace.server;

import java.util.ArrayList;
import java.util.List;

import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.BundleService;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * Returns all bundles the repository; other types of artifacts are ignored.
 */
public class BundleServiceImpl extends RemoteServiceServlet implements BundleService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = -355941324100124904L;

    public BundleDescriptor[] getBundles() throws Exception {
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        
        List<BundleDescriptor> result = new ArrayList<BundleDescriptor>();
        
        for (ArtifactObject a : ar.get(Activator.getContext().createFilter("(" + ArtifactObject.KEY_MIMETYPE + "=" + BundleHelper.MIMETYPE + ")"))) {
            result.add(new BundleDescriptor(a.getName()));
        }
        
        return result.toArray(new BundleDescriptor[result.size()]);
    }
}
