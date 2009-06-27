package net.luminis.liq.location.upnp;

import net.luminis.liq.location.LocationService;
import net.luminis.liq.location.upnp.util.HostUtil;

import org.apache.felix.dependencymanager.DependencyActivatorBase;
import org.apache.felix.dependencymanager.DependencyManager;
import org.osgi.framework.BundleContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.upnp.UPnPDevice;

public class Activator extends DependencyActivatorBase {


	@Override
    public void init(BundleContext context, DependencyManager manager) throws Exception {

	    //we need these to construct the actual presentation url for the service
		int port = Integer.valueOf(context.getProperty("org.osgi.service.http.port"));
		String host = HostUtil.getHost();

		ProvisioningDevice psDevice = new ProvisioningDevice(host, port);



		//this service is configured with the correct settings
		manager.add(createService()
		    .setImplementation(new LocationServiceImpl(host, port))
            .setInterface(LocationService.class.getName(), null)
            .add(createConfigurationDependency().setPid(LocationServiceImpl.PID))
		    );

		//this service depends on the highest ranked location service
		manager.add(createService()
				.setImplementation(psDevice)
				.setInterface(UPnPDevice.class.getName(), psDevice.getDescriptions(null))
				.setComposition("getComposition")
				.add(createServiceDependency().setService(HttpService.class).setRequired(true))
                .add(createServiceDependency().setService(LocationService.class).setRequired(true))
		);

	};

	@Override
	public void destroy(BundleContext context, DependencyManager manager) throws Exception {
	}
}
