package net.luminis.test.osgi.dm;

import org.testng.annotations.Test;

/**
 * A test class that will always fail. It is used as a dummy class to signal that
 * in fact the whole suite of tests did not run. This is usually caused by a test
 * environment not starting up completely (such as dependencies failing).
 */
public class FailTests {
    @SuppressWarnings("unchecked")
    private static Class[] CLASSES;
    private static String REASON;

    @SuppressWarnings("unchecked")
    public static void setClasses(Class[] classes) {
        CLASSES = classes;
    }

    public static void setReason(String reason) {
        REASON = reason;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void fail() {
        StringBuffer tests = new StringBuffer();
        for (Class c : CLASSES) {
            if (tests.length() > 0) {
                tests.append(", ");
            }
            tests.append(c.getName());
        }
        assert false : ("Failed to run test classes: " + tests.toString() + " because: " + REASON);
    }
}
