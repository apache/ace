package net.luminis.liq.test.useradminconfigurator;

import static net.luminis.liq.test.utils.TestUtils.INTEGRATION;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import net.luminis.liq.repository.Repository;

import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class ConfiguratorTest {

    public static Object m_instance;

    private volatile Repository m_repository;
    private volatile UserAdmin m_userAdmin;

    public ConfiguratorTest() {
        synchronized (ConfiguratorTest.class) {
            if (m_instance == null) {
                m_instance = this;
            }
        }
    }

    @Factory
    public Object[] createInstances() {
        synchronized (ConfiguratorTest.class) {
            return new Object[] { m_instance };
        }
    }

    /**
     * Creates a file in the repository, and waits for the UserAdmin to have a new user
     * present, and inspects that user.
     */
    @Test(groups = { INTEGRATION })
    public void configuratorTest() throws IllegalArgumentException, IOException, InterruptedException {
        ByteArrayInputStream bis = new ByteArrayInputStream((
            "<roles>" +
            "    <user name=\"TestUser\">" +
            "    <properties>" +
            "        <email>testUser@luminis.nl</email>" +
            "    </properties>" +
            "    <credentials>" +
            "        <password type=\"String\">swordfish</password>" +
            "        <certificate type=\"byte[]\">42</certificate>" +
            "    </credentials>" +
            "    </user>" +
            "</roles>").getBytes());

        m_repository.commit(bis, m_repository.getRange().getHigh());

        User user = (User) m_userAdmin.getRole("TestUser");
        int count = 0;
        while ((user == null) && (count < 16)) {
            Thread.sleep(250);
            user = (User) m_userAdmin.getRole("TestUser");
            count++;
        }
        if (user == null) {
            assert false : "Even after four seconds, our user is not present.";
        }

        boolean foundPassword = false;
        boolean foundCertificate = false;
        count = 0;
        while (!foundPassword & !foundCertificate && (count < 20)) {
            // Note: there is a window between the creation of the user and the setting of the properties.
            Thread.sleep(50);
            foundPassword = user.hasCredential("password", "swordfish");
            foundCertificate = user.hasCredential("certificate", new byte[] {'4', '2'});
        }

        assert foundPassword : "A second after our user becoming available, there is no password.";
        assert foundCertificate : "A second after our user becoming available, there is no certificate.";
    }


}
