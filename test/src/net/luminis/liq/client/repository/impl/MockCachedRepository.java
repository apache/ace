package net.luminis.liq.client.repository.impl;

import java.io.IOException;
import java.io.InputStream;

import net.luminis.liq.repository.SortedRangeSet;
import net.luminis.liq.repository.ext.CachedRepository;

public class MockCachedRepository implements CachedRepository {

    @Override
    public InputStream checkout(boolean fail) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean commit() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public InputStream getLocal(boolean fail) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean revert() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void writeLocal(InputStream data) throws IOException {
        // TODO Auto-generated method stub

    }

    public InputStream checkout(long version) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean commit(InputStream data, long fromVersion) throws IOException, IllegalArgumentException {
        // TODO Auto-generated method stub
        return false;
    }

    public SortedRangeSet getRange() throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isCurrent() throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public long getHighestRemoteVersion() throws IOException {
        // TODO Auto-generated method stub
        return 0;
    }

    public long getMostRecentVersion() {
        // TODO Auto-generated method stub
        return 0;
    }

}
