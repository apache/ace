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
package org.apache.ace.scheduler;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

/**
 * The scheduler periodically runs tasks based on a scheduling recipe. Tasks can be added and
 * removed using the <code>addRunnable</code> and <code>removeRunnable</code> methods. Recipes are
 * supplied using configuration properties using the <code>updated</code> method, or are
 * passed in the task's properties.<br>
 *
 * A task will be scheduled if both a <code>Runnable</code> and a <code>recipe</code> are available
 * for it.
 */
public class Scheduler implements ManagedService {
    protected Map m_tasks = new HashMap/*<String, SchedulerTask>*/();

    public void stop() {
        for (Iterator i = m_tasks.keySet().iterator(); i.hasNext();) {
            String name = (String) i.next();
            SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
            schedTask.stop();
        }
    }

    /**
     * Adds a new runnable to this scheduler. The runnable will be created if necessary, registered, and processed.
     * @param name A name for this task.
     * @param task A runnable to run for this task.
     * @param description A description of the task.
     * @param recipe Optionally, a recipe for running this task.
     * @param recipeOverride Indicates whether or not the <code>recipe</code> passed in prevails over
     * any recipe provided by the <code>Scheduler</code>'s configuration.
     * @throws ConfigurationException When <code>recipe</code> is not <code>null</code>, and cannot
     * be decoded into a recipe.
     */
    public synchronized void addRunnable(String name, Runnable task, String description, Object recipe, boolean recipeOverride) throws ConfigurationException {
        SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
        if (schedTask == null) {
            schedTask = new SchedulerTask(name);
            m_tasks.put(name, schedTask);
        }
        schedTask.updateTask(task, description, recipe, recipeOverride);
        schedTask.process();
    }

    /**
     * Removes a runnable from this scheduler.
     * @param name The name of the runnable. If the name does not indicate a valid runnable,
     * nothing is done.
     */
    public synchronized void removeRunnable(String name) {
        SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
        if (schedTask != null) {
            try {
                schedTask.updateTask(null, null, null, false);
            }
            catch (ConfigurationException e) {
                // Will not occur; a null recipe will not cause an exception.
            }
            if (!schedTask.process()) {
                m_tasks.remove(name);
            }
        }
    }

    /**
     * Updates the configuration of the scheduler. The scheduler expects the configuration
     * to contain recipes for scheduling. The key of a property should be the name identifying
     * a task and the value should be a string describing the scheduling recipe for this task.
     */
    public void updated(Dictionary properties) throws ConfigurationException {
        if (properties != null) {
            // first remove all the old schedules.
            for (Iterator i = m_tasks.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
                schedTask.updateConfigurationRecipe(null);
            }

            // then apply the new ones
            properties.remove(Constants.SERVICE_PID);
            Enumeration keys = properties.keys();
            while (keys.hasMoreElements()) {
                String name = (String) keys.nextElement();
                SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
                if (schedTask == null) {
                    schedTask = new SchedulerTask(name);
                    m_tasks.put(name, schedTask);
                }
                schedTask.updateConfigurationRecipe(properties.get(name));
            }

            // and remove all tasks that now have no schedule or runnable
            for (Iterator i = m_tasks.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                SchedulerTask schedTask = (SchedulerTask) m_tasks.get(name);
                if (!schedTask.process()) {
                    m_tasks.remove(name);
                }
            }
        }
    }
}
