package org.apache.ace.agent.itest;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.it.IntegrationTestBase;
import org.osgi.framework.Bundle;

public abstract class BaseAgentTest extends IntegrationTestBase {

    @Override
    public void configureProvisionedServices() throws Exception {
        resetAgentBundleState();
    }

    protected void resetAgentBundleState() throws Exception {
        Bundle agentBundle = getAgentBundle();
        System.out.println("BaseAgentTest: Stopping agent bundle");
        File dataDir = agentBundle.getBundleContext().getDataFile("");
        agentBundle.stop();
        System.out.println("BaseAgentTest: Cleaning bundle data dir");
        cleanDir(dataDir);
        System.out.println("BaseAgentTest: Cleaning system properties");
        Set<String> keysBeRemoved = new HashSet<String>();
        for (Object key : System.getProperties().keySet()) {
            if (key instanceof String && ((String) key).startsWith(AgentConstants.CONFIG_KEY_NAMESPACE)) {
                keysBeRemoved.add((String) key);
            }
        }
        for (String removeKey : keysBeRemoved) {
            System.clearProperty(removeKey);
        }
        System.out.println("BaseAgentTest: Starting agent bundle");
        agentBundle.start();
    }

    protected Bundle getAgentBundle() {
        for (Bundle bundle : m_bundleContext.getBundles()) {
            if (bundle.getSymbolicName().equals(AgentControl.class.getPackage().getName())) {
                return bundle;
            }
        }
        throw new IllegalStateException("No agentBundle found");
    }

    private void cleanDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalStateException();
        }
        Stack<File> dirs = new Stack<File>();
        dirs.push(dir);
        while (!dirs.isEmpty()) {
            File currentDir = dirs.pop();
            File[] files = currentDir.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    dirs.push(file);
                }
                else {
                    file.delete();
                }
            }
        }
    }
}
