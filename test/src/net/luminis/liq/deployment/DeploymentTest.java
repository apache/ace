package net.luminis.liq.deployment;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.io.InputStream;

import net.luminis.liq.deployment.deploymentadmin.DeploymentAdminDeployer;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.framework.Version;
import org.osgi.service.deploymentadmin.DeploymentAdmin;
import org.osgi.service.deploymentadmin.DeploymentPackage;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DeploymentTest {

    private DeploymentAdminDeployer m_deploymentAdminDeployer;
    private DeploymentPackage m_mockDeploymentPackage;

    private static final String MOCK_NAME = "MockName";
    private static final Version MOCK_VERSION = new Version("0.1");

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_deploymentAdminDeployer = new DeploymentAdminDeployer();
        TestUtils.configureObject(m_deploymentAdminDeployer, LogService.class);
        Object mockDeploymentAdmin = TestUtils.createMockObjectAdapter(DeploymentAdmin.class, new MockDeploymentAdmin());
        TestUtils.configureObject(m_deploymentAdminDeployer, DeploymentAdmin.class, mockDeploymentAdmin);
        m_mockDeploymentPackage = TestUtils.createMockObjectAdapter(DeploymentPackage.class, new MockDeploymentPackage());
    }

    @Test(groups = { UNIT })
    public void testDeployment() throws Exception {
        Object deploymentPackage = m_deploymentAdminDeployer.install(null);
        assert m_deploymentAdminDeployer.getName(deploymentPackage).equals(MOCK_NAME) : "Installation of mock deployment package failed";
        assert m_deploymentAdminDeployer.getVersion(deploymentPackage).equals(MOCK_VERSION) : "Installation of mock deployment package failed";
        assert ((DeploymentPackage) m_deploymentAdminDeployer.list()[0]).getName().equals(MOCK_NAME) : "List result does not match expected result";
        boolean exceptionthrown = false;
        try {
            m_deploymentAdminDeployer.getName(new String("illegalargument"));
        } catch (IllegalArgumentException iae) {
            exceptionthrown = true;
        }
        assert exceptionthrown : "Illegal argument for getName() did not throw exception";
        exceptionthrown = false;
        try {
            m_deploymentAdminDeployer.getVersion(new String("illegalargument"));
        } catch (IllegalArgumentException iae) {
            exceptionthrown = true;
        }
        assert exceptionthrown : "Illegal argument for getVersion() did not throw exception";
    }

    private class MockDeploymentAdmin {
        public DeploymentPackage installDeploymentPackage(InputStream is) {
            return m_mockDeploymentPackage;
        }

        public DeploymentPackage[] listDeploymentPackages() {
            return new DeploymentPackage[] {m_mockDeploymentPackage};
        }
    }

    private class MockDeploymentPackage {
        public String getName() {
            return MOCK_NAME;
        }

        public Version getVersion() {
            return MOCK_VERSION;
        }
    }
}
