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
package org.apache.ace.agent;

import org.apache.ace.agent.impl.DeploymentHandlerImpl;
import org.osgi.service.deploymentadmin.DeploymentException;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Generic exception that is thrown when an installation of an update failed.
 * 
 * @see DeploymentHandlerImpl#install(java.io.InputStream)
 */
@ProviderType
public class InstallationFailedException extends Exception {

    /* DeploymentException codes duplicated for ease of use. */

    public static final int CODE_CANCELLED = 401;
    public static final int CODE_NOT_A_JAR = 404;
    public static final int CODE_ORDER_ERROR = 450;
    public static final int CODE_MISSING_HEADER = 451;
    public static final int CODE_BAD_HEADER = 452;
    public static final int CODE_MISSING_FIXPACK_TARGET = 453;
    public static final int CODE_MISSING_BUNDLE = 454;
    public static final int CODE_MISSING_RESOURCE = 455;
    public static final int CODE_SIGNING_ERROR = 456;
    public static final int CODE_BUNDLE_NAME_ERROR = 457;
    public static final int CODE_FOREIGN_CUSTOMIZER = 458;
    public static final int CODE_BUNDLE_SHARING_VIOLATION = 460;
    public static final int CODE_RESOURCE_SHARING_VIOLATION = 461;
    public static final int CODE_COMMIT_ERROR = 462;
    public static final int CODE_OTHER_ERROR = 463;
    public static final int CODE_PROCESSOR_NOT_FOUND = 464;
    public static final int CODE_TIMEOUT = 465;

    private static final long serialVersionUID = 1L;

    private final int m_code;
    private final String m_origMsg;

    /**
     * Creates a new {@link InstallationFailedException} instance.
     */
    public InstallationFailedException(String msg, DeploymentException cause) {
        super(msg, cause.getCause());
        m_origMsg = cause.getMessage();
        m_code = cause.getCode();
    }

    /**
     * @return the code of the originating deployment exception, see the <tt>CODE_*</tt> constants for more information.
     */
    public int getCode() {
        return m_code;
    }

    /**
     * @return the original message of the exception that caused this exception.
     */
    public String getOriginalMessage() {
        return m_origMsg;
    }

    /**
     * @return a string representation as to why the installation failed, never <code>null</code>.
     */
    public String getReason() {
        switch (m_code) {
            case CODE_BAD_HEADER:
                return "Syntax error in any manifest header";
            case CODE_BUNDLE_NAME_ERROR:
                return "Bundle symbolic name is not the same as defined by the deployment package manifest";
            case CODE_BUNDLE_SHARING_VIOLATION:
                return "Bundle with the same symbolic name already exists";
            case CODE_CANCELLED:
                return "Installation was cancelled";
            case CODE_COMMIT_ERROR:
                return "A Resource Processors involved in the deployment session threw an exception with the CODE_PREPARE error code";
            case CODE_FOREIGN_CUSTOMIZER:
                return "Matched resource processor service is a customizer from another deployment package";
            case CODE_MISSING_BUNDLE:
                return "A bundle in the deployment package is marked as DeploymentPackage-Missing but there is no such bundle in the target deployment package";
            case CODE_MISSING_FIXPACK_TARGET:
                return "Fix pack version range doesn't fit to the version of the target deployment package or the target deployment package of the fix pack doesn't exist";
            case CODE_MISSING_HEADER:
                return "Missing mandatory manifest header";
            case CODE_MISSING_RESOURCE:
                return "A resource in the source deployment package is marked as DeploymentPackage-Missing but there is no such resource in the target deployment package";
            case CODE_NOT_A_JAR:
                return "The InputStream is not a jar";
            case CODE_ORDER_ERROR:
                return "Order of files in the deployment package is bad";
            case CODE_PROCESSOR_NOT_FOUND:
                return "The Resource Processor service with the given PID is not found";
            case CODE_RESOURCE_SHARING_VIOLATION:
                return "An artifact of any resource already exists";
            case CODE_SIGNING_ERROR:
                return "Bad deployment package signing";
            case CODE_TIMEOUT:
                return "Installation of deployment package timed out";
            case CODE_OTHER_ERROR:
                return m_origMsg + " (" + m_code + ")";
            default:
                return "Unknown error condition: " + m_origMsg + " (" + m_code + ")";
        }
    }
}
