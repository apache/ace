package net.luminis.liq.ma.webstart;

import java.util.NoSuchElementException;

import net.luminis.liq.identification.Identification;

public class SystemPropertyIdentification implements Identification {
    private String m_identification;

    public SystemPropertyIdentification() {
        String defaultID = "Unknown-" + System.currentTimeMillis();
        m_identification = System.getProperty("gateway.identification", defaultID);
        // for debugging purposes, we have an override system
        try {
            m_identification = Override.getProperty("gateway.identification");
        }
        catch (NoSuchElementException e) {
            // ignore the exception
        }
    }

    public String getID() {
        return m_identification;
    }
}
