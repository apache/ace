package net.luminis.liq.test.repositoryadmin;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Properties;

import net.luminis.liq.client.repository.RepositoryAdmin;
import net.luminis.liq.client.repository.RepositoryObject;
import net.luminis.liq.client.repository.repository.Artifact2GroupAssociationRepository;
import net.luminis.liq.client.repository.repository.ArtifactRepository;
import net.luminis.liq.client.repository.repository.DeploymentVersionRepository;
import net.luminis.liq.client.repository.repository.GatewayRepository;
import net.luminis.liq.client.repository.repository.Group2LicenseAssociationRepository;
import net.luminis.liq.client.repository.repository.GroupRepository;
import net.luminis.liq.client.repository.repository.License2GatewayAssociationRepository;
import net.luminis.liq.client.repository.repository.LicenseRepository;
import net.luminis.liq.client.repository.stateful.StatefulGatewayObject;
import net.luminis.liq.client.repository.stateful.StatefulGatewayRepository;
import net.luminis.liq.server.log.store.LogStore;
import net.luminis.liq.test.utils.TestUtils;
import net.luminis.test.osgi.dm.TestActivatorBase;

import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

/**
 * Activator for the integration test.
 */
public class Activator extends TestActivatorBase {

    private ConfigurationAdmin m_configAdmin;

    @Override
    protected void initServices(BundleContext context, DependencyManager manager) {
        manager.add(createService()
            .setImplementation(this)
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true)));
        RepositoryAdminTest test = new RepositoryAdminTest();
        manager.add(createService()
            .setImplementation(test)
            .add(createServiceDependency().setService(RepositoryAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
            .add(createServiceDependency().setService(Artifact2GroupAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GroupRepository.class).setRequired(true))
            .add(createServiceDependency().setService(Group2LicenseAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LicenseRepository.class).setRequired(true))
            .add(createServiceDependency().setService(License2GatewayAssociationRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
            .add(createServiceDependency().setService(StatefulGatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(true))
            .add(createServiceDependency().setService(ConfigurationAdmin.class).setRequired(true)));
        Dictionary<String, Object> topics = new Hashtable<String, Object>();
        topics.put(EventConstants.EVENT_TOPIC, new String[] {RepositoryObject.PUBLIC_TOPIC_ROOT + "*",
            RepositoryObject.PRIVATE_TOPIC_ROOT + "*",
            RepositoryAdmin.PUBLIC_TOPIC_ROOT + "*",
            RepositoryAdmin.PRIVATE_TOPIC_ROOT + "*",
            StatefulGatewayObject.TOPIC_ALL});
        manager.add(createService()
            .setImplementation(test)
            .setInterface(EventHandler.class.getName(), topics));
        TestUtils.configureObject(test, DependencyManager.class, manager);
    }

    public void start() throws IOException {
        Properties props = new Properties();
        props.put("name", "auditlog");
        Configuration config = m_configAdmin.createFactoryConfiguration("net.luminis.liq.server.log.store.factory", null);
        config.update(props);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class[] getTestClasses() {
        return new Class[] { RepositoryAdminTest.class };
    }

}

