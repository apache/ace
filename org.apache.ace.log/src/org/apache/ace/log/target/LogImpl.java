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
package org.apache.ace.log.target;

import java.io.IOException;
import java.util.Dictionary;
import org.apache.ace.log.Log;
import org.apache.ace.feedback.Event;
import org.apache.ace.log.target.store.LogStore;
import org.osgi.service.log.LogService;

public class LogImpl implements Log {
    private volatile LogStore m_store;
    private volatile LogService m_log;

    public void log(int type, Dictionary properties) {
        try {
            m_store.put(type, properties);
        }
        catch (NullPointerException e) {
            // if we cannot store the event, we log it to the normal log as extensively as possible
            m_log.log(LogService.LOG_WARNING, "Could not store event: " + (new Event("", 0, 0, 0, type, properties)).toRepresentation(), e);
        }
        catch (IOException e) {
            // if we cannot store the event, we log it to the normal log as extensively as possible
            m_log.log(LogService.LOG_WARNING, "Could not store event: " + (new Event("", 0, 0, 0, type, properties)).toRepresentation(), e);
        }
    }
}