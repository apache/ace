/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.location.upnp;

import org.apache.ace.location.LocationService;
import org.apache.ace.location.upnp.util.HostUtil;
import org.apache.felix.dm.DependencyActivatorBase;
import org.apache.felix.dm.DependencyManager;
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
		manager.add(createComponent()
		    .setImplementation(new LocationServiceImpl(host, port))
            .setInterface(LocationService.class.getName(), null)
            .add(createConfigurationDependency().setPid(LocationServiceImpl.PID))
		    );

		//this service depends on the highest ranked location service
		manager.add(createComponent()
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
