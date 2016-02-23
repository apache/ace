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
package org.apache.ace.http;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;

public interface HttpConstants {
    /**
     * Name used for binding the servlets from ACE to the servlet context helper from ACE.
     */
    String ACE_WHITEBOARD_CONTEXT_NAME = "org.apache.ace";

    /**
     * Filter used to bind the servlets to the servlet context helper from ACE.
     */
    String ACE_WHITEBOARD_CONTEXT_SELECT_FILTER = "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=" + ACE_WHITEBOARD_CONTEXT_NAME + ")";

}
