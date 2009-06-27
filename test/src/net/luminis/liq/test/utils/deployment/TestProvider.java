package net.luminis.liq.test.utils.deployment;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.luminis.liq.deployment.provider.ArtifactData;
import net.luminis.liq.deployment.provider.DeploymentProvider;

public class TestProvider implements DeploymentProvider {
    private List<ArtifactData> m_collection;
    private List<String> m_versions;

    public TestProvider() throws Exception {
        m_collection = new ArrayList<ArtifactData>();
        m_versions = new ArrayList<String>();
    }

    public void addData(String fileName, String symbolicName, URL url, String version) {
        addData(fileName, symbolicName, url, version, true);
    }

    public void addData(String fileName, String symbolicName, URL url, String version, boolean changed) {
        m_collection.add(new TestData(fileName, symbolicName, url, version, changed));
        m_versions.add(version);
    }

    public List<ArtifactData> getBundleData(String id, String version) {
        return m_collection;
    }

    public List<ArtifactData> getBundleData(String id, String versionFrom, String versionTo) {
        return m_collection;
    }

    public List<String> getVersions(String id) throws IllegalArgumentException {
        Collections.sort(m_versions);
        return m_versions;
    }
}
