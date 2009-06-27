package net.luminis.liq.test.log;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import net.luminis.liq.discovery.property.constants.DiscoveryConstants;
import net.luminis.liq.http.listener.constants.HttpConstants;
import net.luminis.liq.identification.property.constants.IdentificationConstants;
import net.luminis.liq.log.Log;
import net.luminis.liq.server.log.store.LogStore;
import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;

public class Activator extends TestActivatorBase {
    private static final String AUDITLOG = "/auditlog";
    private static final String DEPLOYMENT = "/deployment";

    @SuppressWarnings("unchecked")
    private Class[] m_classes = new Class[] { LogIntegrationTest.class };
    public static final String HOST = "localhost";
    public static final int PORT = 8080;
    public static final String GWID = "gw-id";
    public static final String POLL_INTERVAL = "2";
    private volatile ConfigurationAdmin m_config;
    private volatile LogService m_log;

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return m_classes;
    }

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        // helper service that configures the system
        manager.add(createService()
            .setImplementation(this)
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false))
        );
        // service containing the actual integration test
        manager.add(createService()
            .setImplementation(LogIntegrationTest.class)
            .add(createServiceDependency().setService(Log.class, "(&("+Constants.OBJECTCLASS+"="+Log.class.getName()+")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(Runnable.class, "(&("+Constants.OBJECTCLASS+"="+Runnable.class.getName()+")(taskName=auditlog))").setRequired(true))
        );
    }

    public void start() {
        try {
            configureServer();
            configureGateway();
        }
        catch (Exception e) {
            m_log.log(LogService.LOG_ERROR, "Exception while starting", e);
        }
    }

    public void stop() {
        try {
            unconfigureGateway();
            unconfigureServer();
        }
        catch (IOException e) {
            m_log.log(LogService.LOG_ERROR, "IO exception while stopping", e);
        }
    }

    private void configureGateway() throws IOException {
        setProperty(DiscoveryConstants.DISCOVERY_PID, new Object[][] { { DiscoveryConstants.DISCOVERY_URL_KEY, "http://" + HOST + ":" + PORT } });
        setProperty(IdentificationConstants.IDENTIFICATION_PID, new Object[][] { { IdentificationConstants.IDENTIFICATION_GATEWAYID_KEY, GWID } });
        createFactoryInstance("net.luminis.liq.gateway.log.store.factory", new Object[][] { {"name", "auditlog"} });
        createFactoryInstance("net.luminis.liq.gateway.log.factory", new Object[][] { {"name", "auditlog"} });
    }

    private void unconfigureGateway() throws IOException {
        m_config.getConfiguration(DiscoveryConstants.DISCOVERY_PID, null).delete();
        m_config.getConfiguration(IdentificationConstants.IDENTIFICATION_PID, null).delete();
    }

    private void unconfigureServer() throws IOException {
        m_config.getConfiguration("net.luminis.liq.deployment.servlet", null).delete();
        m_config.getConfiguration("net.luminis.liq.deployment.provider.filebased", null).delete();
    }

    private void configureServer() throws Exception {
        setProperty("net.luminis.liq.deployment.servlet", new Object[][] { { HttpConstants.ENDPOINT, DEPLOYMENT } });
        createFactoryInstance("net.luminis.liq.server.log.servlet.factory", new Object[][] { {"name", "auditlog"}, { HttpConstants.ENDPOINT, AUDITLOG } });
        createFactoryInstance("net.luminis.liq.server.log.store.factory", new Object[][] { {"name", "auditlog"} });
    }

    @SuppressWarnings("unchecked")
    private void setProperty(String pid, Object[][] props) throws IOException {
        Configuration configuration = m_config.getConfiguration(pid, null);
        Dictionary dictionary = configuration.getProperties();
        if (dictionary == null) {
            dictionary = new Hashtable();
        }
        for (Object[] pair : props) {
            dictionary.put(pair[0], pair[1]);
        }
        configuration.update(dictionary);
    }

    @SuppressWarnings("unchecked")
    private void createFactoryInstance(String factoryPid, Object[][] props) throws IOException {
        Dictionary dict = new Properties();
        for (Object[] pair : props) {
            dict.put(pair[0], pair[1]);
        }
        Configuration config = m_config.createFactoryConfiguration(factoryPid, null);
        config.update(dict);
    }

}
