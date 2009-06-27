package net.luminis.liq.client.repository.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.luminis.liq.repository.ext.BackupRepository;

public class MockBackupRepository implements BackupRepository {
    private byte[] m_current;
    private byte[] m_backup;

    @Override
    public boolean backup() throws IOException {
        if (m_current == null) {
            return false;
        }
        m_backup = AdminTestUtil.copy(m_current);
        return true;
    }

    @Override
    public InputStream read() throws IOException {
        return new ByteArrayInputStream(m_current);
    }

    @Override
    public boolean restore() throws IOException {
        if (m_backup == null) {
            return false;
        }
        m_current = AdminTestUtil.copy(m_backup);
        return true;
    }

    @Override
    public void write(InputStream data) throws IOException {
        m_current = AdminTestUtil.copy(data);
    }
}
