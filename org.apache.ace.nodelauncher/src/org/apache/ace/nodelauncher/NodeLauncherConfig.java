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
package org.apache.ace.nodelauncher;

import aQute.bnd.annotation.ProviderType;

/**
 * Empty interface that should be implemented by NodeLauncherConfig implmentation. The interface is empty
 * because configuration is very different for each kind of node (e.g. an embedded node vs a jclouds node) it's
 * not possible to have a standard interface for configuration.
 * The interface is required however so that the NodeLauncherConfig can still be independent of specific node types.
 * Clients of this interface (e.g. UI) should cast this interface to the concrete implementation it's built for.
 *
 */
@ProviderType
public interface NodeLauncherConfig {

}
