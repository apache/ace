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

import org.osgi.service.cm.ConfigurationException;

/**
 * Wrapper class for collecting a <code>Runnable</code> and its corresponding <code>recipe</code>(s).
 * Will schedule the task when both a schedule and a <code>Runnable</code> are available.<br>
 */
public class SchedulerTask {
    private final String m_name;
    private Runnable m_task;
    private String m_description;
    private Object m_configurationRecipe;
    private Object m_taskRecipe;
    private boolean m_recipeOverride;
    private Object m_currentRecipe;
    private Executer m_executer;

    /**
     * Creates instance of this class.
     * @param name The name of the runnable task.
     */
    SchedulerTask(String name) {
        if (name == null) {
            throw new IllegalArgumentException("A SchedulerTask's name cannot be null.");
        }
        m_name = name;
    }

    public String getName() {
        return m_name;
    }

    public String getDescription() {
        return m_description;
    }

    public Runnable getTask() {
        return m_task;
    }

    /**
     * Returns the currently most suited recipe, if any. This function not returning
     * <code>null</code> does not mean that the task is scheduled (it may still be missing
     * a <code>Runnable</code>).
     */
    public Object getCurrentRecipe() {
        return m_currentRecipe;
    }

    /**
     * Indicates whether this task is actually scheduled, and thus will run at some time
     * in the future, unless the schedule is set to <code>null</code>, or the <code>Runnable</code>
     * is removed.
     */
    public boolean isScheduled() {
        return m_executer != null;
    }

    /**
     * States a new set of properties for this task.
     * @param task A runnable to run for this task.
     * @param description A description of the task.
     * @param taskRecipe Optionally, a recipe for running this task.
     * @param recipeOverride Indicates whether or not the <code>recipe</code> passed in prevails over
     * any recipe provided by the <code>Scheduler</code>'s configuration.
     * @throws ConfigurationException When <code>recipe</code> is not <code>null</code>, and cannot
     * be decoded into a recipe.
     */
    public void updateTask(Runnable task, String description, Object taskRecipe, boolean recipeOverride) throws ConfigurationException {
        checkRecipe(taskRecipe);
        m_task = task;
        m_description = description;
        m_taskRecipe = taskRecipe;
        m_recipeOverride = recipeOverride;
    }

    /**
     * States a new recipe as coming from a configuration.
     * @param recipe Optionally, a recipe for running this task.
     * @throws ConfigurationException When <code>recipe</code> is not <code>null</code>, and cannot
     * be decoded into a recipe.
     */
    public void updateConfigurationRecipe(Object recipe) throws ConfigurationException {
        checkRecipe(recipe);
        m_configurationRecipe = recipe;
    }

    public void stop() {
        if (m_executer != null) {
            m_executer.stop();
            m_executer = null;
        }
    }

    public boolean process() {
        Object recipe = findRecipe();
        if ((recipe != null) && (m_task != null)) {
            if (!recipe.equals(m_currentRecipe) && (m_executer != null)) {
                m_executer.stop();
                m_executer = null;
            }
            if (m_executer == null) {
                m_executer = new Executer(m_task);
                m_executer.start(parseScheduleRecipe(recipe));
            }
        }
        else {
            // there is nothing to do, since there is no recipe or task
            stop();
        }
        m_currentRecipe = recipe;
        return ((recipe != null) || (m_task != null));
    }

    /**
     * Finds the most suitable recipe for the given task, using both the properties published
     * with the task, and the scheduler service's properties.
     * @return An <code>Object</code> representing the scheduler recipe, if any. If no suitable recipe can be found,
     * <code>null</code> will be returned.
     */
    private Object findRecipe() {
        if (m_recipeOverride) {
            if (m_taskRecipe != null) {
                return m_taskRecipe;
            }
            else if (m_configurationRecipe != null) {
                return m_configurationRecipe;
            }
        }
        else {
            if (m_configurationRecipe != null) {
                return m_configurationRecipe;
            }
            else if (m_taskRecipe != null) {
                return m_taskRecipe;
            }
        }
        return null;
    }

    /**
     * Decodes an Object into a schedule.
     * @param recipe An object representing a recipe.
     * @return A decoded representation of the recipe.
     */
    private long parseScheduleRecipe(Object recipe) {
        // For now assume just the number of milliseconds is in the string, we may want to do a
        // more 'cron-like' scheduling in the future
        return Long.valueOf(recipe.toString()).longValue();
    }

    /**
     * Helper method that checks whether a recipe is valid.
     * @throws ConfigurationException When <code>recipe</code> is not <code>null</code>, and cannot
     * be decoded into a recipe.
     */
    private void checkRecipe(Object recipe) throws ConfigurationException {
        if (recipe != null) {
            try {
                parseScheduleRecipe(recipe);
            }
            catch (NumberFormatException nfe) {
                throw new ConfigurationException(m_name, "Could not parse scheduling recipe for task", nfe);
            }
        }
    }
}
