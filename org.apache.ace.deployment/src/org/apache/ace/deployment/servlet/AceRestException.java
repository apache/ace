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
package org.apache.ace.deployment.servlet;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

/**
 * Handle common rest problems here.
 * This can be thrown by services (unaware of how to handle it in the end) and handeled inside the catching servlet.
 */
public class AceRestException extends Exception {
    private final int m_statusCode;
    private final String m_description;

    public AceRestException(int statusCode, String description) {
        super(statusCode + ":" + description);
        m_statusCode = statusCode;
        m_description = description;
    }

    /**
     * handling code where we turn <code>this</code> into http error.
     *
     * @param response
     */
    public boolean handleAsHttpError(HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
            response.sendError(m_statusCode, m_description);
            return true;
        }
        return false;
    }
}