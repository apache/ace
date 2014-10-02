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
package org.apache.ace.agent.launcher;

import java.io.IOException;
import java.net.URL;

/**
 * {@link java.util.ServiceLoader} interface for launcher extension bundles providers.
 */
public interface BundleProvider {
    /**
     * Returns all the bundles to (pre-)install with the launcher.
     * 
     * @param properties
     *            the property provider to access configuration properties, cannot be <code>null</code>.
     * @return an array with the URLs of bundles to install, never <code>null</code>.
     * @throws IllegalArgumentException
     *             in case the given provider was <code>null</code>;
     * @throws IOException
     *             in case of other I/O problems.
     */
    URL[] getBundles(PropertyProvider properties) throws IOException;
}
