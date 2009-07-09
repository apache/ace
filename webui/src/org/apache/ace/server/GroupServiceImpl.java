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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.GroupService;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class GroupServiceImpl extends RemoteServiceServlet implements GroupService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = -5744202709461660202L;

    public GroupDescriptor[] getGroups() throws Exception {
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        
        List<GroupDescriptor> result = new ArrayList<GroupDescriptor>();
        
        for (GroupObject g : gr.get()) {
            result.add(new GroupDescriptor(g.getName()));
        }
        
        return result.toArray(new GroupDescriptor[result.size()]);
    }

    public void addGroup(String name) throws Exception {
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        
        Map<String, String> props = new HashMap<String, String>();
        props.put(GroupObject.KEY_NAME, name);
        gr.create(props, null);
    }
}
