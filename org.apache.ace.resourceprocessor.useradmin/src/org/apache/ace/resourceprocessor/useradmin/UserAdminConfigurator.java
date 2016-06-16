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
package org.apache.ace.resourceprocessor.useradmin;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The UserAdminConfigurator can be used to install, remove or explicitly set the users that should be present in the
 * system's UserAdmin.<br>
 * <br>
 * The document should have the following shape,
 * 
 * <pre>
 * &lt;roles&gt;
 *     &lt;group name="group1"/&gt;
 *     &lt;group name="group2"&gt;
 *         &lt;memberof&gt;group1&lt;/memberof&gt;
 *     &lt;/group&gt;
 *     &lt;user name="user1"&gt;
 *         &lt;properties&gt;
 *             &lt;realname type="String"&gt;Mr. U. One&lt;/realname&gt;
 *             &lt;address&gt;1 Infinite Loop&lt;/address&gt;
 *         &lt;/properties&gt;
 *         &lt;credentials&gt;
 *             &lt;password type="byte[]"&gt;secret&lt;/password&gt;
 *         &lt;/credentials&gt;
 *         &lt;memberof&gt;group1&lt;/memberof&gt;
 *     &lt;/user&gt;
 * &lt;/roles&gt;
 * </pre>
 * 
 * Note that when 'type' is missing in the values for properties or credentials, "String" will be assumed. <br>
 * When no UserAdmin is available at time of installation, the UserAdminStore will keep the data around until one is,
 * and update it with all data it has received up to then. Note that UserAdminStore is intended to work with one
 * UserAdmin at a time.
 */
@ProviderType
public interface UserAdminConfigurator
{
    /**
     * Sets the users found in a document as the only users to be present in the UserAdmin.
     * 
     * @param input
     *            A stream containing the document.
     * @throws java.io.IOException
     *             When there is a problem retrieving the document from the stream.
     */
    public void setUsers(InputStream input) throws IOException;
}
