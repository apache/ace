package net.luminis.liq.identification.ifconfig;

import static net.luminis.liq.test.utils.TestUtils.UNIT;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class IfconfigIdentificationTest {

    private IfconfigIdentification m_identification;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_identification = new IfconfigIdentification();
        TestUtils.configureObject(m_identification, LogService.class);
    }

    @Test(groups = { UNIT })
    public void testMacAddressVerifying() throws Exception {
        assert m_identification.isValidMac("FF:FF:FF:FF:FF:FF");
        assert m_identification.isValidMac("01:23:45:67:89:01");
        assert m_identification.isValidMac("0D:C3:45:6A:B9:01");
        assert !m_identification.isValidMac("");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:F");
        assert !m_identification.isValidMac("A:B:C:D:E:F");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:FG");
        assert !m_identification.isValidMac("FF:FF:FF:FF:FF:FF:");
        assert !m_identification.isValidMac("FF-FF-FF-FF-FF-FF");
        assert !m_identification.isValidMac("thisisnotamacaddr");
    }
}
