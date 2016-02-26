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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ExecuterTest {

    private Semaphore m_sem;

    @BeforeMethod()
    public void setup() {
    }

    /* start task, verify if it has run */
    @Test()
    public void testExecute() throws Exception {
        m_sem = new Semaphore(1);
        Executer executer = new Executer(new Runnable() {
            public void run() {
                m_sem.release();
            }
        });
        executer.start(100);
        m_sem.acquire();
        assert m_sem.tryAcquire(2, TimeUnit.SECONDS);
    }

    /* start task, stop it, verify if it executed only once */
    @Test()
    public void testStop() throws Exception {
        m_sem = new Semaphore(2);
        Executer executer = new Executer(new Runnable() {
            public void run() {
                try {
                    m_sem.tryAcquire(1, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        executer.start(10);
        executer.stop();
        Thread.sleep(100);
        assert m_sem.tryAcquire(1, TimeUnit.SECONDS);
    }

    /*
     * start task, which executes longer than the task interval specifies, causing multiple concurrent tasks to be
     * started.
     */
    @Test()
    public void testTooLongTask() throws Exception {
        final CountDownLatch latch = new CountDownLatch(5);

        Executer executer = new Executer(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(20);
                    latch.countDown();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        executer.start(10);
        assert latch.await(1, TimeUnit.SECONDS);
    }
}
