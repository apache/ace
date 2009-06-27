package net.luminis.liq.scheduler;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;


public class ExecuterTest {

    private Semaphore m_sem;

    @BeforeMethod(groups = { UNIT })
    public void setup() {
    }

    /* start task, verify if it has run */
    @Test(groups = { UNIT })
    public void testExecute() throws Exception {
        m_sem = new Semaphore(1);
        Executer executer = new Executer(new Runnable() {
            @Override
            public void run() {
                m_sem.release();
            }
        });
        executer.start(100);
        m_sem.acquire();
        assert m_sem.tryAcquire(2, TimeUnit.SECONDS);
    }

    /* start task, stop it, verify if it executed only once */
    @Test(groups = { UNIT })
    public void testStop() throws Exception {
        m_sem = new Semaphore(2);
        Executer executer = new Executer(new Runnable() {
            @Override
            public void run() {
                try {
                    m_sem.tryAcquire(1, TimeUnit.SECONDS);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        executer.start(10);
        executer.stop();
        Thread.sleep(100);
        assert m_sem.tryAcquire(1, TimeUnit.SECONDS);
    }

}
