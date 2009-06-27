package net.luminis.test.osgi.dm;

import static net.luminis.liq.test.utils.TestUtils.INTEGRATION;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.luminis.liq.test.utils.TestUtils;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.apache.felix.dependencymanager.Service;
import org.apache.felix.dependencymanager.ServiceStateListener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.testng.TestNG;

/**
 * Base class for integration tests within an OSGi framework.
 */
public abstract class TestActivatorBase extends DependencyActivatorBase implements ServiceStateListener, Runnable {
    private BundleContext m_context;
    private Semaphore m_semaphore;
    @SuppressWarnings("unchecked")
    private Class[] m_testClasses;

    @SuppressWarnings("unchecked")
    @Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {
        m_context = context;
        m_testClasses = getTestClasses();
        initServices(context, manager);
        List services = manager.getServices();
        m_semaphore = new Semaphore(1 - services.size());
        for (Object s : services) {
            Service service = (Service) s;
            service.addStateListener(this);
        }
        Thread t = new Thread(this, "TestNG Runner");
        t.start();
    }

    public void run() {
        Thread.currentThread().setContextClassLoader(TestActivatorBase.class.getClassLoader());
        TestNG testng = new TestNG();
        // TODO come up with a good name here
        testng.setDefaultSuiteName("Integration Test " + getClass().getName());

        // ensure all bundles are started correctly
        long timeout = System.currentTimeMillis() + 15000;
        Bundle[] bundles = m_context.getBundles();
        boolean allActive = true;
        String nonActiveBundles = "";
        do {
            if (System.currentTimeMillis() > timeout) {
                fail(testng, "Waiting for all bundles to become active timed out. Inactive bundles: " + nonActiveBundles);
            }
            StringBuffer nonActive = new StringBuffer("");
            allActive = true;
            for (Bundle b : bundles) {
                if (b.getState() != Bundle.ACTIVE) {
                    allActive = false;
                    if (nonActive.length() > 0) {
                        nonActive.append(", ");
                    }
                    nonActive.append(b.getSymbolicName());
                }
            }
            nonActiveBundles = nonActive.toString();
            if (!allActive) {
                try { TimeUnit.MILLISECONDS.sleep(50); } catch (InterruptedException e) {}
            }
        }
        while (allActive == false);

        // wait for the service to be started
        try {
            if (m_semaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                // perform tests
                testng.setTestClasses(m_testClasses);
                testng.setGroups(INTEGRATION);
                testng.setExcludedGroups(TestUtils.BROKEN);
                testng.run();
            }
            else {
                fail(testng, "Service(s) never started.");
            }
        }
        catch (InterruptedException ie) {
            fail(testng, "Thread was interrupted.");
        }
        // shutdown after the tests have been run
        try {
            m_context.getBundle(0).stop();
            // Felix does not quit itself, which is a bug, so wait and exit manually
            try { TimeUnit.MILLISECONDS.sleep(50); } catch (InterruptedException e) {}
            try { System.exit(0); } catch (NoClassDefFoundError e) {}
        }
        catch (BundleException e) {
            e.printStackTrace();
            System.exit(20);
        }
    }

    private void fail(TestNG testng, String reason) {
        FailTests.setClasses(getTestClasses());
        FailTests.setReason(reason);
        testng.setTestClasses(new Class[] { FailTests.class });
        testng.run();
    }

    /**
     * Return the service object that specifies the test service and its
     * dependencies.
     */
    protected abstract void initServices(BundleContext context, DependencyManager manager);

    /**
     * Return a list of test classes that are run.
     */
    @SuppressWarnings("unchecked")
    protected abstract Class[] getTestClasses();

    @Override
    public void destroy(BundleContext context, DependencyManager manager) throws Exception {
        for (Object s : manager.getServices()) {
            Service service = (Service) s;
            service.removeStateListener(this);
        }
    }

    public void started(Service svc) {
        m_semaphore.release();
    }

    public void starting(Service svc) {
    }

    public void stopped(Service svc) {
    }

    public void stopping(Service svc) {
    }
}
