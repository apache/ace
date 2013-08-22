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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ace.agent.FeedbackChannel;
import org.apache.ace.agent.FeedbackHandler;

/**
 * Default implementation of the feedback handler.
 */
public class FeedbackHandlerImpl extends HandlerBase implements FeedbackHandler {

    private final Map<String, FeedbackChannelImpl> m_feedbackChannels = new HashMap<String, FeedbackChannelImpl>();

    public FeedbackHandlerImpl() {
        super("feedback");
    }

    @Override
    protected void onStart() throws Exception {
        // TODO get from configuration
        m_feedbackChannels.put("auditlog", new FeedbackChannelImpl(getAgentContext(), "auditlog"));
    }

    @Override
    protected void onStop() throws Exception {
        m_feedbackChannels.clear();
    }

    @Override
    public List<String> getChannelNames() {
        List<String> names = new ArrayList<String>(m_feedbackChannels.keySet());
        return java.util.Collections.unmodifiableList(names);
    }

    @Override
    public FeedbackChannel getChannel(String name) {
        return m_feedbackChannels.get(name);
    }
}
