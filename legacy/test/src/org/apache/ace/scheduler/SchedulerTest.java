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

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.util.Properties;

import org.apache.ace.test.utils.TestUtils;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class SchedulerTest {

    private Scheduler m_scheduler;

    @BeforeMethod(alwaysRun = true)
    protected void setUp() throws Exception {
        m_scheduler = new Scheduler();
        TestUtils.configureObject(m_scheduler, LogService.class);
    }

    @Test(groups = { UNIT }, expectedExceptions = IllegalArgumentException.class)
    public synchronized void testIllegalCreation() {
        new SchedulerTask(null);
    }

    @Test(groups = { UNIT })
    public synchronized void testUpdate() throws Exception {
        Properties props = new Properties();
        props.put("local.mock.task1", 1000l);
        props.put("local.mock.task2", 2000l);
        props.put("local.mock.task3", 3000l);
        m_scheduler.updated(props);
        assert m_scheduler.m_tasks.size() == props.size() : "Exactly three schedules should be known to the scheduler";
        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(1000)) : "The schedule for mock task 1 should specify interval 1000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();

        props.put("local.mock.task1", 4000l);
        m_scheduler.updated(props);
        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(4000)) : "The schedule for mock task 1 should specify interval 4000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();
        assert !((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "Since we have not provided a runnable for the scheduler, the tasks should not be scheduled.";
    }

    @Test(groups = { UNIT }, expectedExceptions = ConfigurationException.class)
    public synchronized void testIllegalUpdate() throws Exception {
        Properties props = new Properties();
        props.put("local.mock.task1", "invalidValue");
        m_scheduler.updated(props);
        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", null, false);
    }

    @Test(groups = { UNIT })
    public synchronized void testAddTask() throws Exception {
        assert m_scheduler.m_tasks.isEmpty();
        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", null, false);
        assert m_scheduler.m_tasks.size() == 1 : "Exactly one task should be known to the scheduler";
        SchedulerTask task = (SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1");
        assert "local.mock.task1".equals(task.getName()) : "Task that was just added has a different name than expected";
    }

    @Test(groups = { UNIT })
    public synchronized void testRemoveTask() throws Exception {
        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", null, false);
        m_scheduler.removeRunnable("nonExistent");
        assert m_scheduler.m_tasks.size() == 1 : "Number of tasks known to the scheduler should still be one after removing a non-existing task";
        m_scheduler.removeRunnable("local.mock.task1");
        assert m_scheduler.m_tasks.isEmpty() : "Number of tasks known to the scheduler should be zero after removing the task we just added";
    }

    @Test(groups = { UNIT })
    public synchronized void testProcessTask() throws Exception {
        Properties props = new Properties();
        props.put("local.mock.task1", 1000);
        m_scheduler.updated(props);

        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", null, false);

        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "An executer should exist after adding a matching task and scheduling-recipe";
    }

    @Test(groups = { UNIT })
    public synchronized void testSchedulePrevailanceAndRemoval() throws Exception {
        Properties props = new Properties();
        props.put("local.mock.task1", 1000l);
        m_scheduler.updated(props);

        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(1000)) : "The schedule for mock task 1 should specify interval 1000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();
        assert !((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "Since we have not provided a runnable for the scheduler, the tasks should not be scheduled.";

        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", 2000l, true);

        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(2000)) : "The schedule for mock task 1 should specify interval 2000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();
        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "Since we have now provided a runnable for the scheduler, the tasks should be scheduled.";

        m_scheduler.addRunnable("local.mock.task1", new Runnable() {
            @Override
            public void run() {
            }}, "Dummy testing task", 2000l, false);

        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(1000)) : "The schedule for mock task 1 should specify interval 1000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();
        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "Since we have now provided a runnable for the scheduler, the tasks should be scheduled.";

        props = new Properties();
        m_scheduler.updated(props);

        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe().equals(new Long(2000)) : "The schedule for mock task 1 should specify interval 2000, but it specifies " + ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).getCurrentRecipe();
        assert ((SchedulerTask) m_scheduler.m_tasks.get("local.mock.task1")).isScheduled() : "Since we have now provided a runnable for the scheduler, the tasks should be scheduled.";

        m_scheduler.removeRunnable("local.mock.task1");

        assert m_scheduler.m_tasks.size() == 0 : "We have now removed all information about mock task 1, so it should be gone now.";
    }
}
