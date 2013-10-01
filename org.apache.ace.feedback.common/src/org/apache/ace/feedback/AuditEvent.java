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
package org.apache.ace.feedback;

/**
 * Auditlog event. This interface defines a set of constants to define the eventtypes used for the auditlog.
 * These auditlog constants identify the actions on the target that the server needs to process and validate deployment. 
 * 
 * These feedback events are recorded in the auditlog feedbackchannel.
 */
public interface AuditEvent
{

    public static final String KEY_ID = "id";
    public static final String KEY_NAME = "name";
    public static final String KEY_VERSION = "version";
    public static final String KEY_LOCATION = "location";
    public static final String KEY_MSG = "msg";
    public static final String KEY_TYPE = "type";
    public static final String KEY_SUCCESS = "success";

    public static final int BUNDLE_BASE = 0;

    /**
     * When a bundle is installed, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * <dt>name</dt>
     * <dd>symbolic name (if present)</dd>
     * <dt>version</dt>
     * <dd>bundle version</dd>
     * <dt>location</dt>
     * <dd>bundle location</dd>
     * </dl>
     */
    public static final int BUNDLE_INSTALLED = (BUNDLE_BASE + 1);

    /**
     * When a bundle is resolved, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_RESOLVED = (BUNDLE_BASE + 2);

    /**
     * When a bundle is started, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_STARTED = (BUNDLE_BASE + 3);

    /**
     * When a bundle is stopped, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_STOPPED = (BUNDLE_BASE + 4);

    /**
     * When a bundle is unresolved, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_UNRESOLVED = (BUNDLE_BASE + 5);

    /**
     * When a bundle is updated, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * <dt>version</dt>
     * <dd>bundle version</dd>
     * <dt>location</dt>
     * <dd>bundle location</dd>
     * </dl>
     */
    public static final int BUNDLE_UPDATED = (BUNDLE_BASE + 6);

    /**
     * When a bundle is uninstalled, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_UNINSTALLED = (BUNDLE_BASE + 7);

    /**
     * When a bundle is starting, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_STARTING = (BUNDLE_BASE + 8);

    /**
     * When a bundle is stopping, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier</dd>
     * </dl>
     */
    public static final int BUNDLE_STOPPING = (BUNDLE_BASE + 9);



    public static final int FRAMEWORK_BASE = 1000;

    /**
     * When a framework info message occurs, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier (if present)</dd>
     * <dt>msg</dt>
     * <dd>the error message (if present)</dd>
     * <dt>type</dt>
     * <dd>the fully qualified error type (if present)</dd>
     * </dl>
     */
    public static final int FRAMEWORK_INFO = (FRAMEWORK_BASE + 1);

    /**
     * When a framework warning message occurs, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier (if present)</dd>
     * <dt>msg</dt>
     * <dd>the error message (if present)</dd>
     * <dt>type</dt>
     * <dd>the fully qualified error type (if present)</dd>
     * </dl>
     */
    public static final int FRAMEWORK_WARNING = (FRAMEWORK_BASE + 2);

    /**
     * When a framework error message occurs, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>bundle identifier (if present)</dd>
     * <dt>msg</dt>
     * <dd>the error message (if present)</dd>
     * <dt>type</dt>
     * <dd>the fully qualified error type (if present)</dd>
     * </dl>
     */
    public static final int FRAMEWORK_ERROR = (FRAMEWORK_BASE + 3);

    /**
     * When a framework refresh occurs, this event is logged.
     */
    public static final int FRAMEWORK_REFRESH = (FRAMEWORK_BASE + 4);

    /**
     * When a framework start occurs, this event is logged.
     */
    public static final int FRAMEWORK_STARTED = (FRAMEWORK_BASE + 5);

    /**
     * When a framework start level change occurs, this event is logged.
     */
    public static final int FRAMEWORK_STARTLEVEL = (FRAMEWORK_BASE + 6);

    public static final int DEPLOYMENTADMIN_BASE = 2000;

    /**
     * When a deployment package is installed, this event is logged with the following properties:
     * <dl>
     * <dt>name</dt>
     * <dd>deployment package symbolic name</dd>
     * </dl>
     */
    public static final int DEPLOYMENTADMIN_INSTALL = (DEPLOYMENTADMIN_BASE + 1);

    /**
     * When a deployment package is uninstalled, this event is logged with the following properties:
     * <dl>
     * <dt>name</dt>
     * <dd>deployment package symbolic name</dd>
     * </dl>
     */
    public static final int DEPLOYMENTADMIN_UNINSTALL = (DEPLOYMENTADMIN_BASE + 2);

    /**
     * When a deployment package operation has completed, this event is logged with the following properties:
     * <dl>
     * <dt>name</dt>
     * <dd>deployment package symbolic name</dd>
     * <dt>success</dt>
     * <dd>whether the operation was successful or not</dd>
     * <dt>version</dt>
     * <dd>deployment package version</dd>
     * </dl>
     */
    public static final int DEPLOYMENTADMIN_COMPLETE = (DEPLOYMENTADMIN_BASE + 3);

    public static final int DEPLOYMENTCONTROL_BASE = 3000;

    /**
     * Before a deployment package install begins, this event is logged with the following properties:
     * <dl>
     * <dt>url</dt>
     * <dd>deployment package url</dd>
     * <dt>version</dt>
     * <dd>deployment package version</dd>
     * </dl>
     */
    public static final int DEPLOYMENTCONTROL_INSTALL = (DEPLOYMENTCONTROL_BASE + 1);
    
    /** Base event type for all target properties related events. */
    public static final int TARGETPROPERTIES_BASE = 4000;
    
    /** Sets a new collection of properties for this target. */
    public static final int TARGETPROPERTIES_SET = (TARGETPROPERTIES_BASE + 1);
}