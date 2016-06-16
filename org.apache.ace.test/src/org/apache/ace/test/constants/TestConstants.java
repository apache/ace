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
package org.apache.ace.test.constants;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Constants for global test parameters. Use these throughout the test
 * codebase. Make sure to only use public static finals here, which
 * can be inlined by the compiler.
 */
@ProviderType
public interface TestConstants {
    /** The port the webserver is running on for testing. */
    public static final int PORT = Integer.getInteger("org.osgi.service.http.port", 8080);
}
