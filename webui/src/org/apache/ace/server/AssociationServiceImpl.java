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

import javax.servlet.http.HttpServletRequest;

import org.apache.ace.client.repository.RepositoryAdmin;
import org.apache.ace.client.repository.RepositoryObject;
import org.apache.ace.client.repository.helper.bundle.BundleHelper;
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GatewayObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.repository.Artifact2GroupAssociationRepository;
import org.apache.ace.client.repository.repository.Group2LicenseAssociationRepository;
import org.apache.ace.client.repository.repository.License2GatewayAssociationRepository;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.services.AssociationService;
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.TargetDescriptor;
import org.osgi.service.log.LogService;

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
    private AssocationType m_assocationType = AssocationType.DYNAMIC;

    public void setAssocationType(AssocationType type) {
        m_assocationType  = type;
    }
    public AssocationType getAssocationType() {
        return m_assocationType;
    }
    
    public void link(BundleDescriptor bundle, GroupDescriptor group) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        Artifact2GroupAssociationRepository a2gr = Activator.getService(request, Artifact2GroupAssociationRepository.class);
        
        ArtifactObject a = (ArtifactObject) ObjectMapping.unwrap(request, bundle);
        GroupObject g = (GroupObject) ObjectMapping.unwrap(request, group);

        switch (m_assocationType) {
            case STATIC:
                a2gr.create(a, g);
                break;
            case DYNAMIC:
                Map<String, String> properties = new HashMap<String, String>();
                properties.put(BundleHelper.KEY_ASSOCIATION_VERSIONSTATEMENT, "0.0.0");
                a2gr.create(a, properties, g, null);
                break;
        }
    }

    private void unlink(BundleDescriptor bundle, GroupDescriptor group) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        Artifact2GroupAssociationRepository a2gr = Activator.getService(request, Artifact2GroupAssociationRepository.class);
        
        ArtifactObject a = (ArtifactObject) ObjectMapping.unwrap(request, bundle);
        GroupObject g = (GroupObject) ObjectMapping.unwrap(request, group);
        
        a2gr.remove(a.getAssociationsWith(g).get(0));
    }

    public void link(GroupDescriptor group, LicenseDescriptor license) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        Group2LicenseAssociationRepository g2lr = Activator.getService(request, Group2LicenseAssociationRepository.class);

        GroupObject g = (GroupObject) ObjectMapping.unwrap(request, group);
        LicenseObject l = (LicenseObject) ObjectMapping.unwrap(request, license);
        
        g2lr.create(g, l);
    }

    private void unlink(GroupDescriptor group, LicenseDescriptor license) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        Group2LicenseAssociationRepository g2lr = Activator.getService(request, Group2LicenseAssociationRepository.class);

        GroupObject g = (GroupObject) ObjectMapping.unwrap(request, group);
        LicenseObject l = (LicenseObject) ObjectMapping.unwrap(request, license);
        
        g2lr.remove(g.getAssociationsWith(l).get(0));
    }

    public void link(LicenseDescriptor license, TargetDescriptor target) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        License2GatewayAssociationRepository l2tr = Activator.getService(request, License2GatewayAssociationRepository.class);

        LicenseObject l = (LicenseObject) ObjectMapping.unwrap(request, license);
        StatefulGatewayObject g = (StatefulGatewayObject) ObjectMapping.unwrap(request, target);
        
        if (!g.isRegistered()) {
            g.register();
            g.setAutoApprove(true);
        }
        
        l2tr.create(l, g.getGatewayObject());
    }

    private void unlink(LicenseDescriptor license, TargetDescriptor target) throws Exception {
        HttpServletRequest request = getThreadLocalRequest();
        License2GatewayAssociationRepository l2tr = Activator.getService(request, License2GatewayAssociationRepository.class);

        LicenseObject l = (LicenseObject) ObjectMapping.unwrap(request, license);
        StatefulGatewayObject g = (StatefulGatewayObject) ObjectMapping.unwrap(request, target);
        
        l2tr.remove(l.getAssociationsWith(g.getGatewayObject()).get(0));
    }
    
    public void unlink(Descriptor one, Descriptor other) throws Exception {
        _unlink(one, other);
        _unlink(other, one);
    }
    
    /**
     * Helper method for the unlink method, which allows easier checking of 'both ways' of an association.
     */
    private void _unlink(Descriptor one, Descriptor other) throws Exception {
        if (one instanceof BundleDescriptor && other instanceof GroupDescriptor) {
            unlink((BundleDescriptor) one, (GroupDescriptor) other);
        }
        else if (one instanceof GroupDescriptor && other instanceof LicenseDescriptor) {
            unlink((GroupDescriptor) one, (LicenseDescriptor) other);
        }
        else if (one instanceof LicenseDescriptor && other instanceof TargetDescriptor) {
            unlink((LicenseDescriptor) one, (TargetDescriptor) other);
        }
    }

    public Descriptor[] getRelated(Descriptor o) throws Exception {
        try {
            HttpServletRequest request = getThreadLocalRequest();
            RepositoryObject a = ObjectMapping.unwrap(request, o);
            List<RepositoryObject> relatedObjects = getRelated(request, a);
            List<Descriptor> descriptors = ObjectMapping.wrap(relatedObjects);
            return descriptors.toArray(new Descriptor[descriptors.size()]);
        }
        catch (Exception e) {
            Activator.instance().getLog().log(LogService.LOG_ERROR, "Error getting related objects", e);
            throw e;
        }
    }

    /**
     * Helper method that finds all related {@link RepositoryObject}s for a given one.
     */
    private List<RepositoryObject> getRelated(HttpServletRequest request, RepositoryObject object) throws Exception {
        List<RepositoryObject> result = new ArrayList<RepositoryObject>();
        if (object instanceof ArtifactObject) {
            List<GroupObject> groups = getRelated(object, GroupObject.class);
            List<LicenseObject> licenses = getRelated(groups, LicenseObject.class);
            List<GatewayObject> targets = getRelated(licenses, GatewayObject.class);
            result.addAll(groups);
            result.addAll(licenses);
            result.addAll(TargetServiceImpl.instance().findSGOs(request, targets));
        }
        else if (object instanceof GroupObject) {
            List<ArtifactObject> artifacts = getRelated(object, ArtifactObject.class);
            List<LicenseObject> licenses = getRelated(object, LicenseObject.class);
            List<GatewayObject> targets = getRelated(licenses, GatewayObject.class);
            result.addAll(artifacts);
            result.addAll(licenses);
            result.addAll(TargetServiceImpl.instance().findSGOs(request, targets));
        }
        else if (object instanceof LicenseObject) {
            List<GroupObject> groups = getRelated(object, GroupObject.class);
            List<ArtifactObject> artifacts = getRelated(groups, ArtifactObject.class);
            List<GatewayObject> targets = getRelated(object, GatewayObject.class);
            result.addAll(artifacts);
            result.addAll(groups);
            result.addAll(TargetServiceImpl.instance().findSGOs(request, targets));
        }
        else if (object instanceof StatefulGatewayObject && ((StatefulGatewayObject) object).isRegistered()) {
            // We can ignore any non-registered SGO's, since these will never have any associations.
            List<LicenseObject> licenses = getRelated(object, LicenseObject.class);
            List<GroupObject> groups = getRelated(licenses, GroupObject.class);
            List<ArtifactObject> artifacts = getRelated(groups, ArtifactObject.class);
            result.addAll(artifacts);
            result.addAll(groups);
            result.addAll(licenses);
        }
        return result;
    }
    
    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction'
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(FROM from, Class<TO> toClass) {
        return from.getAssociations(toClass);
    }

    /**
     * Helper method to find all related {@link RepositoryObject}s in a given 'direction', starting with a list of objects
     */
    private <FROM extends RepositoryObject, TO extends RepositoryObject> List<TO> getRelated(List<FROM> from, Class<TO> toClass) {
        List<TO> result = new ArrayList<TO>();
        for (RepositoryObject o : from) {
            result.addAll(getRelated(o, toClass));
        }
        return result;
    }
    
}
