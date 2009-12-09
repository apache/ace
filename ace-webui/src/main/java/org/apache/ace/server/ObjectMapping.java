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
import org.apache.ace.client.repository.object.ArtifactObject;
import org.apache.ace.client.repository.object.GroupObject;
import org.apache.ace.client.repository.object.LicenseObject;
import org.apache.ace.client.repository.stateful.StatefulGatewayObject;
import org.apache.ace.client.services.BundleDescriptor;
import org.apache.ace.client.services.Descriptor;
import org.apache.ace.client.services.GroupDescriptor;
import org.apache.ace.client.services.LicenseDescriptor;
import org.apache.ace.client.services.TargetDescriptor;

/**
 * This class contains helper methods that ease the mapping from {@link RepositoryObject}s to {@link Descriptor}s.
 */
public class ObjectMapping {
    /**
     * Wraps {@link RepositoryObject}s in {@link Descriptor}s.
     */
    public static List<Descriptor> wrap(List<RepositoryObject> objects) {
        List<Descriptor> result = new ArrayList<Descriptor>();
        for (RepositoryObject o : objects) {
            result.add(wrap(o));
        }
        return result;
    }
    
    /**
     * Wraps a single {@link RepositoryObject} in a {@link Descriptor}.
     */
    public static Descriptor wrap(RepositoryObject object) {
        return findService(object).wrap(object);
    }

    /**
     * Unwraps a single {@link Descriptor}.
     * @param request 
     */
    public static RepositoryObject unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception {
        return findService(descriptor).unwrap(request, descriptor);
    }
    
    /**
     * Finds the service impl for the given object.
     */
    private static ObjectServiceImpl<? extends RepositoryObject, ? extends Descriptor> findService(RepositoryObject object) {
        if (object instanceof ArtifactObject) {
            return BundleServiceImpl.instance();
        }
        else if (object instanceof GroupObject) {
            return GroupServiceImpl.instance();
        }
        else if (object instanceof LicenseObject) {
            return LicenseServiceImpl.instance();
        }
        else if (object instanceof StatefulGatewayObject) {
            return TargetServiceImpl.instance();
        }
        return null;
    }
    
    /**
     * Finds the service impl for the given object.
     */
    public static ObjectServiceImpl<? extends RepositoryObject, ? extends Descriptor> findService(Descriptor descriptor) {
        if (descriptor instanceof BundleDescriptor) {
            return BundleServiceImpl.instance();
        }
        else if (descriptor instanceof GroupDescriptor) {
            return GroupServiceImpl.instance();
        }
        else if (descriptor instanceof LicenseDescriptor) {
            return LicenseServiceImpl.instance();
        }
        else if (descriptor instanceof TargetDescriptor) {
            return TargetServiceImpl.instance();
        }
        return null;
    }
}
