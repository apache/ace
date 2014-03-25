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
package org.apache.ace.scheduler.constants;

import aQute.bnd.annotation.ProviderType;

/**
 * Compile time constants for the scheduler.
 */
@ProviderType
public interface SchedulerConstants {
    /** Persistent ID for the scheduler, provided for configuration purposes. */
    public static final String SCHEDULER_PID = "org.apache.ace.scheduler";
    /** Name of the task that will be scheduled. */
    public static final String SCHEDULER_NAME_KEY = "taskName";
    /** Description of the task. */
    public static final String SCHEDULER_DESCRIPTION_KEY = "description";
    /** The recipe describing when the task should be scheduled. */
    public static final String SCHEDULER_RECIPE = "recipe";
    /** Determines if the recipe provided in the task overrides any recipe that is configured. */
    public static final String SCHEDULER_RECIPE_OVERRIDE = "override";
}