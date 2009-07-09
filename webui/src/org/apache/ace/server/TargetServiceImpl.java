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

import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.client.services.TargetDescriptor;
import org.apache.ace.client.services.TargetService;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

public class TargetServiceImpl extends RemoteServiceServlet implements TargetService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 4501422339245927089L;

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
        StatefulGatewayRepository sgr = Activator.getService(getThreadLocalRequest(), StatefulGatewayRepository.class);
        sgr.refresh();
        
        List<TargetDescriptor> result = new ArrayList<TargetDescriptor>();
        
        for (StatefulGatewayObject sgo : sgr.get()) {
            result.add(new TargetDescriptor(sgo.getID(), from(sgo.getProvisioningState())));
            System.err.println("Added " + sgo);
        }
        
        return result.toArray(new TargetDescriptor[result.size()]);
    }
}
