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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.LicenseService;

public class LicenseServiceImpl extends ObjectServiceImpl<LicenseObject, LicenseDescriptor> implements LicenseService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = -8435568874637903362L;

    private static LicenseServiceImpl m_instance;
    
    public LicenseServiceImpl() {
        if (m_instance != null) { System.out.println("Warning, duplicate " + getClass().getSimpleName()); }
        m_instance = this;
    }
    
    static LicenseServiceImpl instance() {
        return m_instance;
    }
    
    public LicenseDescriptor[] getLicenses() throws Exception {
        List<LicenseDescriptor> descriptors = getDescriptors();
        return getDescriptors().toArray(new LicenseDescriptor[descriptors.size()]);
    }
    
    @Override
    public List<LicenseObject> get() throws Exception {
        return Activator.getService(getThreadLocalRequest(), LicenseRepository.class).get();
    }
    
    public void addLicense(String name) throws Exception {
        LicenseRepository gr = Activator.getService(getThreadLocalRequest(), LicenseRepository.class);
        
        Map<String, String> props = new HashMap<String, String>();
        props.put(LicenseObject.KEY_NAME, name);
        gr.create(props, null);
    }
    
    @Override
    public void remove(LicenseObject object) throws Exception {
        LicenseRepository lr = Activator.getService(getThreadLocalRequest(), LicenseRepository.class);
        lr.remove(object);
    }
    
    @Override
    public LicenseDescriptor wrap(RepositoryObject object) {
        if (object instanceof LicenseObject) {
            return new LicenseDescriptor(((LicenseObject) object).getName());
        }
        throw new IllegalArgumentException();
    }

    @Override
    public LicenseObject unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception {
        LicenseRepository lr = Activator.getService(request, LicenseRepository.class);
        List<LicenseObject> list = lr.get(Activator.getContext().createFilter("(" + LicenseObject.KEY_NAME + "=" + descriptor.getName() + ")"));
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new IllegalArgumentException();
    }
}
