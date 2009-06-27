package net.luminis.liq.client.repository.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.luminis.liq.repository.Repository;
import net.luminis.liq.repository.SortedRangeSet;

public class MockRepository implements Repository {
    private byte[] m_repo;
    private int currentVersion = 0;

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version == currentVersion) {
            return new ByteArrayInputStream(m_repo);
        }
        return null;
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        if (fromVersion == currentVersion) {
            currentVersion++;
            m_repo = AdminTestUtil.copy(data);
            return true;
        }
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(new long[] {currentVersion});
    }
}
