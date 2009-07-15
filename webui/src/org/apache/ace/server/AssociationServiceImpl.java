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

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.ArtifactRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.GroupRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.repository.LicenseRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayRepository;
import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.TargetDescriptor;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * Allows the linking of objects; will find the {@link RepositoryAdmin}'s representation for
 * each of the descriptor objects.
 */
public class AssociationServiceImpl extends RemoteServiceServlet implements AssociationService {
    /**
     * Generated serialVersionUID
     */
    private static final long serialVersionUID = 2413722456179463935L;

    public void link(BundleDescriptor bundle, GroupDescriptor group) throws Exception {
        Artifact2GroupAssociationRepository a2gr = Activator.getService(getThreadLocalRequest(), Artifact2GroupAssociationRepository.class);
        ArtifactRepository ar = Activator.getService(getThreadLocalRequest(), ArtifactRepository.class);
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        
        ArtifactObject a = ar.get(Activator.getContext().createFilter("(" + ArtifactObject.KEY_ARTIFACT_NAME + "=" + bundle.getName() + ")")).get(0);
        GroupObject g = gr.get(Activator.getContext().createFilter("(" + GroupObject.KEY_NAME + "=" + group.getName() + ")")).get(0);
        
        a2gr.create(a, g);
    }

    public void link(GroupDescriptor group, LicenseDescriptor license) throws Exception {
        Group2LicenseAssociationRepository g2lr = Activator.getService(getThreadLocalRequest(), Group2LicenseAssociationRepository.class);
        GroupRepository gr = Activator.getService(getThreadLocalRequest(), GroupRepository.class);
        LicenseRepository lr = Activator.getService(getThreadLocalRequest(), LicenseRepository.class);
        
        GroupObject g = gr.get(Activator.getContext().createFilter("(" + GroupObject.KEY_NAME + "=" + group.getName() + ")")).get(0);
        LicenseObject l = lr.get(Activator.getContext().createFilter("(" + LicenseObject.KEY_NAME + "=" + license.getName() + ")")).get(0);

        g2lr.create(g, l);
    }

    public void link(LicenseDescriptor license, TargetDescriptor target) throws Exception {
        License2GatewayAssociationRepository l2tr = Activator.getService(getThreadLocalRequest(), License2GatewayAssociationRepository.class);
        LicenseRepository lr = Activator.getService(getThreadLocalRequest(), LicenseRepository.class);
        StatefulGatewayRepository gr = Activator.getService(getThreadLocalRequest(), StatefulGatewayRepository.class);
        
        LicenseObject l = lr.get(Activator.getContext().createFilter("(" + LicenseObject.KEY_NAME + "=" + license.getName() + ")")).get(0);
        StatefulGatewayObject g = gr.get(Activator.getContext().createFilter("(" + GatewayObject.KEY_ID + "=" + target.getName() + ")")).get(0);

        if (!g.isRegistered()) {
            g.register();
            g.setAutoApprove(true);
        }
        
        l2tr.create(l, g.getGatewayObject());
    }
}
