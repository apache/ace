package net.luminis.liq.obr.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Dictionary;

import net.luminis.liq.obr.storage.BundleStore;

import org.osgi.service.cm.ConfigurationException;

public class MockBundleStore implements BundleStore {

    private InputStream m_outFile;

    public MockBundleStore(InputStream outFile) {
        m_outFile = outFile;
    }

    public InputStream get(String fileName) throws IOException {
        if (fileName.equals("UnknownFile")) {
            return null;
        }
        return m_outFile;
    }

    public void put(String fileName, OutputStream data) throws IOException {
        // TODO does nothing yet
    }

    @Override
    public boolean put(String fileName, InputStream data) throws IOException {
        if (fileName.equals("NewFile")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(String fileName) throws IOException {
        if (fileName.equals("RemoveMe")) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void updated(Dictionary arg0) throws ConfigurationException {
        // TODO does nothing yet
    }
}
