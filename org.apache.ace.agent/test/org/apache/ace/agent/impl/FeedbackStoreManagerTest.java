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
package org.apache.ace.agent.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;

import org.apache.ace.agent.AgentContext;
import org.apache.ace.agent.testutil.BaseAgentTest;
import org.apache.ace.feedback.Event;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Testing {@link FeedbackStoreManager}.
 */
public class FeedbackStoreManagerTest extends BaseAgentTest {

    private AgentContext m_agentContext;

    @BeforeMethod
    public void setUpAgain(Method method) throws Exception {
        m_agentContext = mockAgentContext();
        replayTestMocks();
    }

    @AfterMethod
    public void tearDownAgain(Method method) throws Exception {
        verifyTestMocks();
        clearTestMocks();
    }

    @Test
    public void testEmptyRepository() throws Exception {
        FeedbackStoreManager feedbackStoreManager = new FeedbackStoreManager(m_agentContext, "test");
        assertNotNull(getStoreID(feedbackStoreManager));
    }

    @Test
    public void testExceptionHandling() throws Exception {
        FeedbackStoreManager feedbackStoreManager = new FeedbackStoreManager(m_agentContext, "test");

        feedbackStoreManager.forceCreateNewStore();

        SortedSet<Long> allFeedbackStoreIDs = feedbackStoreManager.getAllFeedbackStoreIDs();
        assertEquals(allFeedbackStoreIDs.size(), 2);
    }

    @Test
    public void testReadWriteLogEvents() throws Exception {
        FeedbackStoreManager feedbackStoreManager = new FeedbackStoreManager(m_agentContext, "test");
        long storeID = getStoreID(feedbackStoreManager);
        assertEquals(feedbackStoreManager.getHighestEventID(storeID), 0);

        feedbackStoreManager.write(1, new HashMap<String, String>());
        assertEquals(feedbackStoreManager.getHighestEventID(storeID), 1);

        assertEquals(feedbackStoreManager.getEvents(storeID, 1, 1).size(), 1);
    }

    @Test
    public void testReadFromOldStore() throws Exception {
        FeedbackStoreManager feedbackStoreManager = new FeedbackStoreManager(m_agentContext, "test");
        long storeID = getStoreID(feedbackStoreManager);

        assertEquals(feedbackStoreManager.getHighestEventID(storeID), 0);

        feedbackStoreManager.write(1, new HashMap<String, String>());

        assertEquals(feedbackStoreManager.getHighestEventID(storeID), 1);
        assertEquals(feedbackStoreManager.getEvents(storeID, 0, 1).size(), 1);

        feedbackStoreManager.forceCreateNewStore();

        assertEquals(feedbackStoreManager.getEvents(storeID, 0, 1).size(), 1);
    }

    @Test
    public void testLogfileRotation() throws Exception {
        int maxSize = 100 * 1024;

        FeedbackStoreManager feedbackStoreManager = new FeedbackStoreManager(m_agentContext, "test", maxSize, maxSize / 5);
        long storeID = getStoreID(feedbackStoreManager);
        
        int recordCount = 1000;

        assertEquals(feedbackStoreManager.getHighestEventID(storeID), 0);
        // absolutely exceed the set filesize for this store
        for (int i = 0; i < recordCount; i++) {
            HashMap<String, String> eventProps = new HashMap<>();
            eventProps.put("key", "value" + i);
            feedbackStoreManager.write(i, eventProps);
        }

        File[] logFiles = getLogFiles();
        assertTrue(logFiles.length > 1);

        // take the last 1000 events...
        List<Event> events = feedbackStoreManager.getEvents(storeID, 1, 1000);
        assertEquals(events.size(), 1000);

        long logFileSize = 0;
        for (File file : logFiles) {
            logFileSize += file.length();
        }
        assertTrue(logFileSize < maxSize);
    }

    private long getStoreID(FeedbackStoreManager feedbackStoreManager) throws Exception {
        SortedSet<Long> allFeedbackStoreIDs = feedbackStoreManager.getAllFeedbackStoreIDs();
        assertEquals(allFeedbackStoreIDs.size(), 1);
        return allFeedbackStoreIDs.first();
    }

    private File[] getLogFiles() {
        File[] files = new File(m_agentContext.getWorkDir(), "feedback").listFiles();
        // sort files on storeId and fileNumber
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File f1, File f2) {
                int result = (int) (getStoreId(f1) - getStoreId(f2));
                if (result == 0) {
                    int f1Number = getLogfileNumber(f1.getName(), getStoreName(getStoreId(f1)));
                    int f2Number = getLogfileNumber(f2.getName(), getStoreName(getStoreId(f2)));
                    result = f1Number - f2Number;
                }
                return result;
            }

        });
        return files;
    }

    private String getStoreName(long storeId) {
        return "test-" + storeId;
    }

    private long getStoreId(File storeFile) {
        // remove the extension from the filename
        String storeName = storeFile.getName().replaceFirst("[.][^.]+$", "");
        return Long.parseLong(storeName.replace("test-", ""));
    }

    private int getLogfileNumber(String logfileName, String storeName) {
        String extension = logfileName.replace(storeName + ".", "");
        return Integer.parseInt(extension);

    }
}
