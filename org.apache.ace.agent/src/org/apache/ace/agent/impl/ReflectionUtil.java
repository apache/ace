package org.apache.ace.agent.impl;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ReflectionUtil {

    public static void configureField(Object object, Class<?> iface, Object instance) {
        // Note: Does not check super classes!
        Field[] fields = object.getClass().getDeclaredFields();
        AccessibleObject.setAccessible(fields, true);
        for (int j = 0; j < fields.length; j++) {
            if (fields[j].getType().equals(iface)) {
                try {
                    fields[j].set(object, instance);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    throw new IllegalStateException("Coudld not set field " + fields[j].getName() + " on " + object);
                }
            }
        }
    }

    public static Object invokeMethod(Object object, String methodName, Class<?>[] signature, Object[] parameters) {
        // Note: Does not check super classes!
        Class<?> clazz = object.getClass();
        try {
            Method method = clazz.getDeclaredMethod(methodName, signature);
            return method.invoke(object, parameters);
        }
        catch (NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private ReflectionUtil() {

    }
}
