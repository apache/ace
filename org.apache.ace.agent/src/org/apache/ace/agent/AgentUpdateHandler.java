package org.apache.ace.agent;

import java.io.IOException;
import java.io.InputStream;
import java.util.SortedSet;

import org.osgi.framework.Version;

public interface AgentUpdateHandler {
    /** Returns the locally installed version of the agent. */
    Version getInstalledVersion();
   
    /** Returns the versions available on the server. */
    SortedSet<Version> getAvailableVersions() throws RetryAfterException, IOException;
   
    /** Returns an input stream for the update of the agent. */
    InputStream getInputStream(Version version) throws RetryAfterException, IOException;
   
    /** Returns a download handle to download the update of the agent. */
    DownloadHandle getDownloadHandle(Version version) throws RetryAfterException, IOException;
   
    /** Returns the size of the update of the agent. */
    long getSize(Version version) throws RetryAfterException, IOException;
   
    /** Installs the update of the agent. */
    void install(InputStream stream) throws IOException;
}
