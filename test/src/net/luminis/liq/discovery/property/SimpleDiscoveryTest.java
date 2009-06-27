package net.luminis.liq.discovery.property;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.discovery.property.constants.DiscoveryConstants;

import org.osgi.service.cm.ConfigurationException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class SimpleDiscoveryTest {

    private static final String SERVERURL_KEY = DiscoveryConstants.DISCOVERY_URL_KEY;
    private static final String VALID_URL = "http://test.url.com:8080";
    private static final String INVALID_URL = "malformed url";

    private PropertyBasedDiscovery m_discovery;

    @BeforeTest(alwaysRun = true)
    protected void setUp() throws Exception {
        m_discovery = new PropertyBasedDiscovery();
    }

    /**
     * Test if setting a valid configuration is handled correctly
     * @throws Exception
     */
    @Test(groups = { UNIT })
    public void simpleDiscoveryValidConfiguration() throws ConfigurationException {
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(SERVERURL_KEY, VALID_URL);
        m_discovery.updated(properties);
        URL url = m_discovery.discover();
        assert VALID_URL.equals(url.toString()) : "Configured url was not returned";
    }

    /**
     * Test if setting an invalid configuration is handled correctly.
     * @throws ConfigurationException
     */
    @Test(groups = {UNIT}, expectedExceptions = ConfigurationException.class)
    public void simpleDiscoveryInvalidConfiguration() throws ConfigurationException {
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(SERVERURL_KEY, INVALID_URL);
        m_discovery.updated(properties);
    }

    /**
     * Test if supplying an empty configuration results in the service's default being used.
     * @throws ConfigurationException
     */
    @Test(groups = {UNIT})
    public void simpleDiscoveryEmptyConfiguration() throws ConfigurationException {
        // set valid config
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put(SERVERURL_KEY, VALID_URL);
        m_discovery.updated(properties);
        // set empty config
        m_discovery.updated(null);
    }
}
