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

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.BundleService;
import org.apache.ace.client.services.Descriptor;

/**
 * This service only checks for bundles; all other artifacts are ignored.
 */
public class BundleServiceImpl extends ObjectServiceImpl<ArtifactObject, BundleDescriptor> implements BundleService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = -355941324100124904L;
    
    private static BundleServiceImpl m_instance;
    
    public BundleServiceImpl() {
        if (m_instance != null) { System.out.println("Warning, duplicate " + getClass().getSimpleName()); }
        m_instance = this;
    }
    
    static BundleServiceImpl instance() {
        return m_instance;
    }

    public BundleDescriptor[] getBundles() throws Exception {
        List<BundleDescriptor> descriptors = getDescriptors();
        return getDescriptors().toArray(new BundleDescriptor[descriptors.size()]);
    }
    
    public List<ArtifactObject> get() throws Exception {
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        return ar.get(Activator.getContext().createFilter("(" + ArtifactObject.KEY_MIMETYPE + "=" + BundleHelper.MIMETYPE + ")"));
    }
    
    @Override
    public void remove(ArtifactObject object) throws Exception {
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        ar.remove(object);
    }
    
    @Override
    public BundleDescriptor wrap(RepositoryObject object) {
        if (object instanceof ArtifactObject) {
            return new BundleDescriptor(((ArtifactObject) object).getName());
        }
        throw new IllegalArgumentException();
    }
    
    @Override
    public ArtifactObject unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception {
        ArtifactRepository ar = Activator.getService(request, ArtifactRepository.class);
        List<ArtifactObject> list = ar.get(Activator.getContext().createFilter("(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + descriptor.getName() + ")"));
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new IllegalArgumentException();
    }
}
