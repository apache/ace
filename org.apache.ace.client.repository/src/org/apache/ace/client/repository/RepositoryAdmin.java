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
package org.apache.ace.client.repository;

import java.io.IOException;
import org.apache.ace.client.repository.RepositoryObject.WorkingState;
import org.osgi.service.useradmin.User;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface RepositoryAdmin
{

    public static final String PUBLIC_TOPIC_ROOT = RepositoryAdmin.class.getPackage().getName().replace('.', '/') + "/public/";
    public static final String PRIVATE_TOPIC_ROOT = RepositoryAdmin.class.getPackage().getName().replace('.', '/') + "/private/";

    public static final String TOPIC_ENTITY_ROOT = RepositoryAdmin.class.getSimpleName() + "/";

    public static final String TOPIC_HOLDUNTILREFRESH_SUFFIX = "HOLD";
    
    public static final String TOPIC_REFRESH_SUFFIX = "REFRESH";
    public static final String TOPIC_LOGIN_SUFFIX = "LOGIN";
    public static final String TOPIC_LOGOUT_SUFFIX = "LOGOUT";
    public static final String TOPIC_STATUSCHANGED_SUFFIX = "STATUSCHANGED";
    public static final String TOPIC_FLUSHED_SUFFIX = "FLUSHED";
    public static final String TOPIC_ALL_SUFFIX = "*";

    public static final String PRIVATE_TOPIC_HOLDUNTILREFRESH = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_HOLDUNTILREFRESH_SUFFIX;
    
    /**
     * Indicates a serious change to the structure of the repository, which is too complicated to use
     * the Object's own Changed topic.
     */
    public static final String TOPIC_REFRESH = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REFRESH_SUFFIX;
    
    public static final String PRIVATE_TOPIC_REFRESH = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_REFRESH_SUFFIX;
    /**
     * Indicates a successful login; the model will now be filled, as signaled by the earlier TOPIC_REFRESH.
     */
    public static final String TOPIC_LOGIN = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_LOGIN_SUFFIX;
    
    public static final String PRIVATE_TOPIC_LOGIN = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_LOGIN_SUFFIX;
    /**
     * Indicates a successful logout; the model will now be empty, as signaled by the earlier TOPIC_REFRESH.
     */
    public static final String TOPIC_LOGOUT = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_LOGOUT_SUFFIX;
    
    public static final String PRIVATE_TOPIC_LOGOUT = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_LOGOUT_SUFFIX;
    /**
     * Signals that isCurrent or isModified have (potentially) changed.
     */
    public static final String TOPIC_STATUSCHANGED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_STATUSCHANGED_SUFFIX;
    
    public static final String PRIVATE_TOPIC_STATUSCHANGED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_STATUSCHANGED_SUFFIX;
    /**
     * Signals that a flush() has been done.
     */
    public static final String TOPIC_FLUSHED = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_FLUSHED_SUFFIX;
    
    public static final String PRIVATE_TOPIC_FLUSHED = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_FLUSHED_SUFFIX;

    public static final String TOPIC_ALL = PUBLIC_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;
    
    public static final String PRIVATE_TOPIC_ALL = PRIVATE_TOPIC_ROOT + TOPIC_ENTITY_ROOT + TOPIC_ALL_SUFFIX;

    /**
     * Checks out the most recent version of the repositories from <code>login</code>.
     * @throws java.io.IOException Thrown when we are not logged in, or when there is a problem
     * communicating with either the local repository or the remote one.
     */
    public void checkout() throws IOException;

    /**
     * Commits the most what we have in memory to the repositories from <code>login</code>.
     * @throws java.io.IOException Thrown when we are not logged in, or when there is a problem
     * communicating with either the local repository or the remote one.
     */
    public void commit() throws IOException;

    /**
     * Reverts what we have in memory to the most recent one that was checked our or
     * committed.
     * @throws java.io.IOException Thrown when we are not logged in, or when there is a problem
     * communicating with either the local repository or the remote one.
     */
    public void revert() throws IOException;

    /**
     * Indicates that the version on which the changes are made is the most recent on the server.
     * This indication only applies to repositories for which write access has been received.
     * @throws java.io.IOException Thrown when there is a problem communicating with the backup repository,
     * which keeps track of the local copies of the repository.
     */
    public boolean isCurrent() throws IOException;

    /**
     * Indicates whether the data we have (in memory and persisted) has changed in respect to
     * what is on the server.
     * @throws java.io.IOException Thrown when there is a problem communicating with the backup repository,
     * which keeps track of the local copies of the repository.
     */
    public boolean isModified() throws IOException;

    /**
     * Writes what we have in memory to a backup repository, so it can be persisted between runs
     * of the client, without committing it to the remote repository.
     * @throws java.io.IOException Thrown when there is a problem communicating with the backup repository,
     * which keeps track of the local copies of the repository.
     */
    public void flush() throws IOException;

    /**
     * Writes what we have in memory to a backup repository, and prepares the repository for use by
     * a new user.
     * @param force Indicates that, even when an exception is thrown, we still want to log the
     * user out.
     * @throws java.io.IOException Thrown when there is a problem communicating with the backup repository,
     * which keeps track of the local copies of the repository. If this exception is thrown,
     * the user is still logged in, unless <code>force = true</code>.
     * @throws IllegalStateException Thrown if no user is logged in.
     */
    public void logout(boolean force) throws IOException, IllegalStateException;

    /**
     * Creates a new login context.
     * @param user The user to use for this context.
     * @return A new RepositoryAdminLoginContext,
     */
    public RepositoryAdminLoginContext createLoginContext(User user);

    /**
     * Logs in using the given RepositoryAdminLoginContext; use createLoginContext for an initial
     * RepositoryAdminLoginContext.
     * @param context The context to use for this login.
     * @throws java.io.IOException Thrown when there is a problem communicating with the backup repository,
     * which keeps track of the local copies of the repository.
     * @throws IllegalArgumentException If <code>context</code> was not one that was
     * created by <code>createLoginContext</code<.
     * @throws IllegalStateException If there already is a user logged in.
     */
    public void login(RepositoryAdminLoginContext context) throws IOException;

    /**
     * Return the working state of the given object. If the object is not part of any
     * repository managed by this admin, <code>New</code> will be returned.
     * @param object A repository object.
     * @return The current working state of this object.
     */
    public WorkingState getWorkingState(RepositoryObject object);

    /**
     * Gets the number of objects of a given class with a given state. Note that
     * this class applies to all objects of that class, and all its descendents.
     * @param clazz The class of objects to be counted.
     * @param state A working state.
     * @return The number of objects which are (or descend from) the given class,
     * and have the given working state.
     */
    public int getNumberWithWorkingState(Class<? extends RepositoryObject> clazz, WorkingState state);
    
    /**
     * Cleans up the local files that make up the client-side cache. This method
     * can be invoked after logging out of a session to clean up. This operation
     * is optional, since you might want to be able to log back in in which case
     * you probably don't want to delete the cache.
     */
    public void deleteLocal();
}