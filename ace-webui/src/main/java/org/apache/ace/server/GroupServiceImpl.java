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
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.GroupService;

public class GroupServiceImpl extends ObjectServiceImpl<GroupObject, GroupDescriptor> implements GroupService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 4353842101585350694L;

    private static GroupServiceImpl m_instance;
    
    public GroupServiceImpl() {
        if (m_instance != null) { System.out.println("Warning, duplicate " + getClass().getSimpleName()); }
        m_instance = this;
    }
    
    static GroupServiceImpl instance() {
        return m_instance;
    }
    
    public GroupDescriptor[] getGroups() throws Exception {
        List<GroupDescriptor> descriptors = getDescriptors();
        return getDescriptors().toArray(new GroupDescriptor[descriptors.size()]);
    }

    @Override
    public List<GroupObject> get() throws Exception {
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        return gr.get();
    }
    
    public void addGroup(String name) throws Exception {
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        
        Map<String, String> props = new HashMap<String, String>();
        props.put(GroupObject.KEY_NAME, name);
        gr.create(props, null);
    }

    @Override
    public void remove(GroupObject object) throws Exception {
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        gr.remove(object);
    }

    @Override
    public GroupDescriptor wrap(RepositoryObject object) {
        if (object instanceof GroupObject) {
            return new GroupDescriptor(((GroupObject) object).getName());
        }
        throw new IllegalArgumentException();
    }
    
    @Override
    public GroupObject unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception {
        GroupRepository gr = Activator.getService(request, GroupRepository.class);
        List<GroupObject> list = gr.get(Activator.getContext().createFilter("(" + GroupObject.KEY_NAME + "=" + descriptor.getName() + ")"));
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new IllegalArgumentException();
    }
}
