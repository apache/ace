package org.apache.ace.gogo.log;

import org.apache.ace.log.server.store.LogStore;
import org.apache.felix.service.command.Descriptor;

public class LogCommands {

    public final static String SCOPE = "ace-log";
    public final static String[] FUNCTIONS = new String[] { "cleanup" };

    // Injected by Felix DM...
    private volatile LogStore m_logStore;

    @Descriptor("Apply the configured maximum to all existing logs")
    public void cleanup() throws Exception {
        m_logStore.clean();
        System.out.println("All logfiles processed");
    }

}
