package net.luminis.liq.test.utils.deployment;

import java.net.URL;
import java.util.jar.Attributes;

import net.luminis.liq.deployment.provider.ArtifactData;

public class TestData implements ArtifactData {
    private final String m_fileName;

    private final String m_symbolicName;

    private final URL m_url;

    private final String m_version;

    private final boolean m_changed;

    public TestData(String fileName, String symbolicName, URL url, String version, boolean changed) {
        m_fileName = fileName;
        m_symbolicName = symbolicName;
        m_url = url;
        m_version = version;
        m_changed = changed;
    }

    public boolean hasChanged() {
        return m_changed;
    }

    public String getFilename() {
        return m_fileName;
    }

    public String getSymbolicName() {
        return m_symbolicName;
    }

    public URL getUrl() {
        return m_url;
    }

    public String getVersion() {
        return m_version;
    }

    public String getDirective() {
        // TODO Auto-generated method stub
        return null;
    }

    public Attributes getManifestAttributes(boolean fixPackage) {
        Attributes a = new Attributes();
        a.putValue("Bundle-SymbolicName", getSymbolicName());
        a.putValue("Bundle-Version", getVersion());
        return a;
    }

    public String getProcessorPid() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isBundle() {
        return true;
    }

    public boolean isCustomizer() {
        return false;
    }
}
