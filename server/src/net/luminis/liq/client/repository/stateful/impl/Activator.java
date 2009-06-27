package net.luminis.liq.client.repository.stateful.impl;

import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.client.repository.RepositoryAdmin;
import net.luminis.liq.client.repository.helper.bundle.BundleHelper;
import net.luminis.liq.client.repository.object.Artifact2GroupAssociation;
import net.luminis.liq.client.repository.object.ArtifactObject;
import net.luminis.liq.client.repository.object.DeploymentVersionObject;
import net.luminis.liq.client.repository.object.GatewayObject;
import net.luminis.liq.client.repository.object.Group2LicenseAssociation;
import net.luminis.liq.client.repository.object.GroupObject;
import net.luminis.liq.client.repository.object.License2GatewayAssociation;
import net.luminis.liq.client.repository.object.LicenseObject;
import net.luminis.liq.client.repository.repository.ArtifactRepository;
import net.luminis.liq.client.repository.repository.DeploymentVersionRepository;
import net.luminis.liq.client.repository.repository.GatewayRepository;
import net.luminis.liq.client.repository.stateful.StatefulGatewayRepository;
import net.luminis.liq.server.log.store.LogStore;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.log.LogService;

/**
 * Activator for the StatefulGatewayRepository bundle.
 */
public class Activator extends DependencyActivatorBase {

    @Override
    public synchronized void init(BundleContext context, DependencyManager manager) throws Exception {
        StatefulGatewayRepositoryImpl statefulGatewayRepositoryImpl = new StatefulGatewayRepositoryImpl();
        manager.add(createService()
            .setInterface(StatefulGatewayRepository.class.getName(), null)
            .setImplementation(statefulGatewayRepositoryImpl)
            .add(createServiceDependency().setService(ArtifactRepository.class).setRequired(true))
            .add(createServiceDependency().setService(GatewayRepository.class).setRequired(true))
            .add(createServiceDependency().setService(DeploymentVersionRepository.class).setRequired(true))
            .add(createServiceDependency().setService(LogStore.class, "(&("+Constants.OBJECTCLASS+"="+LogStore.class.getName()+")(name=auditlog))").setRequired(false))
            .add(createServiceDependency().setService(BundleHelper.class).setRequired(true))
            .add(createServiceDependency().setService(EventAdmin.class).setRequired(true))
            .add(createServiceDependency().setService(LogService.class).setRequired(false)));
        Dictionary<String, String[]> topic = new Hashtable<String, String[]>();
        topic.put(EventConstants.EVENT_TOPIC, new String[] {
            ArtifactObject.TOPIC_ALL,
            Artifact2GroupAssociation.TOPIC_ALL,
            GroupObject.TOPIC_ALL,
            Group2LicenseAssociation.TOPIC_ALL,
            LicenseObject.TOPIC_ALL,
            License2GatewayAssociation.TOPIC_ALL,
            GatewayObject.TOPIC_ALL,
            DeploymentVersionObject.TOPIC_ALL,
            RepositoryAdmin.TOPIC_REFRESH, RepositoryAdmin.TOPIC_LOGIN});
        manager.add(createService()
            .setInterface(EventHandler.class.getName(), topic)
            .setImplementation(statefulGatewayRepositoryImpl));
    }

    @Override
    public synchronized void destroy(BundleContext context, DependencyManager manager) throws Exception {
        // service deregistration will happen automatically.
    }

}
