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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Provides some utility methods for use with reflection.
 */
final class ReflectionUtil {

    static void configureField(Object object, Class<?> iface, Object instance) {
        // Note: Does not check super classes!
        Class<?> clazz = object.getClass();
        do {
            Field[] fields = clazz.getDeclaredFields();
            AccessibleObject.setAccessible(fields, true);
            for (int j = 0; j < fields.length; j++) {
                if (iface.equals(fields[j].getType())) {
                    try {
                        synchronized (new Object()) {
                            fields[j].set(object, instance);
                        }
                    }
                    catch (Exception e) {
                        throw new IllegalStateException("Could not set field " + fields[j].getName() + " on " + object, e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        while (!Object.class.equals(clazz));
    }

    static Object invokeMethod(Object object, String methodName, Class<?>[] signature, Object[] parameters) {
        // Note: Does not check super classes!
        Class<?> clazz = object.getClass();
        try {
            Method method = clazz.getDeclaredMethod(methodName, signature);
            return method.invoke(object, parameters);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
