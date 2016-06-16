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
package org.apache.ace.obr.metadata;

import java.io.File;
import java.io.IOException;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface MetadataGenerator
{

    /**
     * Generates the index.xml based upon the new set of Bundles in the given directory. The xml is created
     * as result of this method in the given directory in a file called index.xml.
     * This methods creates the file in an atomic fashion (this includes retrying to overwrite an existing file until success).
     *
     * @param directory the location where to store the newly created index.xml
     *
     * @throws java.io.IOException If I/O problems occur when generating the new meta data index file.
     */
    public void generateMetadata(File directory) throws IOException;
}