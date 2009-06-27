package net.luminis.liq.configurator;

import java.io.IOException;
import java.util.Dictionary;

import org.osgi.service.cm.Configuration;

public class MockConfiguration implements Configuration {

    @SuppressWarnings("unchecked")
    private Dictionary m_properties = null;
    private boolean m_isDeleted = false;

    public void delete() throws IOException {
        m_isDeleted = true;
    }

    public String getBundleLocation() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getFactoryPid() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getPid() {
        // TODO Auto-generated method stub
        return null;
    }

    @SuppressWarnings("unchecked")
    public synchronized Dictionary getProperties() {
        return m_properties;
    }

    public void setBundleLocation(String arg0) {
        // TODO Auto-generated method stub

    }

    public void update() throws IOException {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("unchecked")
    public synchronized void update(Dictionary newConfiguration) throws IOException {
            m_properties = newConfiguration;
    }

    public boolean isDeleted() {
        return m_isDeleted;
    }

}
