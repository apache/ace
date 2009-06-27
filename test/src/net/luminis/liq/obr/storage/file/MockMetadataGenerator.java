package net.luminis.liq.obr.storage.file;

import java.io.File;

import net.luminis.liq.obr.metadata.MetadataGenerator;

public class MockMetadataGenerator implements MetadataGenerator{

    private boolean m_generated = false;
    private int m_numberOfCalls = 0;

    public void generateMetadata(File metadataFilePath) {
        m_numberOfCalls++;
        m_generated = true;
    }

    public boolean generated() {
        return m_generated;
    }

    public int numberOfCalls() {
        return m_numberOfCalls;
    }
}
