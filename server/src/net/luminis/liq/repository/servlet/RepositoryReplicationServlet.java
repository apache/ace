package net.luminis.liq.repository.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Dictionary;

import net.luminis.liq.repository.RepositoryReplication;
import net.luminis.liq.repository.SortedRangeSet;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.ConfigurationException;

public class RepositoryReplicationServlet extends RepositoryServletBase {
    private static final long serialVersionUID = 1L;

    @Override
    protected ServiceReference[] getRepositories(String filter) throws InvalidSyntaxException {
        return m_context.getServiceReferences(RepositoryReplication.class.getName(), filter);
    }

    @Override
    protected SortedRangeSet getRange(ServiceReference ref) throws IOException {
        RepositoryReplication repository = (RepositoryReplication) m_context.getService(ref);
        SortedRangeSet range = repository.getRange();
        m_context.ungetService(ref);
        return range;
    }

    @Override
    protected boolean doCommit(ServiceReference ref, long version, InputStream data) throws IllegalArgumentException, IOException {
        RepositoryReplication r = (RepositoryReplication) m_context.getService(ref);
        boolean result = r.put(data, version);
        m_context.ungetService(ref);
        return result;
    }

    @Override
    protected InputStream doCheckout(ServiceReference ref, long version) throws IllegalArgumentException, IOException {
        RepositoryReplication r = (RepositoryReplication) m_context.getService(ref);
        InputStream result = r.get(version);
        m_context.ungetService(ref);
        return result;
    }

    @Override
    public String getServletInfo() {
        return "LiQ Repository Replication Servlet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void updated(Dictionary settings) throws ConfigurationException {
        // nothing special we want to do here
    }

    @Override
    protected String getCheckoutCommand() {
        return "/get";
    }

    @Override
    protected String getCommitCommand() {
        return "/put";
    }
}
