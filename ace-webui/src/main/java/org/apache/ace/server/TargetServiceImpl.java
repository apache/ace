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

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;

public class TargetServiceImpl extends ObjectServiceImpl<StatefulGatewayObject, TargetDescriptor> implements TargetService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 4501422339245927089L;

    private static TargetServiceImpl m_instance;
    
    public TargetServiceImpl() {
        if (m_instance != null) { System.out.println("Warning, duplicate " + getClass().getSimpleName()); }
        m_instance = this;
    }
    
    static TargetServiceImpl instance() {
        return m_instance;
    }
    
    /**
     * Helper method to translate between server- and client lingo
     */
    private static TargetDescriptor.ProvisioningState from(org.apache.ace.client.repository.stateful.StatefulGatewayObject.ProvisioningState state) {
        if (state != null) {
            switch (state) {
            case Failed: return TargetDescriptor.ProvisioningState.FAILED;
            case Idle: return TargetDescriptor.ProvisioningState.IDLE;
            case InProgress: return TargetDescriptor.ProvisioningState.INPROGRESS;
            case OK: return TargetDescriptor.ProvisioningState.OK;
            }
        }
        return null;
    }

    public TargetDescriptor[] getTargets() throws Exception {
        List<TargetDescriptor> descriptors = getDescriptors();
        return getDescriptors().toArray(new TargetDescriptor[descriptors.size()]);
    }

    @Override
    public List<StatefulGatewayObject> get() throws Exception {
        StatefulGatewayRepository sgr = Activator.getService(getThreadLocalRequest(), StatefulGatewayRepository.class);
        sgr.refresh();
        return sgr.get();
    }
    
    @Override
    public void remove(StatefulGatewayObject object) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public TargetDescriptor wrap(RepositoryObject object) {
        if (object instanceof StatefulGatewayObject) {
            StatefulGatewayObject sgo = (StatefulGatewayObject) object;
            return new TargetDescriptor(sgo.getID(), from(sgo.getProvisioningState()));
        }
        throw new IllegalArgumentException();
    }
    
    @Override
    public StatefulGatewayObject unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception {
        StatefulGatewayRepository gr = Activator.getService(request, StatefulGatewayRepository.class);
        List<StatefulGatewayObject> list = gr.get(Activator.getContext().createFilter("(" + GatewayObject.KEY_ID + "=" + descriptor.getName() + ")"));
        if (list.size() == 1) {
            return list.get(0);
        }
        throw new IllegalArgumentException();
    }
    
    /**
     * Helper method to find the {@link StatefulGatewayObject} for a given {@link GatewayObject}.
     * @param request 
     */
    public StatefulGatewayObject findSGO(HttpServletRequest request, GatewayObject go) throws Exception {
        StatefulGatewayRepository sgr = Activator.getService(request, StatefulGatewayRepository.class);
        
        
        // before we go on, let's refresh
        // this is not the right moment probably, but the question is, WHAT IS? ;)
        sgr.refresh();
        
        
        
        
        return sgr.get(Activator.getContext().createFilter("(" + GatewayObject.KEY_ID + "=" + go.getID() + ")")).get(0);
    }
    
    /**
     * Helper method to find all {@link StatefulGatewayObject}s for some {@link GatewayObject}s.
     * @param request 
     */
    public List<StatefulGatewayObject> findSGOs(HttpServletRequest request, List<GatewayObject> gos) throws Exception {
        List<StatefulGatewayObject> result = new ArrayList<StatefulGatewayObject>();
        for (GatewayObject go : gos) {
            result.add(findSGO(request, go));
        }
        return result;
    }
}
