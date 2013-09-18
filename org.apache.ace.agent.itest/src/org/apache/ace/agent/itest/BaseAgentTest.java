package org.apache.ace.agent.itest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.ace.agent.AgentConstants;
import org.apache.ace.agent.AgentControl;
import org.apache.ace.builder.DeploymentPackageBuilder;
import org.apache.ace.it.IntegrationTestBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import aQute.lib.osgi.Builder;
import aQute.lib.osgi.Jar;

public abstract class BaseAgentTest extends IntegrationTestBase {

    protected static File createBundle(String bsn, Version version, String... headers) throws Exception {
        Builder b = new Builder();

        try {
            b.setProperty("Bundle-SymbolicName", bsn);
            b.setProperty("Bundle-Version", version.toString());
            for (int i = 0; i < headers.length; i += 2) {
                b.setProperty(headers[i], headers[i + 1]);
            }
            b.setProperty("Include-Resource", "bnd.bnd"); // prevent empty jar bug

            Jar jar = b.build();
            jar.getManifest(); // Not sure whether this is needed...

            File file = File.createTempFile("testbundle", ".jar");
            file.deleteOnExit();

            jar.write(file);
            return file;
        }
        finally {
            b.close();
        }
    }

    protected static File createPackage(String name, Version version, File... bundles) throws Exception {
        DeploymentPackageBuilder builder = DeploymentPackageBuilder.createDeploymentPackage(name, version.toString());

        OutputStream fos = null;
        try {
            for (File bundle : bundles) {
                builder.addBundle(bundle.toURI().toURL());
            }

            File file = File.createTempFile("testpackage", ".jar");
            file.deleteOnExit();

            fos = new FileOutputStream(file);
            builder.generate(fos);

            return file;
        }
        finally {
            if (fos != null) {
                fos.close();
            }
        }
    }

    @Override
    protected void configureProvisionedServices() throws Exception {
        resetAgentBundleState();
    }

    protected Bundle getAgentBundle() {
        for (Bundle bundle : m_bundleContext.getBundles()) {
            if (bundle.getSymbolicName().equals(AgentControl.class.getPackage().getName())) {
                return bundle;
            }
        }
        throw new IllegalStateException("No agentBundle found");
    }

    protected void resetAgentBundleState() throws Exception {
        Bundle agentBundle = getAgentBundle();
        File dataDir = agentBundle.getBundleContext().getDataFile("");

//        System.out.println("BaseAgentTest: Stopping agent bundle");
        agentBundle.stop();
//        System.out.println("BaseAgentTest: Cleaning bundle data dir (" + dataDir + ")");
        cleanDir(dataDir);
//        System.out.println("BaseAgentTest: Cleaning system properties");
        Set<String> keysBeRemoved = new HashSet<String>();
        for (Object key : System.getProperties().keySet()) {
            if (key instanceof String && ((String) key).startsWith(AgentConstants.CONFIG_KEY_NAMESPACE)) {
                keysBeRemoved.add((String) key);
            }
        }
        for (String removeKey : keysBeRemoved) {
            System.clearProperty(removeKey);
        }
//        System.out.println("BaseAgentTest: Starting agent bundle");
        agentBundle.start();
    }

    private void cleanDir(File dir) {
        if (!dir.isDirectory()) {
            throw new IllegalStateException();
        }
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                cleanDir(file);
            }
            file.delete();
        }
        dir.delete();
    }
}
