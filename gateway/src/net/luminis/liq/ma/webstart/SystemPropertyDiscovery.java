package net.luminis.liq.ma.webstart;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;

import net.luminis.liq.discovery.Discovery;

public class SystemPropertyDiscovery implements Discovery {
    private URL m_discovery;

    public SystemPropertyDiscovery() throws MalformedURLException {
        m_discovery = new URL(System.getProperty("gateway.discovery", "http://localhost:8080/"));

        // for debugging purposes, we have an override system
        try {
            m_discovery = new URL(Override.getProperty("gateway.discovery"));
        }
        catch (NoSuchElementException e) {
            // ignore the exception
        }
    }

    public URL discover() {
        return m_discovery;
    }

}
