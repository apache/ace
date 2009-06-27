package net.luminis.liq.deployment.provider.repositorybased;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.luminis.liq.repository.Repository;
import net.luminis.liq.repository.SortedRangeSet;

public class MockDeploymentRepository implements Repository {

    private String m_range;
    private String m_xmlRepository;

    public MockDeploymentRepository(String range, String xmlRepository) {
        m_range = range;
        m_xmlRepository = xmlRepository;
    }

    /* (non-Javadoc)
     * Magic number version 1, generates an IOException, else return
     * @see net.luminis.liq.repository.Repository#checkout(long)
     */
    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        if (version == 1) {
            //throw an IOException!
            throw new IOException("Checkout exception.");
        }
        else {
            return new ByteArrayInputStream(m_xmlRepository.getBytes());
        }
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        // Not used in test
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        return new SortedRangeSet(m_range);
    }
}
