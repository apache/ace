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
package org.apache.ace.client.services;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * Service that can create links between the various objects.
 */
@RemoteServiceRelativePath("associations")
public interface AssociationService extends RemoteService {
    /**
     * Links a bundle and a group; note that this will create a link to a given bundle,
     * not to its symbolic name.
     */
    void link(BundleDescriptor bundle, GroupDescriptor group) throws Exception;
    
    /**
     * Links a group and a license by name.
     */
    void link(GroupDescriptor group, LicenseDescriptor license) throws Exception;
    
    /**
     * Links a license and a target. If the target is not yet registered, it will be,
     * and the 'auto approve' will be turned on.
     */
    void link(LicenseDescriptor license, TargetDescriptor target) throws Exception;

    /**
     * Unlinks the two given descriptors.
     */
    void unlink(Descriptor one, Descriptor other) throws Exception;

    /**
     * Gets all the descriptors that are in some way related to the given one.
     */
    Descriptor[] getRelated(Descriptor o) throws Exception;
}
