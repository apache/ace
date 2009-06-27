package net.luminis.liq.test.useradminconfigurator;

import java.io.IOException;
import java.util.Properties;

import net.luminis.liq.repository.Repository;
import net.luminis.liq.repository.impl.constants.RepositoryConstants;
import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.useradmin.UserAdmin;

public class Activator extends TestActivatorBase {

    private volatile ConfigurationAdmin m_configAdmin;

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createService()
                .setImplementation(ConfiguratorTest.class)
                .add(createServiceDependency()
                        .setService(UserAdmin.class)
                        .setRequired(true))
                .add(createServiceDependency()
                    .setService(Repository.class, "(&(" + RepositoryConstants.REPOSITORY_NAME + "=users)(" + RepositoryConstants.REPOSITORY_CUSTOMER + "=luminis))")
                    .setRequired(true)));

        // We need to do some configuration for this test to run; therefore,
        // we (as activator) wait around for the ConfigurationAdmin.
        manager.add(createService()
                .setImplementation(this)
                .add(createServiceDependency()
                        .setService(ConfigurationAdmin.class)
                        .setRequired(true)));

    }

    @Override
    protected Class[] getTestClasses() {
        return new Class[] { ConfiguratorTest.class };
    }

    public void start() throws IOException {
        // Create the repository
        Configuration config = m_configAdmin.createFactoryConfiguration("net.luminis.liq.server.repository.factory", null);

        Properties props = new Properties();
        props.put(RepositoryConstants.REPOSITORY_NAME, "users");
        props.put(RepositoryConstants.REPOSITORY_CUSTOMER, "luminis");
        props.put(RepositoryConstants.REPOSITORY_MASTER, "true");

        config.update(props);

        // Start the servlet
        config = m_configAdmin.getConfiguration("net.luminis.liq.repository.servlet.RepositoryServlet", null);

        props = new Properties();
        props.put("net.luminis.liq.server.servlet.endpoint", "/repository");

        config.update(props);

        // Configure the task
        config = m_configAdmin.getConfiguration("net.luminis.liq.configurator.useradmin.task.UpdateUserAdminTask", null);

        props = new Properties();
        props.put("repositoryName", "users");
        props.put("repositoryCustomer", "luminis");
        props.put("repositoryLocation", "http://localhost:8080/repository");

        config.update(props);

        // Schedule the task
        config = m_configAdmin.getConfiguration("net.luminis.liq.scheduler", null);

        props = new Properties();
        props.put("net.luminis.liq.configurator.useradmin.task.UpdateUserAdminTask", "1000");

        config.update(props);
    }

}
