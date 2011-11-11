package org.apache.ace.webui.domain;

public class OBREntry {
    private final String m_symbolicName;
    private final String m_version;
    private final String m_uri;

    public OBREntry(String symbolicName, String version, String uri) {
        m_symbolicName = symbolicName;
        m_version = version;
        m_uri = uri;
    }

    public String getVersion() {
        return m_version;
    }

    public String getSymbolicName() {
        return m_symbolicName;
    }

    public String getUri() {
        return m_uri;
    }

    @Override
    public int hashCode() {
        return m_uri.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return m_uri.equals(((OBREntry) obj).m_uri);
    }
}