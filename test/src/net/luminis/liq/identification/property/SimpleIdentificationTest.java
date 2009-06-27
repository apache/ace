package net.luminis.liq.identification.property;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.util.Properties;

import net.luminis.liq.identification.property.constants.IdentificationConstants;
import net.luminis.liq.test.utils.TestUtils;

import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class SimpleIdentificationTest {
    private PropertyBasedIdentification m_identification;

    private static final String TEST_ID = "testGatewayID";

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_identification = new PropertyBasedIdentification();
        TestUtils.configureObject(m_identification, LogService.class);
    }

    /**
     * Test simple identification
     *
     * @throws Exception
     */
    @SuppressWarnings("serial")
    @Test(groups = { UNIT })
    public void testSimpleIdentification() throws Exception {
        m_identification.updated(
            new Properties() {
                {put(IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, TEST_ID);}
            });
        assert TEST_ID.equals(m_identification.getID()) : "gateway ID does not match configured gateway ID";
    }
}
