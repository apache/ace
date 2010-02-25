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
package org.apache.ace.server.action;

/**
 * MessageAction is an action that sends out messages. To do so, it needs a user, and
 * both a short and a long description. Is it up to the implementation to do something with
 * it, e.g. use the short description and a subject for an email message, or use the short
 * description as the text in an SMS.
 */
public interface MessageAction extends Action {
    /**
     * Key for the event properties containing a User.
     */
    public static final String USER = "user";
    /**
     * Key for the event properties containing a description as a String.
     */
    public static final String DESCRIPTION = "description";
    /**
     * Key for the event properties containing a very short description as a String.
     */
    public static final String SHORT_DESCRIPTION = "shortDescription";
}
