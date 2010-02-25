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
import org.apache.ace.client.services.Descriptor;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * Base class for all Service impls. We expect each implementer to have
 * a static 'instance' method.
 */
public abstract class ObjectServiceImpl<REPOOBJECT extends RepositoryObject, DESCRIPTOR extends Descriptor> extends RemoteServiceServlet {
    private static final long serialVersionUID = 642638564864566760L;

    /**
     * Unwraps the given descriptor, giving back the original repository object.
     * @param request 
     */
    public abstract REPOOBJECT unwrap(HttpServletRequest request, Descriptor descriptor) throws Exception;
    
    /**
     * Wraps the given object, returning a descriptor.
     */
    public abstract DESCRIPTOR wrap(RepositoryObject object);
    
    public abstract List<REPOOBJECT> get() throws Exception;
    
    public abstract void remove(REPOOBJECT object) throws Exception;
    
    public List<DESCRIPTOR> getDescriptors() throws Exception {
        List<DESCRIPTOR> result = new ArrayList<DESCRIPTOR>();
        for (REPOOBJECT o : get()) {
            result.add(wrap(o));
        }
        return result;
    }
    
    public void remove(DESCRIPTOR descriptor) throws Exception {
        remove(unwrap(getThreadLocalRequest(), descriptor));
    }
    
}
