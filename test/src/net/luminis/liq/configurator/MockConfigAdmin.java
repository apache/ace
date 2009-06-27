package net.luminis.liq.configurator;

import java.io.IOException;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

public class MockConfigAdmin implements ConfigurationAdmin {

    private Configuration m_configuration = new MockConfiguration();

    public Configuration createFactoryConfiguration(String arg0) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public Configuration createFactoryConfiguration(String arg0, String arg1) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    public Configuration getConfiguration(String pid) throws IOException {
        return m_configuration;
    }

    public Configuration getConfiguration(String arg0, String arg1) throws IOException {
        // TODO Auto-generated method stub
        return m_configuration;
    }

    public Configuration[] listConfigurations(String arg0) throws IOException, InvalidSyntaxException {
        return new Configuration[] {m_configuration};
    }

}
