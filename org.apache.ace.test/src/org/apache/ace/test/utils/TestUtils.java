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
package org.apache.ace.test.utils;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Utility class that injects dependencies. Can be used to unit test service implementations.
 */
public class TestUtils {

    /**
     * Configures an object to use a null object for the specified service interface.
     *
     * @param object
     *            the object
     * @param iface
     *            the service interface
     */
    public static <T> void configureObject(Object object, Class<T> iface) {
        configureObject(object, iface, createNullObject(iface));
    }

    /**
     * Creates a null object for a service interface.
     *
     * @param iface
     *            the service interface
     * @return a null object
     */
    @SuppressWarnings("unchecked")
    public static <T> T createNullObject(Class<T> iface) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class[] { iface }, new NullObject());
    }

    /**
     * Wraps the given handler in an adapter that will try to pass on received invocations to the hander if that has an
     * applicable methods else it defaults to a NullObject.
     *
     * @param iface
     *            the service interface
     * @param handler
     *            the handler to pass invocations to.
     * @return an adapter that will try to pass on received invocations to the given handler
     */
    @SuppressWarnings("unchecked")
    public static <T> T createMockObjectAdapter(Class<T> iface, final Object handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class[] { iface }, new NullObject() {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                try {
                    Method bridge = handler.getClass().getMethod(method.getName(), method.getParameterTypes());
                    bridge.setAccessible(true);
                    return bridge.invoke(handler, args);
                }
                catch (NoSuchMethodException ex) {
                    return super.invoke(proxy, method, args);
                }
                catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        });
    }

    /**
     * Configures an object to use a specific implementation for the specified service interface.
     *
     * @param object
     *            the object
     * @param iface
     *            the service interface
     * @param instance
     *            the implementation
     */
    public static void configureObject(Object object, @SuppressWarnings("rawtypes") Class iface, Object instance) {
        Class<?> serviceClazz = object.getClass();

        while (serviceClazz != null) {
            Field[] fields = serviceClazz.getDeclaredFields();
            AccessibleObject.setAccessible(fields, true);
            for (int j = 0; j < fields.length; j++) {
                if (fields[j].getType().equals(iface)) {
                    try {
                        // synchronized makes sure the field is actually written to immediately
                        synchronized (new Object()) {
                            fields[j].set(object, instance);
                        }
                    }
                    catch (Exception e) {
                        throw new IllegalStateException("Could not set field " + fields[j].getName() + " on " + object);
                    }
                }
            }
            serviceClazz = serviceClazz.getSuperclass();
        }
    }

    static class NullObject implements InvocationHandler {
        private static final Boolean DEFAULT_BOOLEAN = Boolean.FALSE;
        private static final Byte DEFAULT_BYTE = new Byte((byte) 0);
        private static final Short DEFAULT_SHORT = new Short((short) 0);
        private static final Integer DEFAULT_INT = new Integer(0);
        private static final Long DEFAULT_LONG = new Long(0);
        private static final Float DEFAULT_FLOAT = new Float(0.0f);
        private static final Double DEFAULT_DOUBLE = new Double(0.0);

        /**
         * Invokes a method on this null object. The method will return a default value without doing anything.
         */
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(Boolean.class) || returnType.equals(Boolean.TYPE)) {
                return DEFAULT_BOOLEAN;
            }
            else if (returnType.equals(Byte.class) || returnType.equals(Byte.TYPE)) {
                return DEFAULT_BYTE;
            }
            else if (returnType.equals(Short.class) || returnType.equals(Short.TYPE)) {
                return DEFAULT_SHORT;
            }
            else if (returnType.equals(Integer.class) || returnType.equals(Integer.TYPE)) {
                return DEFAULT_INT;
            }
            else if (returnType.equals(Long.class) || returnType.equals(Long.TYPE)) {
                return DEFAULT_LONG;
            }
            else if (returnType.equals(Float.class) || returnType.equals(Float.TYPE)) {
                return DEFAULT_FLOAT;
            }
            else if (returnType.equals(Double.class) || returnType.equals(Double.TYPE)) {
                return DEFAULT_DOUBLE;
            }
            else {
                return null;
            }
        }
    }
}
